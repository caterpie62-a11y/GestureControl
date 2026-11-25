package com.gesturecontrol.app.model

data class Profile(
    val id: String,
    val name: String,
    val packageName: String,
    val triggerApp: String,
    val controlMode: ControlMode,
    val gestureMappings: List<GestureMapping>
)

enum class ControlMode {
    UI_CLICK, MEDIA_SESSION, ACCESSIBILITY
}
