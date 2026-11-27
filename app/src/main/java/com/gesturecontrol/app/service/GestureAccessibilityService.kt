package com.gesturecontrol.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class GestureAccessibilityService : AccessibilityService() {

    companion object {
        private var instance: GestureAccessibilityService? = null

        fun performGlobalAction(action: Int): Boolean = instance?.run { performGlobalAction(action) } ?: false
        fun isRunning(): Boolean = instance != null

        fun togglePlayPause(): Boolean = instance?.findAndClickMediaButton() ?: false
    }

    override fun onServiceConnected() { super.onServiceConnected(); instance = this }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            if (it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val packageName = it.packageName?.toString()
            }
        }
    }

    override fun onInterrupt() {}

    private fun findAndClickMediaButton(): Boolean {
        val root = rootInActiveWindow ?: return false
        val keywords = listOf("pause", "play", "пауза", "воспроизведение", "стоп", "stop")
        for (kw in keywords) {
            val node = findNodeByTextRecursive(root, kw)
            if (node != null && node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }
        // Если кликабельного узла нет — ищем по contentDescription
        for (kw in keywords) {
            val node = findNodeByDescRecursive(root, kw)
            if (node != null) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }
        return false
    }

    private fun findNodeByTextRecursive(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val r = findNodeByTextRecursive(child, text)
            if (r != null) return r
        }
        return null
    }

    private fun findNodeByDescRecursive(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString()?.contains(text, ignoreCase = true) == true) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val r = findNodeByDescRecursive(child, text)
            if (r != null) return r
        }
        return null
    }

    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val rootNode = rootInActiveWindow ?: return null
        return findNodeByTextRecursive(rootNode, text)
    }

    fun clickNode(node: AccessibilityNodeInfo): Boolean = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

    fun performSwipeGesture(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) {
        val path = Path().apply { moveTo(startX, startY); lineTo(endX, endY) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration)).build()
        dispatchGesture(gesture, null, null)
    }
}
