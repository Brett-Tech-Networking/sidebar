package com.bretttech.sidebar.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.ImageButton
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bretttech.sidebar.R
import com.bretttech.sidebar.data.SelectedAppsStore
import com.bretttech.sidebar.ui.adapters.SidebarAdapter
import com.bretttech.sidebar.ui.settings.SettingsActivity
import com.bretttech.sidebar.util.AppsLoader

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var sidebarView: View? = null
    private lateinit var store: SelectedAppsStore

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = SelectedAppsStore(this)
        startForegroundServiceNotification()

        // Abort early if overlay permission not granted yet
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        createOverlay()
    }

    private fun createOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_sidebar, null)
        sidebarView = view

        val sidebarRecycler = view.findViewById<RecyclerView>(R.id.sidebarRecycler)
        val btnSettings = view.findViewById<ImageButton>(R.id.btnSettings)

        sidebarRecycler.layoutManager = GridLayoutManager(this, 2)
        val sidebarAdapter = SidebarAdapter { entry ->
            AppsLoader.launchApp(this, entry.packageName)
        }
        sidebarRecycler.adapter = sidebarAdapter

        val saved = store.getSelectedPackages()
        val apps = AppsLoader.getLaunchableApps(this)
            .filter { saved.contains(it.packageName) }
        sidebarAdapter.submitList(apps)

        btnSettings.setOnClickListener {
            val settingsIntent = Intent(this, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(settingsIntent)
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        val gravity = if (store.getSidebarSide() == "right") Gravity.END else Gravity.START

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity or Gravity.CENTER_VERTICAL
            x = 0
            y = 0
        }

        windowManager.addView(view, params)

        // Drag-move support
        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - touchX).toInt()
                        params.y = initialY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(view, params)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun startForegroundServiceNotification() {
        val channelId = "sidebar_overlay_channel"
        val channelName = "Sidebar Overlay"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
        val notif: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sidebar Running")
            .setContentText("Tap the ⚙️ to customize apps.")
            .setSmallIcon(R.drawable.ic_settings_small)
            .setOngoing(true)
            .build()

        // IMPORTANT: On Android 14/15 we MUST have a matching FGS type in the manifest (we set dataSync)
        startForeground(1, notif)
    }

    override fun onDestroy() {
        super.onDestroy()
        sidebarView?.let {
            (getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.removeView(it)
        }
        sidebarView = null
    }
}
