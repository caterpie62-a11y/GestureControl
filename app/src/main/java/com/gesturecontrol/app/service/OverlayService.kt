package com.gesturecontrol.app.service

import android.animation.ObjectAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.Shader
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import com.gesturecontrol.app.R
import com.gesturecontrol.app.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingButton: View? = null
    private var isShowing = false

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var params: WindowManager.LayoutParams? = null

    private var backdropView: View? = null
    private val menuButtons = mutableListOf<View>()
    private var isMenuVisible = false

    private var lastTapTime = 0L
    private var longPressTimer: android.os.Handler? = null
    private var isLongPressTriggered = false

    private lateinit var prefsManager: PreferencesManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val buttonSizeDp = 64
    private val menuButtonSizeDp = 44
    private val menuRadiusDp = 80

    companion object {
        private const val CHANNEL_ID = "GestureControlChannel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_SHOW = "SHOW"
        const val ACTION_HIDE = "HIDE"
        private const val LONG_PRESS_DURATION = 600L
        private const val DOUBLE_TAP_THRESHOLD = 300L
        private const val DRAG_THRESHOLD = 15f

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java).apply { action = ACTION_SHOW })
        }
        fun stop(context: Context) {
            context.startService(Intent(context, OverlayService::class.java).apply { action = ACTION_HIDE })
        }
    }

    private fun dp(px: Int) = (px * resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefsManager = PreferencesManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> hideOverlay()
        }
        return START_STICKY
    }

    private suspend fun getInitialPosition(): Pair<Int, Int> {
        val savedX = prefsManager.buttonX.first()
        val savedY = prefsManager.buttonY.first()
        if (savedX > 0 && savedY > 0) return Pair(savedX.toInt(), savedY.toInt())
        val dm = resources.displayMetrics
        val size = dp(buttonSizeDp)
        return Pair(dm.widthPixels - size - dp(20), dm.heightPixels - size - dp(120))
    }

    private fun showOverlay() {
        if (isShowing) return
        serviceScope.launch {
            val (startX, startY) = getInitialPosition()
            val size = dp(buttonSizeDp)
            val buttonParams = WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START; x = startX; y = startY }
            params = buttonParams

            floatingButton = LayoutInflater.from(this@OverlayService).inflate(R.layout.floating_button, null)
            floatingButton?.setOnTouchListener { _, event -> handleTouch(event, buttonParams); true }
            windowManager?.addView(floatingButton, buttonParams)
            isShowing = true
            startForeground(NOTIFICATION_ID, createNotification())
        }
    }

    private fun hideOverlay() {
        if (!isShowing) return
        hideRadialMenu()
        floatingButton?.let { windowManager?.removeView(it) }
        floatingButton = null
        isShowing = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun handleTouch(event: MotionEvent, buttonParams: WindowManager.LayoutParams): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = buttonParams.x; initialY = buttonParams.y
                initialTouchX = event.rawX; initialTouchY = event.rawY
                isDragging = false; isLongPressTriggered = false
                longPressTimer = android.os.Handler()
                longPressTimer?.postDelayed({
                    if (!isLongPressTriggered && !isDragging) { isLongPressTriggered = true; showRadialMenu() }
                }, LONG_PRESS_DURATION)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD) {
                    isDragging = true
                    longPressTimer?.removeCallbacksAndMessages(null)
                    val dm = resources.displayMetrics
                    buttonParams.x = (initialX + dx.toInt()).coerceIn(0, dm.widthPixels - dp(buttonSizeDp))
                    buttonParams.y = (initialY + dy.toInt()).coerceIn(0, dm.heightPixels - dp(buttonSizeDp))
                    windowManager?.updateViewLayout(floatingButton, buttonParams)
                }
            }
            MotionEvent.ACTION_UP -> {
                longPressTimer?.removeCallbacksAndMessages(null)
                if (isDragging) {
                    serviceScope.launch { prefsManager.saveButtonPosition(buttonParams.x.toFloat(), buttonParams.y.toFloat()) }
                } else if (!isLongPressTriggered) {
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime < DOUBLE_TAP_THRESHOLD) performAction("double_tap") else performAction("single_tap")
                    lastTapTime = now
                }
            }
            MotionEvent.ACTION_CANCEL -> longPressTimer?.removeCallbacksAndMessages(null)
        }
        return true
    }

    private fun showRadialMenu() {
        if (isMenuVisible) return
        isMenuVisible = true
        vibrate(50)

        // Фон с затемнением + blur (API 31+)
        val backdrop = View(this).apply {
            setBackgroundResource(R.drawable.menu_backdrop)
            setOnClickListener { hideRadialMenu() }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backdrop.setRenderEffect(RenderEffect.createBlurEffect(dp(12f).toFloat(), dp(12f).toFloat(), Shader.TileMode.CLAMP))
        }
        val backdropParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        windowManager?.addView(backdrop, backdropParams)
        backdropView = backdrop
        backdrop.alpha = 0f
        backdrop.animate().alpha(1f).setDuration(180).start()

        // Центр плавающей кнопки
        val cx = params!!.x + dp(buttonSizeDp) / 2
        val cy = params!!.y + dp(buttonSizeDp) / 2
        val s = dp(menuButtonSizeDp)
        val radius = dp(menuRadiusDp)

        // 5 действий по кругу + X в центре
        val actions = listOf(
            Triple(R.drawable.ic_back, "back", 0.0),
            Triple(R.drawable.ic_volume_up, "volume_up", 72.0),
            Triple(R.drawable.ic_play_pause, "play_pause", 144.0),
            Triple(R.drawable.ic_home, "home", 216.0),
            Triple(R.drawable.ic_volume_down, "volume_down", 288.0)
        )
        actions.forEachIndexed { index, (icon, action, angleDeg) ->
            val rad = Math.toRadians(angleDeg)
            val bx = (cx + radius * sin(rad)).toInt() - s / 2
            val by = (cy - radius * cos(rad)).toInt() - s / 2
            val btn = createMenuButton(icon, action)
            val p = WindowManager.LayoutParams(s, s,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
                .apply { gravity = Gravity.TOP or Gravity.START; x = bx; y = by }
            windowManager?.addView(btn, p)
            menuButtons.add(btn)
            animateIn(btn, index * 40L)
        }

        // X в центре (над главной кнопкой)
        val closeBtn = createMenuButton(R.drawable.ic_close, "close")
        val cp = WindowManager.LayoutParams(s, s,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.START; x = cx - s / 2; y = cy - s / 2 }
        windowManager?.addView(closeBtn, cp)
        menuButtons.add(closeBtn)
        animateIn(closeBtn, actions.size * 40L)
    }

    private fun createMenuButton(iconRes: Int, action: String): View {
        val container = FrameLayout(this)
        val img = ImageButton(this).apply {
            setImageResource(iconRes)
            setBackgroundResource(R.drawable.menu_button_glass)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(12f.toInt()), dp(12f.toInt()), dp(12f.toInt()), dp(12f.toInt()))
            contentDescription = action
            setOnClickListener { vibrate(30); performAction(action); hideRadialMenu() }
        }
        container.addView(img, FrameLayout.LayoutParams(dp(menuButtonSizeDp), dp(menuButtonSizeDp)))
        return container
    }

    private fun animateIn(v: View, delay: Long) {
        v.alpha = 0f; v.scaleX = 0f; v.scaleY = 0f
        ObjectAnimator.ofFloat(v, "alpha", 0f, 1f).apply { duration = 180; startDelay = delay; start() }
        ObjectAnimator.ofFloat(v, "scaleX", 0f, 1f).apply { duration = 260; startDelay = delay; interpolator = OvershootInterpolator(1.6f); start() }
        ObjectAnimator.ofFloat(v, "scaleY", 0f, 1f).apply { duration = 260; startDelay = delay; interpolator = OvershootInterpolator(1.6f); start() }
    }

    private fun hideRadialMenu() {
        if (!isMenuVisible) return
        isMenuVisible = false
        backdropView?.animate()?.alpha(0f)?.setDuration(150)?.withEndAction {
            windowManager?.removeView(backdropView); backdropView = null
        }?.start()
        menuButtons.forEach { btn ->
            ObjectAnimator.ofFloat(btn, "alpha", 1f, 0f).apply { duration = 120; start() }
            ObjectAnimator.ofFloat(btn, "scaleX", 1f, 0f).apply { duration = 120; start() }
            ObjectAnimator.ofFloat(btn, "scaleY", 1f, 0f).apply { duration = 120; start() }
        }
        android.os.Handler().postDelayed({
            menuButtons.forEach { windowManager?.removeView(it) }
            menuButtons.clear()
        }, 180)
    }

    private fun performAction(action: String) {
        when (action) {
            "back" -> { GestureAccessibilityService.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK); showToast("⬅️ Назад") }
            "home" -> { GestureAccessibilityService.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME); showToast("🏠 Домой") }
            "volume_up" -> { (getSystemService(Context.AUDIO_SERVICE) as AudioManager).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); showToast("🔊 Громкость +") }
            "volume_down" -> { (getSystemService(Context.AUDIO_SERVICE) as AudioManager).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); showToast("🔉 Громкость -") }
            "play_pause" -> { val i = Intent("android.intent.action.MEDIA_BUTTON"); i.putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)); sendOrderedBroadcast(i, null); showToast("⏯ Play/Pause") }
            "close" -> hideRadialMenu()
            "single_tap" -> showToast("👆 Одиночное нажатие")
            "double_tap" -> showToast("👆👆 Двойное нажатие")
        }
    }

    private fun showToast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    private fun vibrate(d: Long) {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(d, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") v.vibrate(d)
    }
    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Gesture Control", NotificationManager.IMPORTANCE_LOW))
    }
    private fun createNotification(): Notification = Notification.Builder(this, CHANNEL_ID)
        .setContentTitle("Gesture Control активен").setContentText("Долгое нажатие — меню")
        .setSmallIcon(R.drawable.ic_notification).build()

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { hideOverlay(); super.onDestroy() }
}
