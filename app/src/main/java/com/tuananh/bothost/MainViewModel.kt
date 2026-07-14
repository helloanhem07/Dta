package com.tuananh.bothost

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tuananh.bothost.data.BotProject
import com.tuananh.bothost.data.BotRepository
import com.tuananh.bothost.data.BotRuntimeState
import com.tuananh.bothost.data.RuntimeType
import com.tuananh.bothost.metrics.SystemMetricsMonitor
import com.tuananh.bothost.termux.CommandResult
import com.tuananh.bothost.termux.CommandResultBus
import com.tuananh.bothost.termux.TermuxBridge
import com.tuananh.bothost.termux.TermuxContract
import com.tuananh.bothost.util.ImportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BotRepository(application)
    private val bridge = TermuxBridge(application)
    private val metricsMonitor = SystemMetricsMonitor(application)

    val bots = repository.bots
    val metrics = metricsMonitor.metrics

    private val _runtimeStates = MutableStateFlow<Map<String, BotRuntimeState>>(emptyMap())
    val runtimeStates: StateFlow<Map<String, BotRuntimeState>> = _runtimeStates.asStateFlow()

    private val _console = MutableStateFlow<Map<String, String>>(emptyMap())
    val console: StateFlow<Map<String, String>> = _console.asStateFlow()

    private val _envContent = MutableStateFlow<Map<String, String>>(emptyMap())
    val envContent: StateFlow<Map<String, String>> = _envContent.asStateFlow()

    private val _busyBots = MutableStateFlow<Set<String>>(emptySet())
    val busyBots: StateFlow<Set<String>> = _busyBots.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val termuxInstalled: Boolean get() = bridge.isTermuxInstalled()

    init {
        metricsMonitor.start(viewModelScope)
        viewModelScope.launch {
            CommandResultBus.results.collect(::handleCommandResult)
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun importBot(
        zipUri: Uri,
        name: String,
        runtime: RuntimeType,
        entryFile: String,
        installCommand: String,
        startCommand: String,
        autoRestart: Boolean
    ) {
        val id = UUID.randomUUID().toString().take(8)
        val bot = BotProject(
            id = id,
            name = name.trim().ifBlank { "Bot $id" },
            runtime = runtime,
            workDir = "${TermuxContract.TERMUX_HOME}/bothost/$id",
            entryFile = entryFile.trim(),
            installCommand = installCommand.trim(),
            startCommand = startCommand.trim(),
            autoRestart = autoRestart
        )
        repository.add(bot)
        setBusy(id, true)
        viewModelScope.launch {
            runCatching {
                val path = withContext(Dispatchers.IO) {
                    ImportManager.copyZipToSharedDownloads(getApplication(), zipUri, id)
                }
                bridge.importZip(bot, path).getOrThrow()
            }.onFailure {
                setBusy(id, false)
                _message.value = "Import lỗi: ${it.message}"
            }
        }
    }

    fun install(bot: BotProject) = dispatch(bot.id) { bridge.install(bot) }
    fun start(bot: BotProject) = dispatch(bot.id) { bridge.start(bot) }
    fun stop(bot: BotProject) = dispatch(bot.id) { bridge.stop(bot) }
    fun restart(bot: BotProject) = dispatch(bot.id) { bridge.restart(bot) }
    fun refreshStatus(bot: BotProject) = dispatch(bot.id, showBusy = false) { bridge.status(bot) }
    fun refreshLogs(bot: BotProject) = dispatch(bot.id, showBusy = false) { bridge.logs(bot) }
    fun clearLogs(bot: BotProject) = dispatch(bot.id) { bridge.clearLogs(bot) }
    fun readEnv(bot: BotProject) = dispatch(bot.id, showBusy = false) { bridge.readEnv(bot) }
    fun writeEnv(bot: BotProject, content: String) = dispatch(bot.id) { bridge.writeEnv(bot, content) }

    fun updateBot(bot: BotProject) {
        repository.update(bot)
        _message.value = "Đã lưu cấu hình ${bot.name}."
    }

    fun deleteBot(bot: BotProject, deleteFiles: Boolean) {
        repository.remove(bot.id)
        _runtimeStates.value = _runtimeStates.value - bot.id
        _console.value = _console.value - bot.id
        _envContent.value = _envContent.value - bot.id
        if (deleteFiles) bridge.removeFiles(bot)
        _message.value = if (deleteFiles) "Đã xóa bot và gửi lệnh xóa dữ liệu." else "Đã xóa bot khỏi danh sách."
    }

    private fun dispatch(botId: String, showBusy: Boolean = true, call: () -> Result<Int>) {
        if (showBusy) setBusy(botId, true)
        call().onFailure {
            if (showBusy) setBusy(botId, false)
            _message.value = it.message ?: "Không gửi được lệnh tới Termux."
        }
    }

    private fun handleCommandResult(result: CommandResult) {
        val botId = result.botId
        if (botId != null) setBusy(botId, false)
        val errorText = listOf(result.internalMessage, result.stderr).filter { it.isNotBlank() }.joinToString("\n")
        if (result.exitCode != 0 && result.operation !in setOf("status", "logs", "env_read")) {
            _message.value = "${result.operation} thất bại (${result.exitCode}): ${errorText.ifBlank { "Không có mô tả" }}"
        }

        when (result.operation) {
            "import" -> {
                _message.value = if (result.exitCode == 0) result.stdout.trim().ifBlank { "Import hoàn tất." } else "Import thất bại."
                botId?.let { repository.find(it)?.also(::refreshStatus) }
            }
            "install" -> {
                botId?.let { _console.value = _console.value + (it to combinedOutput(result)) }
                if (result.exitCode == 0) _message.value = "Cài thư viện hoàn tất."
            }
            "start", "restart" -> {
                botId?.let {
                    val running = result.exitCode == 0 && (result.stdout.contains("RUNNING") || result.operation == "restart")
                    _runtimeStates.value = _runtimeStates.value + (it to BotRuntimeState(running, if (running) "Đang chạy" else "Khởi động lỗi"))
                    repository.find(it)?.also(::refreshLogs)
                }
                if (result.exitCode == 0) _message.value = if (result.operation == "start") "Đã khởi động bot." else "Đã khởi động lại bot."
            }
            "stop" -> {
                botId?.let { _runtimeStates.value = _runtimeStates.value + (it to BotRuntimeState(false, "Đã dừng")) }
                if (result.exitCode == 0) _message.value = "Đã dừng bot."
            }
            "status" -> botId?.let {
                val output = result.stdout.trim()
                val running = output.startsWith("RUNNING")
                val code = if (!running) output.substringAfter(':', "").toIntOrNull() else null
                _runtimeStates.value = _runtimeStates.value + (it to BotRuntimeState(running, if (running) "Đang chạy" else "Đã dừng", code))
            }
            "logs" -> botId?.let { _console.value = _console.value + (it to combinedOutput(result).ifBlank { "Chưa có log." }) }
            "clear_logs" -> botId?.let {
                _console.value = _console.value + (it to "")
                _message.value = "Đã xóa log."
            }
            "env_read" -> botId?.let { _envContent.value = _envContent.value + (it to result.stdout) }
            "env_write" -> if (result.exitCode == 0) _message.value = "Đã lưu file .env."
        }
    }

    private fun combinedOutput(result: CommandResult): String = buildString {
        append(result.stdout.trimEnd())
        if (result.stderr.isNotBlank()) {
            if (isNotEmpty()) append("\n")
            append(result.stderr.trimEnd())
        }
    }

    private fun setBusy(id: String, busy: Boolean) {
        _busyBots.value = if (busy) _busyBots.value + id else _busyBots.value - id
    }

    override fun onCleared() {
        metricsMonitor.stop()
        super.onCleared()
    }
}
