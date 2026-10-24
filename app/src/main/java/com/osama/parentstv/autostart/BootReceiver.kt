package com.osama.parentstv.autostart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fallback for devices that deliver BOOT_COMPLETED to third-party apps (the emulator does). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AutoStartService.openPlayer(context)
    }
}
