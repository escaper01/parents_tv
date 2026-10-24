package com.osama.parentstv.autostart

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.osama.parentstv.ParentsTvApp
import org.json.JSONObject

/**
 * The "open at power-on" feature, as seen by the settings screens.
 *
 * Two things must both be true for the player to open by itself:
 *  1. the user's switch ([com.osama.parentstv.data.Settings.autoStart]), changeable from the TV
 *     and the web page at any time, and
 *  2. the auto-start accessibility service is enabled on this TV. Android does not let an app
 *     enable its own accessibility service, so this is a one-time step per TV: either the user
 *     switches it on in Settings > Accessibility with the remote, or it is enabled over ADB. If
 *     the TV granted WRITE_SECURE_SETTINGS over ADB (`pm grant`), the app can do it by itself.
 */
object AutoStart {
    private const val TAG = "ParentsTv.AutoStart"

    enum class State {
        /** Switch on and service enabled: the player opens at power-on. */
        ACTIVE,
        /** Switch off: the TV boots to its normal home screen. */
        OFF,
        /** Switch on but the service is not enabled on this TV yet: one-time setup needed. */
        NEEDS_SETUP,
    }

    fun component(context: Context) = ComponentName(context, AutoStartService::class.java)

    fun isServiceEnabled(context: Context): Boolean {
        val cr = context.contentResolver
        if (Settings.Secure.getInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false
        val enabled = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val full = component(context).flattenToString()
        val short = component(context).flattenToShortString()
        return enabled.split(':').any { it.equals(full, true) || it.equals(short, true) }
    }

    /** True when WRITE_SECURE_SETTINGS was granted over ADB, so the app can enable the service itself. */
    fun canEnableItself(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun state(context: Context): State {
        val app = context.applicationContext as ParentsTvApp
        return when {
            !app.settings.autoStart -> State.OFF
            isServiceEnabled(context) -> State.ACTIVE
            else -> State.NEEDS_SETUP
        }
    }

    /**
     * Turns the feature on or off. When turning on and the service is not enabled yet, enables it
     * directly if allowed; returns the resulting state so callers can guide the user if needed.
     */
    fun setEnabled(context: Context, on: Boolean): State {
        val app = context.applicationContext as ParentsTvApp
        app.settings.autoStart = on
        if (on && !isServiceEnabled(context) && canEnableItself(context)) enableServiceDirectly(context)
        return state(context)
    }

    private fun enableServiceDirectly(context: Context) {
        runCatching {
            val cr = context.contentResolver
            val ours = component(context).flattenToString()
            val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            // Keep any other accessibility services the TV already uses.
            val list = current.split(':').filter { it.isNotBlank() && !it.equals(ours, true) } + ours
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list.joinToString(":"))
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            Log.i(TAG, "Enabled the auto-start service directly")
        }.onFailure { Log.w(TAG, "Could not enable the service directly", it) }
    }

    /** Which screen [openAccessibilitySettings] managed to open. */
    enum class Opened { ACCESSIBILITY, SETTINGS, NONE }

    /**
     * Opens the TV's Accessibility screen. Android TV builds differ: some answer the standard
     * request with an empty placeholder ("frameworkpackagestubs"), so this skips placeholders and
     * tries the TV-settings variants before falling back to the main Settings screen.
     */
    fun openAccessibilitySettings(context: Context): Opened {
        val pm = context.packageManager
        // (The "ACCESSIBILITY_TV_OEM_LINK" action is deliberately not used: on stock Android TV it
        // resolves but closes at once unless the manufacturer configured it.)
        val candidates = listOf(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.accessibility.AccessibilityActivity"),
        )
        for (intent in candidates) {
            val target = pm.resolveActivity(intent, 0)?.activityInfo ?: continue
            if (target.packageName.contains("stub", true) || target.name.contains("stub", true)) continue
            if (start(context, intent)) return Opened.ACCESSIBILITY
        }
        if (start(context, Intent(Settings.ACTION_SETTINGS))) return Opened.SETTINGS
        Log.w(TAG, "No settings screen could be opened on this TV")
        return Opened.NONE
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false // not exported on this firmware
    }

    /** Everything the web page shows about auto-start. */
    fun toJson(context: Context): JSONObject = JSONObject()
        .put("enabled", (context.applicationContext as ParentsTvApp).settings.autoStart)
        .put("serviceEnabled", isServiceEnabled(context))
        .put("canEnableItself", canEnableItself(context))
        .put("state", state(context).name)
        .put("adbCommands", org.json.JSONArray(adbCommands(context)))

    fun adbCommands(context: Context): List<String> = listOf(
        "adb shell settings put secure enabled_accessibility_services ${component(context).flattenToString()}",
        "adb shell settings put secure accessibility_enabled 1",
    )
}
