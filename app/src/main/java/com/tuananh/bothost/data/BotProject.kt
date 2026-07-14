package com.tuananh.bothost.data

enum class RuntimeType(val label: String) {
    NODE_JS("Node.js"),
    PYTHON("Python"),
    CUSTOM("Tùy chỉnh")
}

data class BotProject(
    val id: String,
    val name: String,
    val runtime: RuntimeType,
    val workDir: String,
    val entryFile: String,
    val installCommand: String,
    val startCommand: String,
    val autoRestart: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class BotRuntimeState(
    val running: Boolean = false,
    val statusText: String = "Chưa kiểm tra",
    val lastExitCode: Int? = null
)
