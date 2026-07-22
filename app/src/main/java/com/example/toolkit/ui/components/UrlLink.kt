package com.example.toolkit.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.toolkit.ui.theme.MuteGreen
import com.example.toolkit.ui.theme.NeonGreen

/**
 * Clickable URL/host row used across recon modules.
 * - Tap → open in the system browser
 * - Long-press → copy to clipboard
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UrlLink(
    url: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    baseUrl: String? = null,
    color: Color = NeonGreen,
    showHint: Boolean = true,
    maxLines: Int = 3
) {
    val context = LocalContext.current
    val openable = normalizeUrl(url, baseUrl) ?: return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { openInBrowser(context, openable) },
                onLongClick = { copyToClipboard(context, openable) }
            )
            .padding(vertical = 2.dp)
    ) {
        if (!label.isNullOrBlank()) {
            Text(label, color = MuteGreen, style = MaterialTheme.typography.labelSmall)
        }
        Text(
            text = url,
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            textDecoration = TextDecoration.Underline,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall
        )
        if (showHint) {
            Text(
                "tap open · hold copy",
                color = MuteGreen,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** Open a URL or hostname (adds https:// when missing). */
fun openInBrowser(context: Context, raw: String, baseUrl: String? = null) {
    val url = normalizeUrl(raw, baseUrl) ?: return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        Toast.makeText(context, "Cannot open URL", Toast.LENGTH_SHORT).show()
    }
}

fun copyToClipboard(context: Context, text: String, toast: String = "Copied") {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("url", text))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}

/**
 * Accepts absolute http(s) URLs, bare hosts, or relative paths when [baseUrl]
 * is provided — returns a browser-ready URL, or null if it shouldn't be opened.
 */
fun normalizeUrl(raw: String, baseUrl: String? = null): String? {
    val t = raw.trim()
    if (t.isBlank()) return null
    when {
        t.startsWith("http://", ignoreCase = true) ||
            t.startsWith("https://", ignoreCase = true) -> return t
        t.startsWith("//") -> return "https:$t"
        Regex("(?i)^[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}(/.*)?(\\?.*)?$").matches(t) ->
            return "https://$t"
    }
    // Relative path — resolve against base when available
    if (baseUrl != null && (t.startsWith("/") || !t.contains("://"))) {
        val base = baseUrl.trim().trimEnd('/')
        if (base.startsWith("http://", true) || base.startsWith("https://", true)) {
            return if (t.startsWith("/")) "$base$t" else "$base/$t"
        }
    }
    return null
}
