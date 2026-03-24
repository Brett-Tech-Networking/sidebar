package com.bretttech.sidebar.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Rect
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bretttech.sidebar.MainActivity
import com.bretttech.sidebar.R
import com.bretttech.sidebar.data.SelectedAppsStore
import com.bretttech.sidebar.ui.adapters.SidebarAdapter
import com.bretttech.sidebar.ui.theme.settings.SettingsActivity
import com.bretttech.sidebar.util.AppsLoader
import com.bretttech.sidebar.util.CrashRecovery
import com.bretttech.sidebar.util.PanelStylePalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayService : Service() {

    private lateinit var notificationManager: NotificationManager
    private lateinit var windowManager: WindowManager
    private var sidebarView: View? = null
    private lateinit var store: SelectedAppsStore
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isPanelVisible = false
    private var currentSide: String = "right"
    private val selectedPackages = mutableListOf<String>()
    private var sidebarAdapter: SidebarAdapter? = null
    private var sidebarLayoutManager: GridLayoutManager? = null
    private var panelContainerView: View? = null
    private var edgeTouchZoneView: View? = null
    private var edgeHandleView: View? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var bindAppsJob: Job? = null

    private val estimatedTileHeightDp = 88
    private val panelWidthSingleColumnDp = 132
    private val panelWidthTwoColumnDp = 220

    private val dragHoldMillis = 350L

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = SelectedAppsStore(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        startForegroundServiceNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        if (!store.isSidebarEnabled()) {
            removeOverlay()
            stopSelf()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (action) {
            ACTION_RELOAD -> reloadOverlay()
            ACTION_APPLY_PREFERENCES -> applyPreferencesToOverlay()
            ACTION_START -> if (sidebarView == null) createOverlay() else bindSidebarData()
        }

        return START_STICKY
    }

    private fun createOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_sidebar, null)
        sidebarView = view

        val overlayRow = view.findViewById<LinearLayout>(R.id.overlayRow)
        val edgeTouchZone = view.findViewById<FrameLayout>(R.id.edgeTouchZone)
        val edgeHandle = view.findViewById<View>(R.id.edgeHandle)
        val panelContainer = view.findViewById<View>(R.id.panelContainer)
        edgeTouchZoneView = edgeTouchZone
        edgeHandleView = edgeHandle
        panelContainerView = panelContainer
        val sidebarRecycler = view.findViewById<RecyclerView>(R.id.sidebarRecycler)
        val btnAddApps = view.findViewById<ImageButton>(R.id.btnAddApps)
        val btnEditApps = view.findViewById<ImageButton>(R.id.btnEditApps)

        currentSide = store.getSidebarSide()
        overlayRow.layoutDirection = if (currentSide == "right") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        applyHandleSize()
        applyPanelTheme(panelContainer)
        panelContainer.visibility = View.GONE
        isPanelVisible = false

        val gridLayoutManager = GridLayoutManager(this, 1)
        sidebarLayoutManager = gridLayoutManager
        sidebarRecycler.layoutManager = gridLayoutManager
        val sidebarAdapter = SidebarAdapter(
            onClick = { entry ->
                AppsLoader.launchApp(this, entry.packageName)
                hidePanel(panelContainer)
            },
            onRemove = { entry ->
                selectedPackages.remove(entry.packageName)
                store.saveSelectedPackages(selectedPackages)
                bindSidebarData(sidebarAdapter)
            },
            onOrderChanged = { reorderedPackages ->
                selectedPackages.clear()
                selectedPackages.addAll(reorderedPackages)
                store.saveSelectedPackages(selectedPackages)
            }
        )
        this.sidebarAdapter = sidebarAdapter
        sidebarRecycler.adapter = sidebarAdapter

        val reorderTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                sidebarAdapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun isLongPressDragEnabled(): Boolean = true
        })
        reorderTouchHelper.attachToRecyclerView(sidebarRecycler)

        bindSidebarData(sidebarAdapter)

        btnAddApps.setOnClickListener {
            hidePanel(panelContainer)
            val settingsIntent = Intent(this, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(settingsIntent)
        }

        btnEditApps.setOnClickListener {
            val editEnabled = !(this.sidebarAdapter?.isEditMode() ?: false)
            this.sidebarAdapter?.setEditMode(editEnabled)
            btnEditApps.alpha = if (editEnabled) 1f else 0.7f
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        val gravity = if (currentSide == "right") Gravity.END else Gravity.START

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity or Gravity.CENTER_VERTICAL
            x = 0
            y = store.getSidebarYOffset()
        }
        layoutParams = params

        windowManager.addView(view, params)
        applyGestureExclusion(edgeTouchZone)

        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE && isPanelVisible) {
                hidePanel(panelContainer)
                true
            } else {
                false
            }
        }

        edgeTouchZone.setOnTouchListener(object : View.OnTouchListener {
            private val touchSlop = ViewConfiguration.get(this@OverlayService).scaledTouchSlop
            private var initialX = 0
            private var initialY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var isDragging = false
            private var longPressActivated = false
            private var downTime = 0L

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        isDragging = false
                        longPressActivated = false
                        downTime = event.eventTime
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!longPressActivated) {
                            if (event.eventTime - downTime >= dragHoldMillis) {
                                longPressActivated = true
                                v?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            } else {
                                return true
                            }
                        }

                        val deltaY = (event.rawY - touchY).toInt()
                        if (!isDragging && kotlin.math.abs(deltaY) > touchSlop) {
                            isDragging = true
                        }
                        if (!isDragging) return true

                        params.x = initialX
                        params.y = initialY + deltaY
                        windowManager.updateViewLayout(view, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isDragging) {
                            store.setSidebarYOffset(params.y)
                        } else if (!longPressActivated) {
                            togglePanel(panelContainer)
                        } else {
                            // Long-press without drag should not toggle panel.
                        }
                        isDragging = false
                        longPressActivated = false
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        isDragging = false
                        longPressActivated = false
                        return true
                    }
                }
                return true
            }
        })
    }

    private fun applyGestureExclusion(target: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        target.post {
            if (target.width <= 0 || target.height <= 0) return@post
            target.systemGestureExclusionRects = listOf(
                Rect(0, 0, target.width, target.height)
            )
        }
    }

    private fun bindSidebarData(existingAdapter: SidebarAdapter? = null) {
        val container = sidebarView ?: return
        val panelContainer = panelContainerView
        val recycler = container.findViewById<RecyclerView>(R.id.sidebarRecycler)
        val adapter = existingAdapter ?: recycler.adapter as? SidebarAdapter ?: return
        bindAppsJob?.cancel()
        bindAppsJob = serviceScope.launch {
            val selectedSnapshot = store.getSelectedPackages()
            selectedPackages.clear()
            selectedPackages.addAll(selectedSnapshot)

            val selectedOrder = selectedSnapshot.withIndex().associate { it.value to it.index }
            val ordered = withContext(Dispatchers.IO) {
                AppsLoader.getLaunchableApps(this@OverlayService)
                    .asSequence()
                    .filter { selectedOrder.containsKey(it.packageName) }
                    .sortedBy { selectedOrder[it.packageName] ?: Int.MAX_VALUE }
                    .toList()
            }

            if (!isActive || sidebarView == null) return@launch

            val estimatedTileHeightPx = dpToPx(estimatedTileHeightDp)
            val recyclerHeightPx = if (recycler.height > 0) recycler.height else dpToPx(440)
            val singleColumnCapacity = (recyclerHeightPx / estimatedTileHeightPx).coerceAtLeast(1)
            val useTwoColumns = ordered.size > singleColumnCapacity

            sidebarLayoutManager?.spanCount = if (useTwoColumns) 2 else 1
            panelContainer?.layoutParams = panelContainer.layoutParams.apply {
                width = dpToPx(if (useTwoColumns) panelWidthTwoColumnDp else panelWidthSingleColumnDp)
            }
            panelContainer?.requestLayout()

            val labelColor = when (store.getPanelTheme()) {
                "light" -> ContextCompat.getColor(this@OverlayService, R.color.edge_panel_label)
                "custom" -> PanelStylePalette.forCustomBase(store.getCustomPanelColor()).labelColor
                else -> android.graphics.Color.WHITE
            }
            adapter.setLabelTextColor(labelColor)
            adapter.submitList(ordered)
        }
    }

    private fun reloadOverlay() {
        removeOverlay()
        createOverlay()
    }

    private fun applyPreferencesToOverlay() {
        if (sidebarView == null) {
            createOverlay()
            return
        }

        val root = sidebarView ?: return
        val overlayRow = root.findViewById<LinearLayout>(R.id.overlayRow)
        currentSide = store.getSidebarSide()
        overlayRow.layoutDirection = if (currentSide == "right") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

        layoutParams?.let { params ->
            params.gravity = (if (currentSide == "right") Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
            params.y = store.getSidebarYOffset()
            windowManager.updateViewLayout(root, params)
        }

        panelContainerView?.let { panel ->
            applyPanelTheme(panel)
            panel.requestLayout()
        }

        applyHandleSize()

        bindSidebarData()
    }

    private fun applyHandleSize() {
        val handleWidthPx = dpToPx(store.getHandleWidthDp())
        val handleHeightPx = dpToPx(store.getHandleHeightDp())

        edgeHandleView?.layoutParams = edgeHandleView?.layoutParams?.apply {
            width = handleWidthPx
            height = handleHeightPx
        }
        edgeTouchZoneView?.layoutParams = edgeTouchZoneView?.layoutParams?.apply {
            width = (handleWidthPx + dpToPx(16)).coerceAtLeast(dpToPx(24))
            height = (handleHeightPx + dpToPx(28)).coerceAtLeast(dpToPx(56))
        }

        edgeHandleView?.requestLayout()
        edgeTouchZoneView?.requestLayout()
        edgeTouchZoneView?.let { applyGestureExclusion(it) }
    }

    private fun togglePanel(panelContainer: View) {
        if (isPanelVisible) {
            hidePanel(panelContainer)
        } else {
            showPanel(panelContainer)
        }
    }

    private fun showPanel(panelContainer: View) {
        val side = currentSide
        val fromX = if (side == "right") dpToPx(24).toFloat() else -dpToPx(24).toFloat()
        panelContainer.visibility = View.VISIBLE
        panelContainer.translationX = fromX
        panelContainer.alpha = 0f
        sidebarAdapter?.setEditMode(false)
        panelContainer.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(180L)
            .start()
        isPanelVisible = true
    }

    private fun hidePanel(panelContainer: View) {
        val side = currentSide
        if (!isPanelVisible && panelContainer.visibility != View.VISIBLE) return
        val toX = if (side == "right") dpToPx(24).toFloat() else -dpToPx(24).toFloat()
        sidebarAdapter?.setEditMode(false)
        panelContainer.animate()
            .translationX(toX)
            .alpha(0f)
            .setDuration(140L)
            .withEndAction {
                panelContainer.visibility = View.GONE
                panelContainer.translationX = 0f
                panelContainer.alpha = 1f
            }
            .start()
        isPanelVisible = false
    }

    private fun applyPanelTheme(panelContainer: View) {
        when (store.getPanelTheme()) {
            "light" -> {
                panelContainer.setBackgroundResource(R.drawable.sidebar_bg)
                applyFooterColors(
                    mutedColor = ContextCompat.getColor(this, R.color.edge_panel_muted),
                    labelColor = ContextCompat.getColor(this, R.color.edge_panel_label)
                )
                applyHandleTheme(
                    backgroundColor = ContextCompat.getColor(this, R.color.edge_handle_bg),
                    strokeColor = ContextCompat.getColor(this, R.color.edge_handle_stroke)
                )
            }
            "blue" -> {
                panelContainer.setBackgroundResource(R.drawable.sidebar_bg_blue)
                applyFooterColors(
                    mutedColor = Color.parseColor("#CCFFFFFF"),
                    labelColor = Color.WHITE
                )
                applyHandleTheme(
                    backgroundColor = Color.parseColor("#D94A617E"),
                    strokeColor = Color.parseColor("#66FFFFFF")
                )
            }
            "custom" -> {
                val colors = PanelStylePalette.forCustomBase(store.getCustomPanelColor())
                panelContainer.background = createRoundedBackground(
                    color = colors.panelBackground,
                    strokeColor = colors.panelStroke,
                    cornerRadiusDp = 30f
                )
                applyFooterColors(colors.mutedColor, colors.labelColor)
                applyHandleTheme(colors.handleBackground, colors.handleStroke)
            }
            else -> {
                panelContainer.setBackgroundResource(R.drawable.sidebar_bg_dark)
                applyFooterColors(
                    mutedColor = Color.parseColor("#CCFFFFFF"),
                    labelColor = Color.WHITE
                )
                applyHandleTheme(
                    backgroundColor = Color.parseColor("#88929E"),
                    strokeColor = Color.parseColor("#4DFFFFFF")
                )
            }
        }
    }

    private fun applyFooterColors(mutedColor: Int, labelColor: Int) {
        val root = sidebarView ?: return
        root.findViewById<ImageButton>(R.id.btnAddApps)?.imageTintList = ColorStateList.valueOf(mutedColor)
        root.findViewById<ImageButton>(R.id.btnEditApps)?.imageTintList = ColorStateList.valueOf(mutedColor)
        root.findViewById<TextView>(R.id.panelFooterDots)?.setTextColor(mutedColor)

        val adapter = sidebarAdapter
        if (adapter != null) {
            adapter.setLabelTextColor(labelColor)
        }
    }

    private fun applyHandleTheme(backgroundColor: Int, strokeColor: Int) {
        edgeHandleView?.background = createRoundedBackground(
            color = backgroundColor,
            strokeColor = strokeColor,
            cornerRadiusDp = 10f
        )
    }

    private fun createRoundedBackground(color: Int, strokeColor: Int, cornerRadiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(cornerRadiusDp.toInt()).toFloat()
            setColor(color)
            setStroke(dpToPx(1), strokeColor)
        }
    }

    private fun removeOverlay() {
        val current = sidebarView ?: return
        try {
            windowManager.removeView(current)
        } catch (_: Exception) {
        }
        sidebarView = null
        edgeTouchZoneView = null
        edgeHandleView = null
        panelContainerView = null
        layoutParams = null
        isPanelVisible = false
    }

    private fun startForegroundServiceNotification() {
        val channelId = NOTIFICATION_CHANNEL_ID
        val channelName = getString(R.string.notification_channel_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_MIN)
            channel.setShowBadge(false)
            notificationManager.createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            101,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val manageIntent = PendingIntent.getActivity(
            this,
            102,
            Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_body))
            .setSmallIcon(R.drawable.ic_settings_small)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_settings_small, getString(R.string.manage_shortcuts), manageIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        startForeground(1, notif)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!store.isSidebarEnabled() || !Settings.canDrawOverlays(this)) return
        CrashRecovery.logEvent(this, name = "task_removed", reason = "service_task_removed")
        start(this, ACTION_START)
    }

    override fun onDestroy() {
        bindAppsJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
        removeOverlay()
    }

    companion object {
        const val ACTION_START = "com.bretttech.sidebar.action.START"
        const val ACTION_RELOAD = "com.bretttech.sidebar.action.RELOAD"
        const val ACTION_APPLY_PREFERENCES = "com.bretttech.sidebar.action.APPLY_PREFERENCES"

        private const val NOTIFICATION_CHANNEL_ID = "sidebar_overlay_channel"

        fun start(context: Context, action: String = ACTION_START) {
            val intent = Intent(context, OverlayService::class.java).apply {
                this.action = action
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                CrashRecovery.logEvent(
                    context,
                    name = "service_start_failed",
                    reason = "startForegroundService_exception",
                    extra = mapOf("action" to action)
                )
                CrashRecovery.scheduleServiceRestart(context, "start_foreground_service_failed")
            }
        }
    }
}
