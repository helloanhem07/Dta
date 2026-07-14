package com.tuananh.bothost.termux

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import com.tuananh.bothost.data.BotProject
import java.util.concurrent.atomic.AtomicInteger

object TermuxContract {
    const val PACKAGE_NAME = "com.termux"
    const val SERVICE_NAME = "com.termux.app.RunCommandService"
    const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_STDIN = "com.termux.RUN_COMMAND_STDIN"
    const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
    const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
    const val EXTRA_COMMAND_DESCRIPTION = "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION"

    const val RESULT_BUNDLE = "result"
    const val RESULT_STDOUT = "stdout"
    const val RESULT_STDERR = "stderr"
    const val RESULT_EXIT_CODE = "exitCode"
    const val RESULT_ERR = "err"
    const val RESULT_ERRMSG = "errmsg"

    const val TERMUX_HOME = "/data/data/com.termux/files/home"
    const val TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash"
}

data class CommandResult(
    val requestId: Int,
    val botId: String?,
    val operation: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val internalError: Int,
    val internalMessage: String
)

object CommandResultBus {
    val results = kotlinx.coroutines.flow.MutableSharedFlow<CommandResult>(extraBufferCapacity = 32)
}

class TermuxBridge(private val context: Context) {
    private val requestIds = AtomicInteger(1000)

    fun isTermuxInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(TermuxContract.PACKAGE_NAME, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun runBash(
        script: String,
        workDir: String = TermuxContract.TERMUX_HOME,
        stdin: String? = null,
        botId: String? = null,
        operation: String = "command",
        label: String = "BotHost command"
    ): Result<Int> = runCatching {
        check(isTermuxInstalled()) { "Chưa cài Termux hoặc đang dùng bản không hỗ trợ RUN_COMMAND." }

        val requestId = requestIds.incrementAndGet()
        val resultIntent = Intent(context, CommandResultService::class.java).apply {
            putExtra(CommandResultService.EXTRA_REQUEST_ID, requestId)
            putExtra(CommandResultService.EXTRA_BOT_ID, botId)
            putExtra(CommandResultService.EXTRA_OPERATION, operation)
        }
        val flags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pendingIntent = PendingIntent.getService(context, requestId, resultIntent, flags)

        val intent = Intent().apply {
            setClassName(TermuxContract.PACKAGE_NAME, TermuxContract.SERVICE_NAME)
            action = TermuxContract.ACTION_RUN_COMMAND
            putExtra(TermuxContract.EXTRA_COMMAND_PATH, TermuxContract.TERMUX_BASH)
            putExtra(TermuxContract.EXTRA_ARGUMENTS, arrayOf("-lc", script))
            putExtra(TermuxContract.EXTRA_WORKDIR, workDir)
            putExtra(TermuxContract.EXTRA_BACKGROUND, true)
            putExtra(TermuxContract.EXTRA_PENDING_INTENT, pendingIntent)
            putExtra(TermuxContract.EXTRA_COMMAND_LABEL, label)
            putExtra(TermuxContract.EXTRA_COMMAND_DESCRIPTION, "Lệnh được gửi bởi BotHost Android")
            stdin?.let { putExtra(TermuxContract.EXTRA_STDIN, it) }
        }
        context.startService(intent)
        requestId
    }

    fun importZip(bot: BotProject, sharedZipPath: String): Result<Int> {
        val destination = shellQuote(bot.workDir)
        val zip = shellQuote(sharedZipPath)
        val temp = shellQuote("${TermuxContract.TERMUX_HOME}/.bothost-import-${bot.id}")
        val script = """
            set -e
            command -v unzip >/dev/null 2>&1 || pkg install -y unzip
            rm -rf $temp $destination
            mkdir -p $temp $destination
            unzip -oq $zip -d $temp
            count=${'$'}(find $temp -mindepth 1 -maxdepth 1 | wc -l | tr -d ' ')
            first=${'$'}(find $temp -mindepth 1 -maxdepth 1 | head -n 1)
            if [ "${'$'}count" = "1" ] && [ -d "${'$'}first" ]; then
              cp -a "${'$'}first"/. $destination/
            else
              cp -a $temp/. $destination/
            fi
            mkdir -p $destination/.bothost
            rm -rf $temp
            rm -f $zip 2>/dev/null || true
            echo "Đã giải nén vào ${bot.workDir}"
        """.trimIndent()
        return runBash(script, botId = bot.id, operation = "import", label = "Import ${bot.name}")
    }

    fun install(bot: BotProject): Result<Int> {
        val cmd = bot.installCommand.trim()
        if (cmd.isEmpty()) return runBash("echo 'Bot không có lệnh cài thư viện.'", botId = bot.id, operation = "install")
        val script = """
            set +e
            mkdir -p .bothost
            bash -lc ${shellQuote(cmd)} > .bothost/install.log 2>&1
            code=${'$'}?
            tail -n 120 .bothost/install.log
            exit ${'$'}code
        """.trimIndent()
        return runBash(script, workDir = bot.workDir, botId = bot.id, operation = "install", label = "Cài thư viện ${bot.name}")
    }

    fun start(bot: BotProject): Result<Int> {
        val commandB64 = Base64.encodeToString(bot.startCommand.toByteArray(), Base64.NO_WRAP)
        val restartFlag = if (bot.autoRestart) "1" else "0"
        val script = """
            set -e
            mkdir -p .bothost
            if [ -f .bothost/watchdog.pid ]; then
              old=${'$'}(cat .bothost/watchdog.pid 2>/dev/null || true)
              if [ -n "${'$'}old" ] && kill -0 "${'$'}old" 2>/dev/null; then
                echo "Bot đang chạy PID ${'$'}old"
                exit 0
              fi
            fi
            printf '%s' '$commandB64' | base64 -d > .bothost/start-command.sh
            chmod 700 .bothost/start-command.sh
            cat > .bothost/runner.sh <<'RUNNER'
            #!/data/data/com.termux/files/usr/bin/bash
            cd "${'$'}(dirname "${'$'}0")/.." || exit 1
            while true; do
              echo "[${'$'}(date '+%Y-%m-%d %H:%M:%S')] Khởi động bot" >> .bothost/bot.log
              bash .bothost/start-command.sh >> .bothost/bot.log 2>&1
              code=${'$'}?
              echo "[${'$'}(date '+%Y-%m-%d %H:%M:%S')] Bot thoát, mã ${'$'}code" >> .bothost/bot.log
              echo "${'$'}code" > .bothost/last-exit-code
              [ "$restartFlag" = "1" ] || break
              sleep 5
            done
            RUNNER
            chmod 700 .bothost/runner.sh
            nohup bash .bothost/runner.sh >/dev/null 2>&1 &
            pid=${'$'}!
            echo "${'$'}pid" > .bothost/watchdog.pid
            sleep 1
            if kill -0 "${'$'}pid" 2>/dev/null; then
              echo "RUNNING:${'$'}pid"
            else
              echo "FAILED"
              exit 1
            fi
        """.trimIndent()
        return runBash(script, workDir = bot.workDir, botId = bot.id, operation = "start", label = "Start ${bot.name}")
    }

    fun stop(bot: BotProject): Result<Int> {
        val script = """
            if [ ! -f .bothost/watchdog.pid ]; then echo STOPPED; exit 0; fi
            pid=${'$'}(cat .bothost/watchdog.pid 2>/dev/null || true)
            if [ -n "${'$'}pid" ] && kill -0 "${'$'}pid" 2>/dev/null; then
              pkill -TERM -P "${'$'}pid" 2>/dev/null || true
              kill -TERM "${'$'}pid" 2>/dev/null || true
              sleep 1
              pkill -KILL -P "${'$'}pid" 2>/dev/null || true
              kill -KILL "${'$'}pid" 2>/dev/null || true
            fi
            rm -f .bothost/watchdog.pid
            echo STOPPED
        """.trimIndent()
        return runBash(script, workDir = bot.workDir, botId = bot.id, operation = "stop", label = "Stop ${bot.name}")
    }

    fun restart(bot: BotProject): Result<Int> {
        val commandB64 = Base64.encodeToString(bot.startCommand.toByteArray(), Base64.NO_WRAP)
        val restartFlag = if (bot.autoRestart) "1" else "0"
        val script = """
            if [ -f .bothost/watchdog.pid ]; then
              pid=${'$'}(cat .bothost/watchdog.pid 2>/dev/null || true)
              pkill -TERM -P "${'$'}pid" 2>/dev/null || true
              kill -TERM "${'$'}pid" 2>/dev/null || true
              sleep 1
            fi
            rm -f .bothost/watchdog.pid
            mkdir -p .bothost
            printf '%s' '$commandB64' | base64 -d > .bothost/start-command.sh
            chmod 700 .bothost/start-command.sh
            cat > .bothost/runner.sh <<'RUNNER'
            #!/data/data/com.termux/files/usr/bin/bash
            cd "${'$'}(dirname "${'$'}0")/.." || exit 1
            while true; do
              echo "[${'$'}(date '+%Y-%m-%d %H:%M:%S')] Khởi động bot" >> .bothost/bot.log
              bash .bothost/start-command.sh >> .bothost/bot.log 2>&1
              code=${'$'}?
              echo "[${'$'}(date '+%Y-%m-%d %H:%M:%S')] Bot thoát, mã ${'$'}code" >> .bothost/bot.log
              echo "${'$'}code" > .bothost/last-exit-code
              [ "$restartFlag" = "1" ] || break
              sleep 5
            done
            RUNNER
            chmod 700 .bothost/runner.sh
            nohup bash .bothost/runner.sh >/dev/null 2>&1 &
            echo "${'$'}!" > .bothost/watchdog.pid
            echo RESTARTED
        """.trimIndent()
        return runBash(script, workDir = bot.workDir, botId = bot.id, operation = "restart", label = "Restart ${bot.name}")
    }

    fun status(bot: BotProject): Result<Int> {
        val script = """
            if [ -f .bothost/watchdog.pid ]; then
              pid=${'$'}(cat .bothost/watchdog.pid 2>/dev/null || true)
              if [ -n "${'$'}pid" ] && kill -0 "${'$'}pid" 2>/dev/null; then
                echo "RUNNING:${'$'}pid"
                exit 0
              fi
            fi
            code=${'$'}(cat .bothost/last-exit-code 2>/dev/null || echo '')
            echo "STOPPED:${'$'}code"
        """.trimIndent()
        return runBash(script, workDir = bot.workDir, botId = bot.id, operation = "status", label = "Status ${bot.name}")
    }

    fun logs(bot: BotProject): Result<Int> = runBash(
        "tail -n 250 .bothost/bot.log 2>/dev/null || echo 'Chưa có log.'",
        workDir = bot.workDir,
        botId = bot.id,
        operation = "logs",
        label = "Logs ${bot.name}"
    )

    fun clearLogs(bot: BotProject): Result<Int> = runBash(
        ": > .bothost/bot.log; echo 'Đã xóa log.'",
        workDir = bot.workDir,
        botId = bot.id,
        operation = "clear_logs",
        label = "Clear logs ${bot.name}"
    )

    fun readEnv(bot: BotProject): Result<Int> = runBash(
        "test -f .env && cat .env || true",
        workDir = bot.workDir,
        botId = bot.id,
        operation = "env_read",
        label = "Read env ${bot.name}"
    )

    fun writeEnv(bot: BotProject, content: String): Result<Int> = runBash(
        "cat > .env && chmod 600 .env && echo 'Đã lưu .env'",
        workDir = bot.workDir,
        stdin = content,
        botId = bot.id,
        operation = "env_write",
        label = "Write env ${bot.name}"
    )

    fun removeFiles(bot: BotProject): Result<Int> = runBash(
        "rm -rf ${shellQuote(bot.workDir)} && echo 'Đã xóa thư mục bot.'",
        botId = bot.id,
        operation = "remove_files",
        label = "Remove ${bot.name}"
    )

    companion object {
        fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
    }
}
