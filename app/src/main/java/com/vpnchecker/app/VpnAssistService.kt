package com.vpnchecker.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Optional assist: only searches the currently visible VPN app for an explicit Connect action. */
class VpnAssistService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!VpnAssistState.enabled || event == null) return
        val root = rootInActiveWindow ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in VpnAssistState.allowedPackages) return
        val labels = listOf("connect", "start", "اتصال", "شروع", "وصل")
        if (VpnAssistState.clickedForPackage == pkg) return
        if (clickMatching(root, labels)) VpnAssistState.clickedForPackage = pkg
    }

    private fun clickMatching(node: AccessibilityNodeInfo, labels: List<String>): Boolean {
        val text = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
        if (node.isEnabled && node.isClickable && labels.any { text.contains(it, ignoreCase = true) }) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (clickMatching(child, labels)) return true
        }
        return false
    }

    override fun onInterrupt() = Unit
}

object VpnAssistState {
    @Volatile var enabled: Boolean = false
    @Volatile var allowedPackages: Set<String> = emptySet()
    @Volatile var clickedForPackage: String? = null
}
