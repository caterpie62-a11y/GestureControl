package com.gesturecontrol.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gesturecontrol.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isServiceRunning: Boolean,
    onToggleService: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Black).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Gesture Control", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = White)

        Card(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            colors = CardDefaults.cardColors(containerColor = DarkGray),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(8.dp), shape = RoundedCornerShape(50), color = if (isServiceRunning) Success else TextTertiary) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isServiceRunning) "Служба активна" else "Служба остановлена",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = White
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Долгое нажатие на кнопку — меню", fontSize = 14.sp, color = TextSecondary)
                }
                Button(
                    onClick = onToggleService,
                    colors = ButtonDefaults.buttonColors(containerColor = if (isServiceRunning) RedDark else Red),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.width(110.dp)
                ) {
                    Text(
                        text = if (isServiceRunning) "ВЫКЛ" else "ВКЛ",
                        color = White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Text(text = "Быстрые действия", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = White, modifier = Modifier.padding(top = 8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            QuickActionCard(Icons.Default.Lock, "Разрешения", Modifier.weight(1f), onClick = onOpenPermissions)
            QuickActionCard(Icons.Default.Star, "YouTube", Modifier.weight(1f), onClick = { })
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppControlCard(Icons.Default.Videocam, "YouTube", "MediaSession", {})
            AppControlCard(Icons.Default.Tv, "Lampa", "UI Click", {})
            AppControlCard(Icons.Default.Settings, "Системные команды", "Accessibility Service", {})
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.height(80.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DarkGray),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(imageVector = icon, contentDescription = title, tint = Red, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = title, fontSize = 12.sp, color = White, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppControlCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(72.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DarkGray),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = title, tint = Red, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = White)
                    Text(text = subtitle, fontSize = 14.sp, color = TextSecondary)
                }
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Перейти", tint = Red)
        }
    }
}
