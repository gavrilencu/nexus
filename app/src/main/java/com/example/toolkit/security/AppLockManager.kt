package com.example.toolkit.security

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object AppLockManager {

    fun findActivity(context: Context): FragmentActivity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is FragmentActivity) return ctx
            ctx = ctx.baseContext
        }
        return ctx as? FragmentActivity
    }

    fun canAuthenticate(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        // Prefer weak+credential (most devices), then strong+credential, then biometric only
        return listOf(
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL,
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL,
            BIOMETRIC_WEAK,
            BIOMETRIC_STRONG,
            DEVICE_CREDENTIAL
        ).any { auth ->
            manager.canAuthenticate(auth) == BiometricManager.BIOMETRIC_SUCCESS
        }
    }

    fun statusMessage(context: Context): String {
        val manager = BiometricManager.from(context)
        val auth = preferredAuthenticators(manager)
        return when (manager.canAuthenticate(auth)) {
            BiometricManager.BIOMETRIC_SUCCESS ->
                "Unlock with fingerprint / face or device PIN"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "No fingerprint or PIN set — set a screen lock in Android Settings"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "Biometric hardware temporarily unavailable"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                "No biometric hardware — use device PIN if available"
            else -> "Device authentication unavailable"
        }
    }

    private fun preferredAuthenticators(manager: BiometricManager): Int {
        val options = listOf(
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL,
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL,
            BIOMETRIC_WEAK,
            BIOMETRIC_STRONG,
            DEVICE_CREDENTIAL
        )
        return options.firstOrNull {
            manager.canAuthenticate(it) == BiometricManager.BIOMETRIC_SUCCESS
        } ?: (BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
    }

    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit = {}
    ) {
        if (!canAuthenticate(activity)) {
            onError("No fingerprint or PIN enrolled on this device")
            return
        }

        try {
            val manager = BiometricManager.from(activity)
            val authenticators = preferredAuthenticators(manager)
            val executor = ContextCompat.getMainExecutor(activity)

            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON ->
                                onError("Cancelled — tap Unlock to try again")
                            BiometricPrompt.ERROR_LOCKOUT,
                            BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                                onError("Too many attempts — use PIN or wait")
                            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ->
                                onError("No PIN/password set on device")
                            BiometricPrompt.ERROR_HW_NOT_PRESENT,
                            BiometricPrompt.ERROR_HW_UNAVAILABLE,
                            BiometricPrompt.ERROR_NO_BIOMETRICS ->
                                onError(errString.toString())
                            else -> onError("Error $errorCode: $errString")
                        }
                    }

                    override fun onAuthenticationFailed() {
                        onFailed()
                    }
                }
            )

            val info = buildPromptInfo(authenticators)
            prompt.authenticate(info)
        } catch (e: Exception) {
            onError("Cannot open unlock prompt: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun buildPromptInfo(authenticators: Int): BiometricPrompt.PromptInfo {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("NEXUS Secure Access")
            .setSubtitle("Use fingerprint, face, or device PIN")
            .setConfirmationRequired(false)

        val allowsDeviceCredential = authenticators and DEVICE_CREDENTIAL != 0

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: biometric and/or device PIN
            builder.setAllowedAuthenticators(authenticators).build()
        } else if (allowsDeviceCredential) {
            // Android 8–10: PIN fallback via deprecated API
            builder.setDeviceCredentialAllowed(true).build()
        } else {
            // Biometric only — Cancel button required
            builder.setNegativeButtonText("Cancel").build()
        }
    }
}
