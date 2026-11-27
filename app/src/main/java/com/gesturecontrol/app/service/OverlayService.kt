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
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import com.gesturecontrol.app.R

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingButton: View? = null
    private var isShowing = false
    
    // Кнопки кругового меню
    private val menuButtons = mutableListOf<View>()
    private var isMenuVisible = false
    
    // Для распознавания жестов
    private var lastTapTime = 0L
    private var longPressTimer: android.os.Handler? = null
    private var isLongPressTriggered = false
    
    companion object {
        private const val CHANNEL_ID = "GestureControlChannel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_SHOW = "SHOW"
        const val ACTION_HIDE = "HIDE"
        private const val LONG_PRESS_DURATION = 1000L // 1 секунда
        private const val DOUBLE_TAP_THRESHOLD = 300L
        private const val MENU_RADIUS = 150 // Расстояние кнопок меню от центра

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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> hideOverlay()
        }
        return START_STICKY
    }

    private fun showOverlay() {
        if (isShowing) return
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { 
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200 
        }

        floatingButton = LayoutInflater.from(this).inflate(R.layout.floating_button, null)
        floatingButton?.setOnTouchListener { _, event -> handleTouch(event); true }
        windowManager?.addView(floatingButton, params)
        isShowing = true
        
        startForeground(NOTIFICATION_ID, createNotification())
        Toast.makeText(this, "Кнопка активна. Долгое нажатие для меню.", Toast.LENGTH_LONG).show()
    }

    private fun hideOverlay() {
        if (!isShowing) return
        
        // Удаляем кнопки меню если они есть
        menuButtons.forEach { windowManager?.removeView(it) }
        menuButtons.clear()
        
        floatingButton?.let { windowManager?.removeView(it) }
        isShowing = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isLongPressTriggered = false
                longPressTimer = android.os.Handler()
                longPressTimer?.postDelayed({
                    if (!isLongPressTriggered) {
                        isLongPressTriggered = true
                        showRadialMenu()
                    }
                }, LONG_PRESS_DURATION)
            }
            MotionEvent.ACTION_UP -> {
                longPressTimer?.removeCallbacksAndMessages(null)
                if (!isLongPressTriggered) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastTapTime < DOUBLE_TAP_THRESHOLD) {
                        // Двойное нажатие
                        performAction("double_tap")
                    } else {
                        // Одиночное нажатие
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
        
        // Вибрация при открытии меню
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
        
        // Создаем 4 кнопки меню
        val actions = listOf(
            Pair("up", "Назад"),
            Pair("down", "Домой"),
            Pair("left", "Громкость -"),
            Pair("right", "Громкость +")
        )
        
        val centerX = floatingButton?.x?.toInt() ?: 100
        val centerY = floatingButton?.y?.toInt() ?: 200
        
        actions.forEachIndexed { index, (direction, label) ->
            val button = createMenuButton(label)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            
            // Позиционируем кнопки по кругу
            when (direction) {
                "up" -> {
                    params.x = centerX
                    params.y = centerY - MENU_RADIUS
                }
                "down" -> {
                    params.x = centerX
                    params.y = centerY + MENU_RADIUS
                }
                "left" -> {
                    params.x = centerX - MENU_RADIUS
                    params.y = centerY
                }
                "right" -> {
                    params.x = centerX + MENU_RADIUS
                    params.y = centerY
                }
            }
            
            button.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    performAction(direction)
                    hideRadialMenu()
                }
                true
            }
            
            windowManager?.addView(button, params)
            menuButtons.add(button)
            
            // Анимация появления
            button.alpha = 0f
            button.scaleX = 0f
            button.scaleY = 0f
            ObjectAnimator.ofFloat(button, "alpha", 0f, 1f).apply {
                duration = 200
                start()
            }
            ObjectAnimator.ofFloat(button, "scaleX", 0f, 1f).apply {
                duration = 200
                interpolator = OvershootInterpolator()
                start()
            }
            ObjectAnimator.ofFloat(button, "scaleY", 0f, 1f).apply {
                duration = 200
                interpolator = OvershootInterpolator()
                start()
            }
        }
    }

    private fun hideRadialMenu() {
        if (!isMenuVisible) return
        isMenuVisible = false
        
        menuButtons.forEach { button ->
            ObjectAnimator.ofFloat(button, "alpha", 1f, 0f).apply {
                duration = 150
                start()
            }
            ObjectAnimator.ofFloat(button, "scaleX", 1f, 0f).apply {
                duration = 150
                start()
            }
            ObjectAnimator.ofFloat(button, "scaleY", 1f, 0f).apply {
                duration = 150
                start()
            }
        }
        
        // Удаляем кнопки после анимации
        android.os.Handler().postDelayed({
            menuButtons.forEach { windowManager?.removeView(it) }
            menuButtons.clear()
        }, 200)
    }

    private fun createMenuButton(label: String): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#CC1A1A1A")) // Полупрозрачный темный
        }
        
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_notification)
            setColorFilter(Color.WHITE)
        }
        
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, 8)
        }
        layout.addView(icon, layoutParams)
        
        val textView = android.widget.TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
        }
        layout.addView(textView)
        
        // Делаем круглым
        val params = WindowManager.LayoutParams(
            120, 120,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        
        return layout
    }

    private fun performAction(action: String) {
        when (action) {
            "up" -> {
                // Назад
                GestureAccessibilityService.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
                )
                Toast.makeText(this, "Назад", Toast.LENGTH_SHORT).show()
            }
            "down" -> {
                // Домой
                GestureAccessibilityService.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                )
                Toast.makeText(this, "Домой", Toast.LENGTH_SHORT).show()
            }
            "left" -> {
                // Громкость -
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER,
                    0
                )
                Toast.makeText(this, "Громкость -", Toast.LENGTH_SHORT).show()
            }
            "right" -> {
                // Громкость +
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE,
                    0
                )
                Toast.makeText(this, "Громкость +", Toast.LENGTH_SHORT).show()
            }
            "single_tap" -> {
                Toast.makeText(this, "Одиночное нажатие", Toast.LENGTH_SHORT).show()
            }
            "double_tap" -> {
                Toast.makeText(this, "Двойное нажатие", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Gesture Control", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Gesture Control активен")
            .setContentText("Долгое нажатие для меню")
            .setSmallIcon(R.drawable.ic_notification)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { hideOverlay(); super.onDestroy() }
}
