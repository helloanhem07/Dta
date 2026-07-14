package com.tuananh.bothost.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuananh.bothost.MainViewModel
import com.tuananh.bothost.data.BotProject
import com.tuananh.bothost.data.BotRuntimeState
import com.tuananh.bothost.data.RuntimeType
import com.tuananh.bothost.metrics.SystemMetrics
import com.tuananh.bothost.metrics.formatBytes
import com.tuananh.bothost.ui.components.LineChart
import com.tuananh.bothost.ui.components.MetricCard
import kotlinx.coroutines.launch

private enum class MainTab(val title: String) {
    DASHBOARD("Tổng quan"), BOTS("Bot"), SETTINGS("Cài đặt")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotHostApp(viewModel: MainViewModel) {
    val bots by viewModel.bots.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val states by viewModel.runtimeStates.collectAsStateWithLifecycle()
    val console by viewModel.console.collectAsStateWithLifecycle()
    val env by viewModel.envContent.collectAsStateWithLifecycle()
    val busy by viewModel.busyBots.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(MainTab.DASHBOARD) }
    var selectedBotId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val selectedBot = bots.firstOrNull { it.id == selectedBotId }
    if (selectedBot != null) {
        BotDetailScreen(
            bot = selectedBot,
            state = states[selectedBot.id] ?: BotRuntimeState(),
            console = console[selectedBot.id].orEmpty(),
            envContent = env[selectedBot.id].orEmpty(),
            busy = selectedBot.id in busy,
            onBack = { selectedBotId = null },
            viewModel = viewModel,
            snackbar = snackbar
        )
        return
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(tab.title, fontWeight = FontWeight.Bold) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == MainTab.DASHBOARD,
                    onClick = { tab = MainTab.DASHBOARD },
                    icon = { Icon(Icons.Rounded.Dashboard, null) },
                    label = { Text("Tổng quan") }
                )
                NavigationBarItem(
                    selected = tab == MainTab.BOTS,
                    onClick = { tab = MainTab.BOTS },
                    icon = { Icon(Icons.Rounded.SmartToy, null) },
                    label = { Text("Bot") }
                )
                NavigationBarItem(
                    selected = tab == MainTab.SETTINGS,
                    onClick = { tab = MainTab.SETTINGS },
                    icon = { Icon(Icons.Rounded.Settings, null) },
                    label = { Text("Cài đặt") }
                )
            }
        }
    ) { padding ->
        when (tab) {
            MainTab.DASHBOARD -> DashboardScreen(metrics, bots, states, Modifier.padding(padding))
            MainTab.BOTS -> BotsScreen(
                bots = bots,
                states = states,
                busy = busy,
                modifier = Modifier.padding(padding),
                onOpen = { selectedBotId = it.id },
                onImport = viewModel::importBot,
                onRefresh = viewModel::refreshStatus
            )
            MainTab.SETTINGS -> SettingsScreen(viewModel.termuxInstalled, Modifier.padding(padding))
        }
    }
}

@Composable
private fun DashboardScreen(
    metrics: SystemMetrics,
    bots: List<BotProject>,
    states: Map<String, BotRuntimeState>,
    modifier: Modifier = Modifier
) {
    val running = bots.count { states[it.id]?.running == true }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Thiết bị host", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Số liệu được đo khi ứng dụng đang mở.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("CPU", "%.0f%%".format(metrics.cpuPercent), "Mức sử dụng hiện tại", Modifier.weight(1f))
                MetricCard(
                    "RAM",
                    "%.0f%%".format(metrics.ramPercent),
                    "${formatBytes(metrics.ramUsedBytes)} / ${formatBytes(metrics.ramTotalBytes)}",
                    Modifier.weight(1f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    "Bộ nhớ",
                    formatBytes(metrics.storageUsedBytes),
                    "Tổng ${formatBytes(metrics.storageTotalBytes)}",
                    Modifier.weight(1f)
                )
                MetricCard("Ping", if (metrics.pingMs >= 0) "${metrics.pingMs} ms" else "--", "Google connectivity", Modifier.weight(1f))
            }
        }
        item { MetricCard("Bot online", "$running / ${bots.size}", "Theo PID watchdog trong Termux") }
        item { LineChart("CPU 2 phút gần nhất", metrics.history.map { it.cpu }, 100f, "%") }
        item { LineChart("RAM 2 phút gần nhất", metrics.history.map { it.ram }, 100f, "%") }
        item {
            val pingValues = metrics.history.map { if (it.ping >= 0) it.ping.toFloat() else 0f }
            LineChart("Ping 2 phút gần nhất", pingValues, (pingValues.maxOrNull() ?: 100f).coerceAtLeast(100f), " ms")
        }
    }
}

@Composable
private fun BotsScreen(
    bots: List<BotProject>,
    states: Map<String, BotRuntimeState>,
    busy: Set<String>,
    modifier: Modifier = Modifier,
    onOpen: (BotProject) -> Unit,
    onImport: (Uri, String, RuntimeType, String, String, String, Boolean) -> Unit,
    onRefresh: (BotProject) -> Unit
) {
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingUri = uri
    }

    Box(modifier.fillMaxSize()) {
        if (bots.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Rounded.Folder, null, modifier = Modifier.height(54.dp))
                Text("Chưa có bot", style = MaterialTheme.typography.headlineSmall)
                Text("Nhấn Upload ZIP để thêm project Node.js hoặc Python.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(bots, key = { it.id }) { bot ->
                    BotCard(bot, states[bot.id] ?: BotRuntimeState(), bot.id in busy, onOpen, onRefresh)
                }
            }
        }
        ExtendedFloatingActionButton(
            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
            onClick = { launcher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Upload ZIP") }
        )
    }

    pendingUri?.let { uri ->
        AddBotDialog(
            onDismiss = { pendingUri = null },
            onConfirm = { name, runtime, entry, install, start, autoRestart ->
                onImport(uri, name, runtime, entry, install, start, autoRestart)
                pendingUri = null
            }
        )
    }
}

@Composable
private fun BotCard(
    bot: BotProject,
    state: BotRuntimeState,
    busy: Boolean,
    onOpen: (BotProject) -> Unit,
    onRefresh: (BotProject) -> Unit
) {
    Card(
        onClick = { onOpen(bot) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(bot.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${bot.runtime.label} · ${bot.id}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (busy) CircularProgressIndicator(modifier = Modifier.height(28.dp))
            }
            Text(
                if (state.running) "● Đang chạy" else "○ ${state.statusText}",
                color = if (state.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onRefresh(bot) }) {
                    Icon(Icons.Rounded.Refresh, null)
                    Text(" Kiểm tra")
                }
                Button(onClick = { onOpen(bot) }) { Text("Quản lý") }
            }
        }
    }
}

@Composable
private fun AddBotDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, RuntimeType, String, String, String, Boolean) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var runtime by rememberSaveable { mutableStateOf(RuntimeType.NODE_JS) }
    var entry by rememberSaveable { mutableStateOf("index.js") }
    var install by rememberSaveable { mutableStateOf("npm install") }
    var start by rememberSaveable { mutableStateOf("node index.js") }
    var autoRestart by rememberSaveable { mutableStateOf(true) }

    fun applyRuntime(value: RuntimeType) {
        runtime = value
        when (value) {
            RuntimeType.NODE_JS -> { entry = "index.js"; install = "npm install"; start = "node index.js" }
            RuntimeType.PYTHON -> { entry = "bot.py"; install = "python -m pip install -r requirements.txt"; start = "python bot.py" }
            RuntimeType.CUSTOM -> { entry = ""; install = ""; start = "" }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thêm bot từ ZIP") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Tên bot") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Runtime", style = MaterialTheme.typography.labelLarge)
                RuntimeType.entries.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().selectable(item == runtime) { applyRuntime(item) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(selected = item == runtime, onClick = { applyRuntime(item) })
                        Text(item.label)
                    }
                }
                OutlinedTextField(entry, { entry = it }, label = { Text("File chạy chính") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(install, { install = it }, label = { Text("Lệnh cài thư viện") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(start, { start = it }, label = { Text("Lệnh khởi động") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tự chạy lại khi crash", Modifier.weight(1f))
                    Switch(autoRestart, { autoRestart = it })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && start.isNotBlank(),
                onClick = { onConfirm(name, runtime, entry, install, start, autoRestart) }
            ) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BotDetailScreen(
    bot: BotProject,
    state: BotRuntimeState,
    console: String,
    envContent: String,
    busy: Boolean,
    onBack: () -> Unit,
    viewModel: MainViewModel,
    snackbar: SnackbarHostState
) {
    var detailTab by rememberSaveable(bot.id) { mutableIntStateOf(0) }
    var envText by remember(bot.id, envContent) { mutableStateOf(envContent) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(bot.id) {
        viewModel.refreshStatus(bot)
        viewModel.refreshLogs(bot)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(bot.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Quay lại") } },
                actions = {
                    IconButton(onClick = { viewModel.refreshStatus(bot); viewModel.refreshLogs(bot) }) {
                        Icon(Icons.Rounded.Refresh, "Làm mới")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (state.running) "● Đang chạy" else "○ ${state.statusText}", fontWeight = FontWeight.Bold)
                        Text(bot.workDir, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (busy) CircularProgressIndicator(modifier = Modifier.height(28.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Button(onClick = { viewModel.start(bot) }, enabled = !busy && !state.running) {
                        Icon(Icons.Rounded.PlayArrow, null); Text("Start")
                    }
                    OutlinedButton(onClick = { viewModel.stop(bot) }, enabled = !busy && state.running) {
                        Icon(Icons.Rounded.Stop, null); Text("Stop")
                    }
                    OutlinedButton(onClick = { viewModel.restart(bot) }, enabled = !busy) { Text("Restart") }
                }
                OutlinedButton(onClick = { viewModel.install(bot) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Terminal, null); Text(" Cài/cập nhật thư viện")
                }
            }
            TabRow(selectedTabIndex = detailTab) {
                listOf("Console", ".env", "Cấu hình").forEachIndexed { index, title ->
                    Tab(selected = detailTab == index, onClick = { detailTab = index }, text = { Text(title) })
                }
            }
            when (detailTab) {
                0 -> ConsolePanel(bot, console, viewModel)
                1 -> EnvPanel(bot, envText, { envText = it }, viewModel)
                2 -> ConfigPanel(bot, viewModel, onDelete = { showDelete = true })
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Xóa ${bot.name}?") },
            text = { Text("Bạn có thể chỉ xóa khỏi danh sách hoặc xóa luôn thư mục project trong Termux.") },
            confirmButton = {
                Button(onClick = { viewModel.deleteBot(bot, true); showDelete = false; onBack() }) { Text("Xóa cả dữ liệu") }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = { viewModel.deleteBot(bot, false); showDelete = false; onBack() }) { Text("Chỉ xóa danh sách") }
                    TextButton(onClick = { showDelete = false }) { Text("Hủy") }
                }
            }
        )
    }
}

@Composable
private fun ConsolePanel(bot: BotProject, console: String, viewModel: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.refreshLogs(bot) }) { Icon(Icons.Rounded.Refresh, null); Text(" Làm mới") }
            OutlinedButton(onClick = { viewModel.clearLogs(bot) }) { Text("Xóa log") }
        }
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            SelectionContainer {
                Text(
                    text = console.ifBlank { "Chưa có log." },
                    modifier = Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun EnvPanel(bot: BotProject, content: String, onChange: (String) -> Unit, viewModel: MainViewModel) {
    LaunchedEffect(bot.id) { viewModel.readEnv(bot) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Token và API key được lưu trong Termux. Không chia sẻ ảnh chụp màn hình file này.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = content,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = { Text(".env") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        )
        Button(onClick = { viewModel.writeEnv(bot, content) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Save, null); Text(" Lưu .env")
        }
    }
}

@Composable
private fun ConfigPanel(bot: BotProject, viewModel: MainViewModel, onDelete: () -> Unit) {
    var name by remember(bot.id) { mutableStateOf(bot.name) }
    var install by remember(bot.id) { mutableStateOf(bot.installCommand) }
    var start by remember(bot.id) { mutableStateOf(bot.startCommand) }
    var autoRestart by remember(bot.id) { mutableStateOf(bot.autoRestart) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(name, { name = it }, label = { Text("Tên bot") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(install, { install = it }, label = { Text("Lệnh cài") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(start, { start = it }, label = { Text("Lệnh chạy") }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tự chạy lại khi crash", Modifier.weight(1f))
            Switch(autoRestart, { autoRestart = it })
        }
        Button(
            onClick = { viewModel.updateBot(bot.copy(name = name, installCommand = install, startCommand = start, autoRestart = autoRestart)) },
            modifier = Modifier.fillMaxWidth()
        ) { Icon(Icons.Rounded.Save, null); Text(" Lưu cấu hình") }
        HorizontalDivider()
        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Delete, null); Text(" Xóa bot")
        }
    }
}

@Composable
private fun SettingsScreen(termuxInstalled: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val setup = """
        Chạy trong Termux một lần:

        mkdir -p ~/.termux
        echo 'allow-external-apps=true' >> ~/.termux/termux.properties
        termux-reload-settings
        termux-setup-storage
        pkg update -y
        pkg install -y nodejs python git unzip

        Sau đó vào Cài đặt Android → Ứng dụng → BotHost Android → Quyền bổ sung và bật “Run commands in Termux environment”.
    """.trimIndent()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            MetricCard("Termux", if (termuxInstalled) "Đã phát hiện" else "Chưa phát hiện", "Nên dùng bản F-Droid hoặc GitHub chính thức")
        }
        item {
            Text("Thiết lập lần đầu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                SelectionContainer {
                    Text(setup, Modifier.padding(16.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Button(
                onClick = {
                    context.packageManager.getLaunchIntentForPackage("com.termux")?.let { context.startActivity(it) }
                },
                enabled = termuxInstalled,
                modifier = Modifier.fillMaxWidth()
            ) { Icon(Icons.Rounded.Terminal, null); Text(" Mở Termux") }
        }
        item {
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Icon(Icons.Rounded.Settings, null); Text(" Mở quyền ứng dụng") }
        }
        item {
            Text("Lưu ý", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Bot thực sự chạy trong Termux. BotHost chỉ gửi lệnh, hiển thị trạng thái và quản lý cấu hình. Android có thể dừng Termux khi bật tiết kiệm pin, vì vậy nên tắt tối ưu pin cho cả hai ứng dụng.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
