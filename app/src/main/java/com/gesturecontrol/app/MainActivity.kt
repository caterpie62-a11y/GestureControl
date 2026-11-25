package com.gesturecontrol.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.gesturecontrol.app.service.OverlayService
import com.gesturecontrol.app.ui.screens.MainScreen
import com.gesturecontrol.app.ui.theme.GestureControlTheme

class MainActivity : ComponentActivity() {
    
    // Состояние: работает ли служба
    private var isServiceRunning by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GestureControlTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        isServiceRunning = isServiceRunning,
                        onToggleService = { toggleService() },
                        onOpenPermissions = { openOverlaySettings() }
                    )
                }
            }
        }
    }

    private fun toggleService() {
        // 1. Проверяем, есть ли разрешение на отображение поверх других окон
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Сначала дайте разрешение на отображение поверх других приложений", Toast.LENGTH_LONG).show()
            openOverlaySettings()
            return
        }

        // 2. Запускаем или останавливаем службу
        if (isServiceRunning) {
            OverlayService.stop(this)
            isServiceRunning = false
            Toast.makeText(this, "Служба остановлена", Toast.LENGTH_SHORT).show()
        } else {
            OverlayService.start(this)
            isServiceRunning = true
            Toast.makeText(this, "Служба запущена! Проверьте экран.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}
