package com.tuananh.bothost.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

class BotRepository(context: Context) {
    private val prefs = context.getSharedPreferences("bothost_projects", Context.MODE_PRIVATE)
    private val _bots = MutableStateFlow(load())
    val bots: StateFlow<List<BotProject>> = _bots

    fun add(bot: BotProject) {
        _bots.value = (_bots.value + bot).sortedByDescending { it.createdAt }
        save()
    }

    fun update(bot: BotProject) {
        _bots.value = _bots.value.map { if (it.id == bot.id) bot else it }
        save()
    }

    fun remove(id: String) {
        _bots.value = _bots.value.filterNot { it.id == id }
        save()
    }

    fun find(id: String): BotProject? = _bots.value.firstOrNull { it.id == id }

    private fun save() {
        val array = JSONArray()
        _bots.value.forEach { bot ->
            array.put(JSONObject().apply {
                put("id", bot.id)
                put("name", bot.name)
                put("runtime", bot.runtime.name)
                put("workDir", bot.workDir)
                put("entryFile", bot.entryFile)
                put("installCommand", bot.installCommand)
                put("startCommand", bot.startCommand)
                put("autoRestart", bot.autoRestart)
                put("createdAt", bot.createdAt)
            })
        }
        prefs.edit().putString("items", array.toString()).apply()
    }

    private fun load(): List<BotProject> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        BotProject(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            runtime = RuntimeType.valueOf(item.optString("runtime", RuntimeType.NODE_JS.name)),
                            workDir = item.getString("workDir"),
                            entryFile = item.optString("entryFile"),
                            installCommand = item.optString("installCommand"),
                            startCommand = item.getString("startCommand"),
                            autoRestart = item.optBoolean("autoRestart", true),
                            createdAt = item.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
