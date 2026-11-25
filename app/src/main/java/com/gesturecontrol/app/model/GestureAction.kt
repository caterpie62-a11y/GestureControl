package com.gesturecontrol.app.model

enum class GestureType {
    SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT,
    SINGLE_TAP, DOUBLE_TAP, LONG_PRESS
}

enum class ActionType {
    GO_BACK, GO_HOME, SCREENSHOT, TOGGLE_FLASHLIGHT,
    VOLUME_UP, VOLUME_DOWN, PLAY_PAUSE, SKIP_FORWARD,
    SKIP_BACKWARD, STOP, OPEN_YOUTUBE, OPEN_LAMPA, NONE, OPEN_MENU
}

data class GestureMapping(
    val gestureType: GestureType,
    val actionType: ActionType
)
