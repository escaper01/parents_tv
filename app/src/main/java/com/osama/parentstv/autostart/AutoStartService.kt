package com.osama.parentstv.autostart

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.osama.parentstv.MainActivity

/**
 * Brings the player to the front when the TV boots and when it wakes from standby.
 *
 * Android binds enabled accessibility services at every boot, which works on this TV's firmware
 * where third-party BOOT_COMPLETED receivers are never run. Enable once over ADB:
 *   adb shell settings put secure enabled_accessibility_services com.osama.parentstv/.autostart.AutoStartService
 *   adb shell settings put secure accessibility_enabled 1
 *
 * The player is opened immediately. The stock launcher may still be starting and draw over it,
 * so for a short guard window the service watches window changes and, the first time a home
 * screen comes to the front, reopens the player once. After that the user is free to press Home.
 */
class AutoStartService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private var homePackages: Set<String> = emptySet()
    private var guardUntil = 0L

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "Screen on, reopening player")
            main.postDelayed({ openAndGuard(WAKE_GUARD_MS) }, WAKE_DELAY_MS)
        }
    }

    override fun onServiceConnected() {
        homePackages = findHomePackages()
        Log.i(TAG, "Service connected, opening player (home screens: $homePackages)")
        registerReceiver(screenOn, IntentFilter(Intent.ACTION_SCREEN_ON))
        openAndGuard(BOOT_GUARD_MS)
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenOn) }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (SystemClock.elapsedRealtime() > guardUntil) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in homePackages) {
            Log.i(TAG, "Home screen $pkg came up over the player, reopening it")
            guardUntil = 0 // only once, so a deliberate Home press later is respected
            main.postDelayed({ openPlayer(this) }, RELAUNCH_DELAY_MS)
        }
    }

    override fun onInterrupt() = Unit

    private fun openAndGuard(guardMs: Long) {
        guardUntil = SystemClock.elapsedRealtime() + guardMs
        openPlayer(this)
    }

    private fun findHomePackages(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.queryIntentActivities(home, 0)
            .map { it.activityInfo.packageName }
            .filter { it != packageName }
            .toSet()
    }

    companion object {
        private const val TAG = "ParentsTv.AutoStart"
        private const val BOOT_GUARD_MS = 60_000L
        private const val WAKE_GUARD_MS = 15_000L
        private const val WAKE_DELAY_MS = 500L
        private const val RELAUNCH_DELAY_MS = 300L

        fun openPlayer(context: Context) {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }
    }
}
