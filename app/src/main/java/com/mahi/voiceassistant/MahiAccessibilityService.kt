package com.mahi.voiceassistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MahiAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow ?: return
        
        val sendButtons = rootNode.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
        if (sendButtons != null && sendButtons.isNotEmpty()) {
            sendButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    override fun onInterrupt() {}
}