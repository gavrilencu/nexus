package com.example.toolkit.ui.lock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.toolkit.security.AppLockManager
import com.example.toolkit.ui.components.NexusButton
import com.example.toolkit.ui.theme.AccentGradient
import com.example.toolkit.ui.theme.AlertAmber
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.GlassBorder
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.PanelGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack

/**
 * Gates [content] behind biometric / device-credential auth.
 *
 * IMPORTANT: [content] is kept in composition at all times — the lock screen is
 * rendered as an opaque overlay on top of it, never as a replacement for it.
 *
 * Previously this gate did `if (unlocked) content() else LockScreen()`, which
 * fully *removed* content (including the nav graph, its ViewModels, and any
 * screen-local `remember` state such as an in-progress search) from the
 * composition whenever the app was locked. Locking happens on every
 * `ON_STOP` — which fires not just when the user backgrounds the app, but
 * also when a search result opens a browser/share sheet, or even while the
 * biometric system dialog itself is shown. The result: any search the user
 * had typed was wiped every single time they authenticated. Keeping content
 * mounted underneath the overlay fixes this — nothing the user typed is ever
 * torn down, it's simply covered until they unlock again.
 */
@Composable
fun AppLockGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var lockAvailable by remember {
        mutableStateOf(AppLockManager.canAuthenticate(context))
    }
    var unlocked by remember { mutableStateOf(!lockAvailable) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf(AppLockManager.statusMessage(context)) }
    var promptRunning by remember { mutableStateOf(false) }
    var autoPromptKey by remember { mutableStateOf(0) }

    fun requestUnlock(force: Boolean = false) {
        val activity = AppLockManager.findActivity(context)
        if (activity == null) {
            error = "Activity not ready — reopen the app"
            status = "Lock error"
            promptRunning = false
            return
        }

        lockAvailable = AppLockManager.canAuthenticate(activity)
        if (!lockAvailable) {
            // No PIN/fingerprint enrolled — allow entry
            unlocked = true
            promptRunning = false
            return
        }

        if (promptRunning && !force) return
        promptRunning = true
        error = null
        status = "Waiting for fingerprint / PIN…"

        AppLockManager.authenticate(
            activity = activity,
            onSuccess = {
                promptRunning = false
                unlocked = true
                error = null
                status = "Access granted"
            },
            onError = { msg ->
                promptRunning = false
                unlocked = false
                error = msg
                status = "Locked — tap Unlock to try again"
            },
            onFailed = {
                // Keep prompt open usually; show hint
                error = "Not recognized — try again or use PIN"
            }
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (AppLockManager.canAuthenticate(context)) {
                        unlocked = false
                        promptRunning = false
                        status = "Session locked"
                        error = null
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    lockAvailable = AppLockManager.canAuthenticate(context)
                    if (!unlocked && lockAvailable) {
                        autoPromptKey++
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(autoPromptKey, unlocked, lockAvailable) {
        if (!unlocked && lockAvailable) {
            // Small delay so activity/window is ready for system dialog
            kotlinx.coroutines.delay(250)
            requestUnlock(force = true)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Always composed — see kdoc above for why this must never be conditional.
        content()

        AnimatedVisibility(
            visible = !unlocked,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LockScreen(
                status = status,
                error = error,
                lockAvailable = lockAvailable,
                onUnlockClick = {
                    promptRunning = false
                    requestUnlock(force = true)
                }
            )
        }
    }
}

@Composable
private fun LockScreen(
    status: String,
    error: String?,
    lockAvailable: Boolean,
    onUnlockClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = PanelGreen.copy(alpha = 0.9f)),
            border = BorderStroke(1.dp, GlassBorder.copy(alpha = 0.10f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(AccentGradient, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = com.example.toolkit.ui.theme.OnAccent,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "NEXUS",
                    color = GhostWhite,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp
                )
                Text(
                    text = "SECURE ACCESS",
                    color = MuteGreen,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(28.dp))
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(VoidBlack, CircleShape)
                        .border(1.dp, GlassBorder.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = status,
                    color = TerminalGray,
                    textAlign = TextAlign.Center
                )
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = AlertRed, textAlign = TextAlign.Center)
                }
                if (!lockAvailable) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Set PIN or fingerprint in Android Settings → Security, then reopen NEXUS.",
                        color = AlertAmber,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                NexusButton(
                    text = if (lockAvailable) "Unlock with fingerprint / PIN" else "Continue",
                    onClick = onUnlockClick
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your in-progress search and results are kept exactly as you left them.",
                    color = MuteGreen.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}
