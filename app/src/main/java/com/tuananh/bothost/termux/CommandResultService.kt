package com.tuananh.bothost.termux

import android.app.Service
import android.content.Intent
import android.os.IBinder

class CommandResultService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            intent.extras?.classLoader = classLoader
            val bundle = intent.getBundleExtra(TermuxContract.RESULT_BUNDLE)
            bundle?.classLoader = classLoader
            val result = CommandResult(
                requestId = intent.getIntExtra(EXTRA_REQUEST_ID, 0),
                botId = intent.getStringExtra(EXTRA_BOT_ID),
                operation = intent.getStringExtra(EXTRA_OPERATION) ?: "command",
                stdout = bundle?.getString(TermuxContract.RESULT_STDOUT).orEmpty(),
                stderr = bundle?.getString(TermuxContract.RESULT_STDERR).orEmpty(),
                exitCode = bundle?.getInt(TermuxContract.RESULT_EXIT_CODE, -1) ?: -1,
                internalError = bundle?.getInt(TermuxContract.RESULT_ERR, 0) ?: 0,
                internalMessage = bundle?.getString(TermuxContract.RESULT_ERRMSG).orEmpty()
            )
            CommandResultBus.results.tryEmit(result)
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        const val EXTRA_REQUEST_ID = "bothost_request_id"
        const val EXTRA_BOT_ID = "bothost_bot_id"
        const val EXTRA_OPERATION = "bothost_operation"
    }
}
