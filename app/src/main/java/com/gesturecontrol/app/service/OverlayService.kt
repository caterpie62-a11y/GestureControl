package com.gesturecontrol.app.service

import android.animation.ObjectAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
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
import android.widget.ImageButton
import android.widget.Toast
import com.gesturecontrol.app.R
import com.gesturecontrol.app.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingButton: View? = null
    private var isShowing = false

    // Перетаскивание
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var params: WindowManager.LayoutParams? = null

    // Меню
    private var menuView: View? = null
    private var isMenuVisible = false

    // Жесты
    private var lastTapTime = 0L
    private var longPressTimer: android.os.Handler? = null
    private var isLongPressTriggered = false

    // Позиция
    private lateinit var prefsManager: PreferencesManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val CHANNEL_ID = "GestureControlChannel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_SHOW = "SHOW"
        const val ACTION_HIDE = "HIDE"
        private const val LONG_PRESS_DURATION = 600L
        private const val DOUBLE_TAP_THRESHOLD = 300L
        private const val DRAG_THRESHOLD = 15f
        const val OUR_PACKAGE = "com.gesturecontrol.app"

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply { action = ACTION_SHOW }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply { action = ACTION_HIDE }
            context.startService(intent)
        }
    }

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

        if (savedX > 0 && savedY > 0) {
            return Pair(savedX.toInt(), savedY.toInt())
        }

        // Правый нижний угол по умолчанию
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val buttonSize = 150
        val offsetX = 60
        val offsetY = 250
        return Pair(screenWidth - buttonSize - offsetX, screenHeight - buttonSize - offsetY)
    }

    private fun showOverlay() {
        if (isShowing) return

        serviceScope.launch {
            val (startX, startY) = getInitialPosition()

            val buttonParams = WindowManager.LayoutParams(
                150, 150,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = startX
                y = startY
            }

            params = buttonParams

            floatingButton = LayoutInflater.from(this@OverlayService)
                .inflate(R.layout.floating_button, null)

            floatingButton?.setOnTouchListener { _, event ->
                handleTouch(event, buttonParams)
                true
            }

            windowManager?.addView(floatingButton, buttonParams)
            isShowing = true

            startForeground(NOTIFICATION_ID, createNotification())
            Toast.makeText(this@OverlayService, "Кнопка активна. Долгое нажатие — меню.", Toast.LENGTH_LONG).show()
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
                initialX = buttonParams.x
                initialY = buttonParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                isLongPressTriggered = false

                longPressTimer = android.os.Handler()
                longPressTimer?.postDelayed({
                    if (!isLongPressTriggered && !isDragging) {
                        isLongPressTriggered = true
                        showRadialMenu()
                    }
                }, LONG_PRESS_DURATION)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY

                if (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD) {
                    isDragging = true
                    longPressTimer?.removeCallbacksAndMessages(null)

                    val newX = initialX + dx.toInt()
                    val newY = initialY + dy.toInt()

                    // Ограничиваем границами экрана
                    val displayMetrics = resources.displayMetrics
                    val clampedX = newX.coerceIn(0, displayMetrics.widthPixels - 150)
                    val clampedY = newY.coerceIn(0, displayMetrics.heightPixels - 150)

                    buttonParams.x = clampedX
                    buttonParams.y = clampedY
                    windowManager?.updateViewLayout(floatingButton, buttonParams)
                }
            }
            MotionEvent.ACTION_UP -> {
                longPressTimer?.removeCallbacksAndMessages(null)

                if (isDragging) {
                    // Сохраняем позицию после перетаскивания
                    serviceScope.launch {
                        prefsManager.saveButtonPosition(buttonParams.x.toFloat(), buttonParams.y.toFloat())
                    }
                } else if (!isLongPressTriggered) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastTapTime < DOUBLE_TAP_THRESHOLD) {
                        performAction("double_tap")
                    } else {
                        performAction("single_tap")
                    }
                    lastTapTime = currentTime
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                longPressTimer?.removeCallbacksAndMessages(null)
            }
        }
        return true
    }

    private fun showRadialMenu() {
        if (isMenuVisible) return
        isMenuVisible = true

        vibrate(50)

        menuView = LayoutInflater.from(this).inflate(R.layout.radial_menu, null)

        val menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        windowManager?.addView(menuView, menuParams)

        // Настраиваем кнопки
        setupMenuButton(R.id.btn_back, "back")
        setupMenuButton(R.id.btn_home, "home")
        setupMenuButton(R.id.btn_volume_up, "volume_up")
        setupMenuButton(R.id.btn_volume_down, "volume_down")
        setupMenuButton(R.id.btn_play_pause, "play_pause")
        setupMenuButton(R.id.btn_close, "close")

        // Анимация появления кнопок
        val buttons = listOf(
            R.id.btn_back, R.id.btn_home, R.id.btn_volume_up,
            R.id.btn_volume_down, R.id.btn_play_pause, R.id.btn_close
        )
        buttons.forEachIndexed { index, id ->
            menuView?.findViewById<ImageButton>(id)?.let { btn ->
                btn.alpha = 0f
                btn.scaleX = 0f
                btn.scaleY = 0f
                ObjectAnimator.ofFloat(btn, "alpha", 0f, 1f).apply {
                    duration = 200
                    startDelay = index * 50L
                    start()
                }
                ObjectAnimator.ofFloat(btn, "scaleX", 0f, 1f).apply {
                    duration = 300
                    startDelay = index * 50L
                    interpolator = OvershootInterpolator(1.5f)
                    start()
                }
                ObjectAnimator.ofFloat(btn, "scaleY", 0f, 1f).apply {
                    duration = 300
                    startDelay = index * 50L
                    interpolator = OvershootInterpolator(1.5f)
                    start()
                }
            }
        }
    }

    private fun hideRadialMenu() {
        if (!isMenuVisible || menuView == null) return
        isMenuVisible = false

        val buttons = listOf(
            R.id.btn_back, R.id.btn_home, R.id.btn_volume_up,
            R.id.btn_volume_down, R.id.btn_play_pause, R.id.btn_close
        )
        buttons.forEach { id ->
            menuView?.findViewById<ImageButton>(id)?.let { btn ->
                ObjectAnimator.ofFloat(btn, "alpha", 1f, 0f).apply { duration = 150; start() }
                ObjectAnimator.ofFloat(btn, "scaleX", 1f, 0f).apply { duration = 150; start() }
                ObjectAnimator.ofFloat(btn, "scaleY", 1f, 0f).apply { duration = 150; start() }
            }
        }

        android.os.Handler().postDelayed({
            windowManager?.removeView(menuView)
            menuView = null
        }, 200)
    }

    private fun setupMenuButton(buttonId: Int, action: String) {
        menuView?.findViewById<ImageButton>(buttonId)?.setOnClickListener {
            vibrate(30)
            performAction(action)
            hideRadialMenu()
        }
    }

    private fun performAction(action: String) {
        when (action) {
            "back" -> {
                GestureAccessibilityService.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
                )
                showToast("⬅️ Назад")
            }
            "home" -> {
                GestureAccessibilityService.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                )
                showToast("🏠 Домой")
            }
            "volume_up" -> {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                showToast("🔊 Громкость +")
            }
            "volume_down" -> {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                showToast("🔉 Громкость -")
            }
            "play_pause" -> {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val intent = Intent("android.intent.action.MEDIA_BUTTON")
                val keyEvent = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                intent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent)
                sendOrderedBroadcast(intent, null)
                showToast("⏯ Play/Pause")
            }
            "close" -> hideRadialMenu()
            "single_tap" -> showToast("👆 Одиночное нажатие")
            "double_tap" -> showToast("👆👆 Двойное нажатие")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun vibrate(duration: Long) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Gesture Control", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Gesture Control активен")
            .setContentText("Долгое нажатие — меню")
            .setSmallIcon(R.drawable.ic_notification)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { hideOverlay(); super.onDestroy() }
}
