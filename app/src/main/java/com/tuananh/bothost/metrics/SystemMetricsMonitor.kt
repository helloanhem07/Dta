package com.tuananh.bothost.metrics

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class MetricPoint(
    val timestamp: Long,
    val cpu: Float,
    val ram: Float,
    val ping: Int
)

data class SystemMetrics(
    val cpuPercent: Float = 0f,
    val ramPercent: Float = 0f,
    val ramUsedBytes: Long = 0,
    val ramTotalBytes: Long = 0,
    val storageUsedBytes: Long = 0,
    val storageTotalBytes: Long = 0,
    val pingMs: Int = -1,
    val history: List<MetricPoint> = emptyList()
)

class SystemMetricsMonitor(private val context: Context) {
    private val _metrics = MutableStateFlow(SystemMetrics())
    val metrics: StateFlow<SystemMetrics> = _metrics
    private var job: Job? = null
    private var previousCpu: CpuSnapshot? = null
    private var lastPing = -1
    private var pingCounter = 0

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val cpu = readCpuPercent()
                val ram = readRam()
                val storage = readStorage()
                if (pingCounter++ % 3 == 0) lastPing = measurePing()
                val point = MetricPoint(System.currentTimeMillis(), cpu, ram.third, lastPing)
                val history = (_metrics.value.history + point).takeLast(60)
                _metrics.value = SystemMetrics(
                    cpuPercent = cpu,
                    ramPercent = ram.third,
                    ramUsedBytes = ram.first,
                    ramTotalBytes = ram.second,
                    storageUsedBytes = storage.first,
                    storageTotalBytes = storage.second,
                    pingMs = lastPing,
                    history = history
                )
                delay(2_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun readRam(): Triple<Long, Long, Float> {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        val used = info.totalMem - info.availMem
        val percent = if (info.totalMem > 0) used * 100f / info.totalMem else 0f
        return Triple(used, info.totalMem, percent.coerceIn(0f, 100f))
    }

    private fun readStorage(): Pair<Long, Long> {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        return (total - free) to total
    }

    private fun readCpuPercent(): Float {
        val current = runCatching {
            val parts = File("/proc/stat").useLines { lines ->
                lines.first().trim().split(Regex("\\s+")).drop(1).map { it.toLong() }
            }
            val idle = parts.getOrElse(3) { 0L } + parts.getOrElse(4) { 0L }
            val total = parts.sum()
            CpuSnapshot(idle, total)
        }.getOrNull() ?: return 0f

        val previous = previousCpu
        previousCpu = current
        if (previous == null) return 0f
        val totalDelta = current.total - previous.total
        val idleDelta = current.idle - previous.idle
        if (totalDelta <= 0) return 0f
        return ((totalDelta - idleDelta) * 100f / totalDelta).coerceIn(0f, 100f)
    }

    private suspend fun measurePing(): Int = withContext(Dispatchers.IO) {
        runCatching {
            val start = System.nanoTime()
            val connection = URL("https://connectivitycheck.gstatic.com/generate_204")
                .openConnection() as HttpURLConnection
            connection.connectTimeout = 3_000
            connection.readTimeout = 3_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connect()
            connection.responseCode
            connection.disconnect()
            ((System.nanoTime() - start) / 1_000_000.0).roundToInt()
        }.getOrDefault(-1)
    }

    private data class CpuSnapshot(val idle: Long, val total: Long)
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index <= 1) "${value.roundToInt()} ${units[index]}" else "%.1f %s".format(value, units[index])
}
