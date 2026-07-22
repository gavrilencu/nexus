package com.example.toolkit.data.capture

import android.content.Context
import android.os.Build
import android.os.UserManager
import android.provider.Settings

/**
 * Real identity of THIS phone — the thing that used to show up as
 * "Unknown device" in the LAN list. Pulls the manufacturer/brand/model from
 * [Build], the name the user set for the device (Settings → About phone →
 * Device name) and the current Android user/profile name.
 */
data class LocalDevice(
    val userSetName: String?,   // e.g. "Andrei's Galaxy"
    val manufacturer: String,   // e.g. "samsung"
    val brand: String,          // e.g. "Samsung"
    val model: String,          // e.g. "SM-S911B"
    val marketName: String,     // brand + model, human readable
    val userName: String?       // current Android user / owner profile name
) {
    /** Best short label for the phone in the device list. */
    val displayName: String
        get() = userSetName?.takeIf { it.isNotBlank() } ?: marketName
}

object DeviceIdentity {

    fun local(context: Context): LocalDevice {
        val brand = Build.BRAND?.replaceFirstChar { it.uppercase() }?.takeIf { it.isNotBlank() } ?: "Android"
        val manufacturer = Build.MANUFACTURER
            ?.replaceFirstChar { it.uppercase() }
            ?.takeIf { it.isNotBlank() }
            ?: brand
        val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Device"

        // Avoid "Samsung SM-... " when brand already appears in the model.
        val market = if (model.startsWith(brand, ignoreCase = true)) model else "$brand $model"

        return LocalDevice(
            userSetName = userSetName(context),
            manufacturer = manufacturer,
            brand = brand,
            model = model,
            marketName = market,
            userName = userName(context)
        )
    }

    /** The name the user typed in Settings for the device / hotspot. */
    private fun userSetName(context: Context): String? {
        val cr = context.contentResolver
        val candidates = listOf(
            "device_name",       // Settings.Global.DEVICE_NAME
            "bluetooth_name"
        )
        for (key in candidates) {
            val g = runCatching { Settings.Global.getString(cr, key) }.getOrNull()
            if (!g.isNullOrBlank()) return g
            val s = runCatching { Settings.Secure.getString(cr, key) }.getOrNull()
            if (!s.isNullOrBlank()) return s
            val sys = runCatching { Settings.System.getString(cr, key) }.getOrNull()
            if (!sys.isNullOrBlank()) return sys
        }
        return null
    }

    /** Current Android user / owner profile name (e.g. "Andrei"). */
    private fun userName(context: Context): String? {
        return runCatching {
            val um = context.getSystemService(Context.USER_SERVICE) as? UserManager
            um?.userName?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
