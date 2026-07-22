package com.example.toolkit.data.capture

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager

data class SimCard(
    val slot: Int,
    val carrier: String,
    val simOperator: String,
    val countryIso: String,
    val phoneNumber: String?,
    val roaming: Boolean
)

data class SimInfo(
    val hasTelephony: Boolean,
    val simState: String,
    val networkType: String,
    val dataState: String,
    val cards: List<SimCard>
) {
    companion object {
        val NONE = SimInfo(false, "No telephony", "—", "—", emptyList())
    }
}

object SimInfoProvider {

    @SuppressLint("MissingPermission", "HardwareIds")
    fun current(context: Context): SimInfo {
        val pm = context.packageManager
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) return SimInfo.NONE

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return SimInfo.NONE

        val simState = simStateLabel(tm.simState)
        val networkType = networkTypeLabel(context, tm)
        val dataState = dataStateLabel(tm.dataState)

        val cards = mutableListOf<SimCard>()
        if (tm.simState == TelephonyManager.SIM_STATE_READY) {
            val phone = runCatching {
                @Suppress("DEPRECATION")
                tm.line1Number?.takeIf { it.isNotBlank() }
            }.getOrNull()
            cards += SimCard(
                slot = 0,
                carrier = tm.networkOperatorName?.ifBlank { "—" } ?: "—",
                simOperator = tm.simOperatorName?.ifBlank { "—" } ?: "—",
                countryIso = tm.simCountryIso?.uppercase()?.ifBlank { "—" } ?: "—",
                phoneNumber = phone,
                roaming = runCatching { tm.isNetworkRoaming }.getOrDefault(false)
            )
        }

        return SimInfo(
            hasTelephony = true,
            simState = simState,
            networkType = networkType,
            dataState = dataState,
            cards = cards
        )
    }

    private fun simStateLabel(state: Int): String = when (state) {
        TelephonyManager.SIM_STATE_ABSENT -> "Absent"
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN required"
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK required"
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network locked"
        TelephonyManager.SIM_STATE_READY -> "Ready"
        TelephonyManager.SIM_STATE_NOT_READY -> "Not ready"
        TelephonyManager.SIM_STATE_PERM_DISABLED -> "Disabled"
        TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "IO error"
        TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "Restricted"
        else -> "Unknown"
    }

    private fun dataStateLabel(state: Int): String = when (state) {
        TelephonyManager.DATA_DISCONNECTED -> "Disconnected"
        TelephonyManager.DATA_CONNECTING -> "Connecting"
        TelephonyManager.DATA_CONNECTED -> "Connected"
        TelephonyManager.DATA_SUSPENDED -> "Suspended"
        else -> "Unknown"
    }

    @SuppressLint("MissingPermission")
    private fun networkTypeLabel(context: Context, tm: TelephonyManager): String {
        val type = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            ) {
                tm.dataNetworkType
            } else {
                @Suppress("DEPRECATION")
                tm.networkType
            }
        }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)

        return when (type) {
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G / LTE"
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            else -> "—"
        }
    }
}
