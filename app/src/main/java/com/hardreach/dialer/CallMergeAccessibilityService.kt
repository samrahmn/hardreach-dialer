package com.hardreach.dialer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility Service to automatically tap "Merge" button during conference calls
 * Requires user to enable in Settings -> Accessibility
 */
class CallMergeAccessibilityService : AccessibilityService() {

    private val TAG = "CallMergeService"
    private val handler = Handler(Looper.getMainLooper())
    private var mergeAttempted = false
    private var callsActive = 0

    companion object {
        var isConnected = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        isConnected = true

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                   AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
            // Listen to ALL packages to catch any dialer app
            packageNames = null
        }
        serviceInfo = info

        Log.i(TAG, "✅ Accessibility Service CONNECTED and ready")
        Log.i(TAG, "Service will monitor ALL apps for merge button")
        RemoteLogger.i(applicationContext, TAG, "✅ Accessibility Service CONNECTED - ready to auto-merge")
        RemoteLogger.i(applicationContext, TAG, "Monitoring ALL apps for merge button")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Log ALL events to debug
        val packageName = event.packageName?.toString() ?: "unknown"
        val eventType = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "CONTENT_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "STATE_CHANGED"
            else -> "OTHER(${event.eventType})"
        }

        Log.d(TAG, "Event received: $eventType from $packageName")

        // Log if it's from a phone/dialer app
        if (packageName.contains("phone") || packageName.contains("dialer") ||
            packageName.contains("call") || packageName.contains("telecom")) {
            Log.i(TAG, "📞 Phone app event: $eventType from $packageName")
            RemoteLogger.i(applicationContext, TAG, "📞 Phone app detected: $packageName")
        }

        // Only process window changes
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                tryMergeCalls()
            }, 2000) // Wait 2s for UI to stabilize
        }
    }

    private fun tryMergeCalls() {
        try {
            val rootNode = rootInActiveWindow

            if (rootNode == null) {
                Log.w(TAG, "⚠️ Cannot access window - rootInActiveWindow is null")
                RemoteLogger.w(applicationContext, TAG, "⚠️ Cannot access current window")
                return
            }

            Log.i(TAG, "✓ Can access window - searching for merge button...")
            RemoteLogger.i(applicationContext, TAG, "🔍 Searching for merge button in window...")

            // FIRST: Look for and tap "More" button to reveal merge option
            val moreButton = findNodesByText(rootNode, "more", ignoreCase = true).firstOrNull()
            if (moreButton != null && moreButton.isClickable) {
                Log.i(TAG, "Found 'More' button - tapping to reveal menu...")
                RemoteLogger.i(applicationContext, TAG, "📱 Tapping 'More' button to reveal merge option...")

                val clicked = moreButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    Log.i(TAG, "✓ More button tapped - waiting for menu to open...")
                    RemoteLogger.i(applicationContext, TAG, "✓ More menu opened - searching for merge...")

                    // Wait for menu to open, then try again
                    handler.postDelayed({
                        tryFindAndClickMerge()
                    }, 1000)
                    return
                }
            }

            // If no "More" button, try to find merge directly
            tryFindAndClickMerge()

        } catch (e: Exception) {
            Log.e(TAG, "Error trying to merge calls: ${e.message}")
            RemoteLogger.e(applicationContext, TAG, "❌ Error auto-merging: ${e.message}")
        }
    }

    private fun tryFindAndClickMerge() {
        try {
            val rootNode = rootInActiveWindow ?: return

            // Look for "Merge" button with various possible texts
            val mergeTexts = listOf(
                "merge",
                "merge calls",
                "conference",
                "دمج", // Arabic
                "合并"  // Chinese
            )

            for (text in mergeTexts) {
                val nodes = findNodesByText(rootNode, text, ignoreCase = true)
                for (node in nodes) {
                    if (node.isClickable && node.isEnabled) {
                        Log.i(TAG, "Found merge button: '${node.text}' - clicking now!")
                        RemoteLogger.i(applicationContext, TAG, "✓ Found Merge button - auto-clicking!")

                        val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (clicked) {
                            Log.i(TAG, "✅ Successfully clicked merge button")
                            RemoteLogger.i(applicationContext, TAG, "✅ Merge button clicked successfully!")
                            mergeAttempted = true
                            return
                        }
                    }
                }
            }

            // Alternative: Look for merge button by view ID
            val mergeButton = findMergeButtonById(rootNode)
            if (mergeButton != null && mergeButton.isClickable && mergeButton.isEnabled) {
                Log.i(TAG, "Found merge button by ID - clicking!")
                RemoteLogger.i(applicationContext, TAG, "✓ Found Merge button (by ID) - auto-clicking!")

                val clicked = mergeButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    Log.i(TAG, "✅ Successfully clicked merge button")
                    RemoteLogger.i(applicationContext, TAG, "✅ Merge button clicked successfully!")
                    mergeAttempted = true
                    return
                }
            }

            // If we got here, no merge button was found
            Log.d(TAG, "No merge button found in current window")
            RemoteLogger.w(applicationContext, TAG, "⚠️ No merge button found - may not be on call screen yet")

        } catch (e: Exception) {
            Log.e(TAG, "Error trying to merge calls: ${e.message}")
            RemoteLogger.e(applicationContext, TAG, "❌ Error auto-merging: ${e.message}")
        }
    }

    private fun findNodesByText(
        node: AccessibilityNodeInfo,
        text: String,
        ignoreCase: Boolean = false
    ): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()

        node.text?.toString()?.let { nodeText ->
            if (nodeText.contains(text, ignoreCase)) {
                results.add(node)
            }
        }

        node.contentDescription?.toString()?.let { desc ->
            if (desc.contains(text, ignoreCase)) {
                results.add(node)
            }
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                results.addAll(findNodesByText(child, text, ignoreCase))
            }
        }

        return results
    }

    private fun findMergeButtonById(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Common merge button IDs across different phone dialers
        val mergeIds = listOf(
            "merge",
            "mergeButton",
            "merge_button",
            "conference_merge_button",
            "merge_calls"
        )

        node.viewIdResourceName?.let { id ->
            for (mergeId in mergeIds) {
                if (id.contains(mergeId, ignoreCase = true)) {
                    return node
                }
            }
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findMergeButtonById(child)?.let { return it }
            }
        }

        return null
    }

    override fun onInterrupt() {
        Log.i(TAG, "⚠️ Service interrupted")
        RemoteLogger.w(applicationContext, TAG, "⚠️ Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isConnected = false
        handler.removeCallbacksAndMessages(null)
        Log.i(TAG, "Service destroyed")
        RemoteLogger.i(applicationContext, TAG, "Accessibility Service destroyed")
    }
}
