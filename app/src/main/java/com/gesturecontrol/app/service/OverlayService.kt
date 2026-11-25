package com.gesturecontrol.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.gesturecontrol.app.R

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingButton: View? = null
    private var isShowing = false

    companion object {
        private const val CHANNEL_ID = "GestureControlChannel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_SHOW = "SHOW"
        const val ACTION_HIDE = "HIDE"

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
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 100; y = 200 }

        floatingButton = LayoutInflater.from(this).inflate(R.layout.floating_button, null)
        floatingButton?.setOnTouchListener { _, event -> handleTouch(event); true }
        windowManager?.addView(floatingButton, params)
        isShowing = true
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun hideOverlay() {
        if (!isShowing) return
        floatingButton?.let { windowManager?.removeView(it) }
        isShowing = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_MOVE -> true
            else -> false
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Gesture Control", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Gesture Control активен")
            .setContentText("Служба работает в фоне")
            .setSmallIcon(R.drawable.ic_notification)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { hideOverlay(); super.onDestroy() }
}
