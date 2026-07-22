package com.example.toolkit.ui.linux

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.toolkit.data.linux.LinuxEnvironmentHolder
import com.example.toolkit.data.linux.LinuxSessionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TerminalLine(
    val id: Long,
    val text: String,
    val kind: LineKind
)

enum class LineKind { SYSTEM, COMMAND, OUTPUT, ERROR }

data class LinuxTerminalState(
    val installed: Boolean = false,
    val installing: Boolean = false,
    val installPercent: Int = 0,
    val installStage: String = "",
    val installDetail: String = "",
    val running: Boolean = false,
    val backgroundService: Boolean = false,
    val fontSize: Int = 12,
    val input: String = "",
    val lines: List<TerminalLine> = emptyList(),
    val history: List<String> = emptyList(),
    val historyIndex: Int = -1,
    val error: String? = null,
    val architecture: String = ""
)

/**
 * Backed by a *shared, app-wide* [com.example.toolkit.data.linux.LinuxEnvironment]
 * (see [LinuxEnvironmentHolder]) instead of a private instance. Combined with
 * never stopping the shell in [onCleared], this means the same bash session —
 * and anything you started inside it — survives screen navigation, app
 * backgrounding, and lock/unlock cycles. Real persistence beyond that (i.e.
 * surviving the OS reclaiming the whole process under memory pressure) is
 * provided by [LinuxSessionService] when the "run in background" toggle is on.
 */
class LinuxTerminalViewModel(app: Application) : AndroidViewModel(app) {
    private val environment = LinuxEnvironmentHolder.get(app)
    private val _state = MutableStateFlow(
        LinuxTerminalState(
            installed = environment.isInstalled,
            running = environment.isRunning,
            architecture = runCatching { environment.architecture }.getOrDefault("arm64"),
            lines = listOf(
                TerminalLine(
                    1,
                    "NEXUS Linux · Ubuntu + apt (PRoot)",
                    LineKind.SYSTEM
                )
            )
        )
    )
    val state: StateFlow<LinuxTerminalState> = _state.asStateFlow()
    private var nextLineId = 2L

    fun setInput(value: String) {
        _state.update { it.copy(input = value, historyIndex = -1) }
    }

    /** Inserts a symbol/snippet at the end of the current input — for keys that are
     *  awkward to reach on a phone keyboard (|, ~, /, &, etc). */
    fun insertAtCursor(fragment: String) {
        _state.update { it.copy(input = it.input + fragment, historyIndex = -1) }
    }

    fun installLinux() {
        if (_state.value.installing) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(installing = true, error = null) }
            try {
                environment.reset()
                _state.update { it.copy(installed = false) }
                environment.prepare { progress ->
                    _state.update {
                        it.copy(
                            installPercent = progress.percent,
                            installStage = progress.stage,
                            installDetail = progress.detail
                        )
                    }
                }
                _state.update {
                    it.copy(
                        installed = true,
                        installing = false,
                        installPercent = 100,
                        installDetail = "Ubuntu + apt ready"
                    )
                }
                append("Install complete. Starting shell…", LineKind.SYSTEM)
                startTerminalInternal()
            } catch (e: Exception) {
                val msg = e.message ?: "Install failed"
                _state.update {
                    it.copy(installing = false, installed = false, error = msg)
                }
                append("Install error: $msg", LineKind.ERROR)
            }
        }
    }

    fun startTerminal() {
        if (!environment.isInstalled || _state.value.running) return
        viewModelScope.launch(Dispatchers.IO) { startTerminalInternal() }
    }

    private fun startTerminalInternal() {
        try {
            environment.startShell(
                onOutput = { append(it, LineKind.OUTPUT) },
                onExit = { code ->
                    _state.update { it.copy(running = false) }
                    append("Shell exited (code $code). Press Start to reopen.", LineKind.SYSTEM)
                }
            )
            _state.update { it.copy(running = true, error = null) }
        } catch (e: Exception) {
            _state.update { it.copy(running = false, error = e.message) }
            append("Cannot start shell: ${e.message}", LineKind.ERROR)
        }
    }

    fun submit() {
        val command = _state.value.input.trimEnd()
        if (command.isBlank()) return
        if (!_state.value.running) {
            _state.update { it.copy(error = "Start the terminal first") }
            return
        }
        append("root@nexus:~# $command", LineKind.COMMAND)
        _state.update {
            it.copy(
                input = "",
                history = (listOf(command) + it.history.filterNot { old -> old == command }).take(80),
                historyIndex = -1,
                error = null
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                environment.send(command)
            } catch (e: Exception) {
                append("Error: ${e.message}", LineKind.ERROR)
                _state.update { it.copy(running = false) }
            }
        }
    }

    fun runCommand(command: String) {
        if (!_state.value.running) {
            _state.update { it.copy(error = "Start the terminal first") }
            return
        }
        _state.update { it.copy(input = command) }
        submit()
    }

    fun historyPrevious() {
        val history = _state.value.history
        if (history.isEmpty()) return
        val next = (_state.value.historyIndex + 1).coerceAtMost(history.lastIndex)
        _state.update { it.copy(historyIndex = next, input = history[next]) }
    }

    fun historyNext() {
        val current = _state.value.historyIndex
        if (current <= 0) {
            _state.update { it.copy(historyIndex = -1, input = "") }
        } else {
            val next = current - 1
            _state.update { it.copy(historyIndex = next, input = it.history[next]) }
        }
    }

    fun sendControlC() {
        if (!_state.value.running) return
        append("^C · restarting shell…", LineKind.COMMAND)
        environment.stopShell()
        _state.update { it.copy(running = false) }
        viewModelScope.launch(Dispatchers.IO) { startTerminalInternal() }
    }

    fun stopTerminal() {
        environment.stopShell()
        _state.update { it.copy(running = false, backgroundService = false) }
        LinuxSessionService.stop(getApplication())
    }

    /**
     * Toggles the foreground service that protects this shell (and anything
     * running inside it) from being killed when the user leaves the screen
     * or the app. This is what makes `nohup python3 -m http.server 8080 &`
     * actually keep serving after you switch apps.
     */
    fun setBackgroundService(enabled: Boolean) {
        val app: Application = getApplication()
        if (enabled) {
            if (!_state.value.running) {
                _state.update { it.copy(error = "Start the terminal first") }
                return
            }
            LinuxSessionService.start(app)
            append("Background session ON — the shell keeps running after you leave this screen.", LineKind.SYSTEM)
        } else {
            LinuxSessionService.stop(app)
            append("Background session OFF — the shell may be killed once you leave the app.", LineKind.SYSTEM)
        }
        _state.update { it.copy(backgroundService = enabled) }
    }

    fun increaseFont() {
        _state.update { it.copy(fontSize = (it.fontSize + 1).coerceAtMost(18)) }
    }

    fun decreaseFont() {
        _state.update { it.copy(fontSize = (it.fontSize - 1).coerceAtLeast(9)) }
    }

    fun clearOutput() {
        _state.update { it.copy(lines = emptyList()) }
    }

    fun resetLinux() {
        viewModelScope.launch(Dispatchers.IO) {
            LinuxSessionService.stop(getApplication())
            environment.reset()
            _state.update {
                LinuxTerminalState(
                    installed = false,
                    architecture = runCatching { environment.architecture }.getOrDefault("arm64"),
                    lines = listOf(
                        TerminalLine(nextLineId++, "Environment wiped. Install again.", LineKind.SYSTEM)
                    )
                )
            }
        }
    }

    private fun append(text: String, kind: LineKind) {
        if (text.isEmpty()) return
        _state.update { current ->
            current.copy(
                lines = (current.lines + TerminalLine(nextLineId++, text, kind)).takeLast(1500)
            )
        }
    }

    // Deliberately does NOT stop the shell — see class kdoc. The shared
    // LinuxEnvironment (and, if the user enabled it, LinuxSessionService)
    // keep it alive independent of this ViewModel's lifecycle.
    override fun onCleared() {
        super.onCleared()
    }
}
