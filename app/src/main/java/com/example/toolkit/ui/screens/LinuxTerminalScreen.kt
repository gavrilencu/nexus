package com.example.toolkit.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toolkit.ui.linux.LineKind
import com.example.toolkit.ui.linux.LinuxTerminalViewModel
import com.example.toolkit.ui.theme.AlertRed
import com.example.toolkit.ui.theme.BorderGreen
import com.example.toolkit.ui.theme.GhostWhite
import com.example.toolkit.ui.theme.MatrixBlack
import com.example.toolkit.ui.theme.NeonGreen
import com.example.toolkit.ui.theme.SoftGreen
import com.example.toolkit.ui.theme.TerminalGray
import com.example.toolkit.ui.theme.VoidBlack

private const val PROMPT = "root@nexus:~# "

/**
 * A full-screen, Termux-style terminal: the whole page is the console. Output
 * scrolls in place and the command line is an inline prompt at the bottom of
 * the scrollback — you type straight into it and press Enter to run, exactly
 * like a real shell, instead of a separate input box + buttons.
 *
 * On first entry it auto-installs the Ubuntu rootfs and auto-starts the shell,
 * so opening the screen drops you at a live prompt. A couple of client-side
 * conveniences are handled here before anything is sent to the shell:
 *   clear        → wipe the screen
 *   :bg on/off   → toggle the background service (keeps processes alive)
 *   :reinstall   → wipe and rebuild the environment
 */
@Composable
fun LinuxTerminalScreen(vm: LinuxTerminalViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val focusRequester = remember { FocusRequester() }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.setBackgroundService(true) }

    fun requestBackground(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.setBackgroundService(enabled)
        }
    }

    // Termux-like bootstrap: set up on first entry, then keep a live shell.
    LaunchedEffect(state.installed, state.installing, state.running, state.error) {
        when {
            !state.installed && !state.installing && state.error == null -> vm.installLinux()
            state.installed && !state.running && !state.installing -> vm.startTerminal()
        }
    }

    fun handleSubmit() {
        val cmd = state.input.trim()
        when {
            cmd.isEmpty() -> Unit
            cmd == "clear" -> { vm.clearOutput(); vm.setInput("") }
            cmd == ":bg on" -> { vm.setInput(""); requestBackground(true) }
            cmd == ":bg off" -> { vm.setInput(""); requestBackground(false) }
            cmd == ":reinstall" -> { vm.setInput(""); vm.resetLinux() }
            else -> vm.submit()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidBlack)
            .imePadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { runCatching { focusRequester.requestFocus() } }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        TerminalScrollback(
            lines = state.lines,
            fontSize = state.fontSize,
            installing = state.installing,
            installPercent = state.installPercent,
            installDetail = state.installDetail,
            error = state.error,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        InlinePrompt(
            value = state.input,
            onValueChange = vm::setInput,
            onSubmit = ::handleSubmit,
            fontSize = state.fontSize,
            focusRequester = focusRequester
        )

        ExtraKeysRow(
            onInsert = vm::insertAtCursor,
            onTab = vm::tabComplete,
            onHistoryPrev = vm::historyPrevious,
            onHistoryNext = vm::historyNext,
            onCtrlC = vm::sendControlC
        )
    }
}

@Composable
private fun TerminalScrollback(
    lines: List<com.example.toolkit.ui.linux.TerminalLine>,
    fontSize: Int,
    installing: Boolean,
    installPercent: Int,
    installDetail: String,
    error: String?,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    // Keep the newest output (and the prompt just below) in view as things stream.
    LaunchedEffect(lines.size, installing, installPercent) {
        val count = lines.size + if (installing || error != null) 1 else 0
        if (count > 0) listState.scrollToItem(count - 1)
    }
    SelectionContainer(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(lines, key = { it.id }) { line ->
                Text(
                    text = line.text,
                    color = when (line.kind) {
                        LineKind.SYSTEM -> NeonGreen
                        LineKind.COMMAND -> GhostWhite
                        LineKind.OUTPUT -> TerminalGray
                        LineKind.ERROR -> AlertRed
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize + 4).sp
                )
            }
            if (installing) {
                item(key = "installing") {
                    Text(
                        text = "⟳ setting up Ubuntu… $installPercent%  $installDetail",
                        color = SoftGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize + 4).sp
                    )
                }
            } else if (error != null) {
                item(key = "error") {
                    Text(
                        text = "✗ $error   (type :reinstall to retry)",
                        color = AlertRed,
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize + 4).sp
                    )
                }
            }
        }
    }
}

@Composable
private fun InlinePrompt(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    fontSize: Int,
    focusRequester: FocusRequester
) {
    // Auto-focus so the keyboard is ready the moment you land on the terminal.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val textStyle = TextStyle(
        color = GhostWhite,
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize.sp,
        lineHeight = (fontSize + 4).sp
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = PROMPT,
            color = NeonGreen,
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize.sp,
            lineHeight = (fontSize + 4).sp
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            textStyle = textStyle,
            cursorBrush = SolidColor(NeonGreen),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }, onDone = { onSubmit() })
        )
    }
}

/**
 * A compact, horizontally-scrolling row of the keys that are painful to reach
 * on a soft keyboard — the equivalent of Termux's extra-keys bar. History
 * arrows and Ctrl-C sit alongside the shell symbols.
 */
@Composable
private fun ExtraKeysRow(
    onInsert: (String) -> Unit,
    onTab: () -> Unit,
    onHistoryPrev: () -> Unit,
    onHistoryNext: () -> Unit,
    onCtrlC: () -> Unit
) {
    val symbols = listOf("|", "&&", "&", ">", ">>", "<", "~", "/", "-", "_", ".", ":", "$", "\"", "'", "`", "*")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Key("^C", onClick = onCtrlC, accent = true)
        Key("↑", onClick = onHistoryPrev)
        Key("↓", onClick = onHistoryNext)
        Key("Tab", onClick = onTab, accent = true)
        symbols.forEach { symbol -> Key(symbol) { onInsert(symbol) } }
    }
}

@Composable
private fun Key(label: String, accent: Boolean = false, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .background(if (accent) NeonGreen.copy(alpha = 0.14f) else MatrixBlack, RoundedCornerShape(8.dp))
            .border(1.dp, if (accent) NeonGreen.copy(alpha = 0.5f) else BorderGreen, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (accent) NeonGreen else GhostWhite,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}
