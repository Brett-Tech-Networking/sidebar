package com.bretttech.sidebar.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import com.bretttech.sidebar.receiver.CrashRestartReceiver
import org.json.JSONArray
import org.json.JSONObject
import kotlin.system.exitProcess

object CrashRecovery {

    private const val PREFS_NAME = "crash_recovery"
    private const val KEY_LAST_WINDOW_START_MS = "last_window_start_ms"
    private const val KEY_WINDOW_COUNT = "window_count"
    private const val KEY_EVENT_LOG = "event_log"

    private const val MAX_RESTARTS_PER_WINDOW = 4
    private const val RESTART_WINDOW_MS = 120_000L
    private const val RESTART_DELAY_MS = 1_500L

    private const val RESTART_REQUEST_CODE = 9331
    private const val MAX_LOG_EVENTS = 40

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val appContext = context.applicationContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching {
                    logEvent(
                        appContext,
                        name = "uncaught_exception",
                        reason = throwable.javaClass.simpleName,
                        extra = mapOf(
                            "thread" to thread.name,
                            "message" to (throwable.message ?: "")
                        )
                    )
                    scheduleServiceRestart(appContext, "uncaught_exception")
                }
                previous?.uncaughtException(thread, throwable) ?: run {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
            }
            installed = true
        }
    }

    fun scheduleServiceRestart(context: Context, reason: String) {
        val appContext = context.applicationContext
        if (!canScheduleRestart(appContext, reason)) return

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val restartIntent = Intent(appContext, CrashRestartReceiver::class.java).apply {
            action = CrashRestartReceiver.ACTION_RESTART_SERVICE
            putExtra(CrashRestartReceiver.EXTRA_REASON, reason)
            setPackage(appContext.packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            RESTART_REQUEST_CODE,
            restartIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = System.currentTimeMillis() + RESTART_DELAY_MS
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )

        logEvent(
            appContext,
            name = "restart_scheduled",
            reason = reason,
            extra = mapOf("delayMs" to RESTART_DELAY_MS.toString())
        )
    }

    private fun canScheduleRestart(context: Context, reason: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastWindowStart = prefs.getLong(KEY_LAST_WINDOW_START_MS, 0L)

        val (nextWindowStart, nextCount) = if (now - lastWindowStart > RESTART_WINDOW_MS) {
            now to 1
        } else {
            lastWindowStart to (prefs.getInt(KEY_WINDOW_COUNT, 0) + 1)
        }

        prefs.edit()
            .putLong(KEY_LAST_WINDOW_START_MS, nextWindowStart)
            .putInt(KEY_WINDOW_COUNT, nextCount)
            .commit()

        val allowed = nextCount <= MAX_RESTARTS_PER_WINDOW
        if (!allowed) {
            logEvent(
                context,
                name = "restart_throttled",
                reason = reason,
                extra = mapOf(
                    "windowCount" to nextCount.toString(),
                    "windowMs" to RESTART_WINDOW_MS.toString()
                )
            )
        }
        return allowed
    }

    fun logEvent(
        context: Context,
        name: String,
        reason: String,
        extra: Map<String, String> = emptyMap()
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_EVENT_LOG, "[]") ?: "[]"
        val arr = runCatching { JSONArray(existing) }.getOrElse { JSONArray() }

        val entry = JSONObject()
            .put("ts", System.currentTimeMillis())
            .put("name", name)
            .put("reason", reason)
        if (extra.isNotEmpty()) {
            val extraObj = JSONObject()
            extra.forEach { (k, v) -> extraObj.put(k, v) }
            entry.put("extra", extraObj)
        }

        arr.put(entry)

        val trimmed = if (arr.length() > MAX_LOG_EVENTS) {
            val keepFrom = arr.length() - MAX_LOG_EVENTS
            JSONArray().also { out ->
                for (index in keepFrom until arr.length()) {
                    out.put(arr.getJSONObject(index))
                }
            }
        } else {
            arr
        }

        // Use commit() (synchronous) so the write is guaranteed to reach disk
        // before the process is killed by an uncaught exception handler.
        prefs.edit().putString(KEY_EVENT_LOG, trimmed.toString()).commit()
    }

    fun getRecentEvents(context: Context): List<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_EVENT_LOG, "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        val out = ArrayList<String>(arr.length())
        for (index in 0 until arr.length()) {
            val item = arr.optJSONObject(index) ?: continue
            out.add(item.toString())
        }
        return out
    }

    fun clearLogs(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_EVENT_LOG, "[]").apply()
    }
}