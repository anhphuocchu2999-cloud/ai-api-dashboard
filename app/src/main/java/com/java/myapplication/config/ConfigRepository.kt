package com.java.myapplication.config

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 配置仓库
 * 唯一配置入口，负责新旧配置兼容
 */
object ConfigRepository {

    private const val NEW_CONFIG_KEY = "api_configs"
    private val LEGACY_KEYS = listOf("Kimi", "MiMo", "DeepSeek", "OpenAI")

    /**
     * 加载所有配置（自动迁移旧数据）
     */
    fun loadAllConfigs(prefs: SharedPreferences): List<ApiAccountConfig> {
        val configs = mutableListOf<ApiAccountConfig>()

        // 1. 先尝试读取新格式配置
        val newConfigJson = prefs.getString(NEW_CONFIG_KEY, null)
        if (!newConfigJson.isNullOrBlank()) {
            try {
                val jsonArray = JSONArray(newConfigJson)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    configs.add(parseConfigFromJson(obj))
                }
            } catch (_: Exception) {
                // 新格式解析失败，继续尝试旧格式
            }
        }

        // 2. 如果新格式为空，尝试迁移旧格式
        if (configs.isEmpty()) {
            for (legacyKey in LEGACY_KEYS) {
                val json = prefs.getString(legacyKey, null)
                if (!json.isNullOrBlank()) {
                    val config = parseLegacyConfig(legacyKey, json)
                    if (config != null) {
                        configs.add(config)
                    }
                }
            }
        }

        return configs
    }

    /**
     * 获取启用的配置
     */
    fun getEnabledConfigs(prefs: SharedPreferences): List<ApiAccountConfig> {
        return loadAllConfigs(prefs).filter { it.enabled }
    }

    /**
     * 保存所有配置（新格式）
     */
    fun saveConfigs(prefs: SharedPreferences, configs: List<ApiAccountConfig>) {
        val jsonArray = JSONArray()
        for (config in configs) {
            jsonArray.put(configToJson(config))
        }
        prefs.edit().putString(NEW_CONFIG_KEY, jsonArray.toString()).apply()
    }

    /**
     * 保存单个配置
     */
    fun saveConfig(prefs: SharedPreferences, config: ApiAccountConfig) {
        val configs = loadAllConfigs(prefs).toMutableList()
        val index = configs.indexOfFirst { it.id == config.id }
        if (index >= 0) {
            configs[index] = config
        } else {
            configs.add(config)
        }
        saveConfigs(prefs, configs)
    }

    /**
     * 解析新格式配置
     */
    private fun parseConfigFromJson(obj: JSONObject): ApiAccountConfig {
        return ApiAccountConfig(
            id = obj.optString("id", ""),
            name = obj.optString("name", ""),
            apiBase = obj.optString("apiBase", ""),
            apiKey = obj.optString("apiKey", ""),
            model = obj.optString("model", ""),
            enabled = obj.optBoolean("enabled", true)
        )
    }

    /**
     * 解析旧格式配置
     */
    private fun parseLegacyConfig(legacyKey: String, json: String): ApiAccountConfig? {
        return try {
            val obj = JSONObject(json)
            val name = obj.optString("name", legacyKey)
            val model = obj.optString("model", "")
            ApiAccountConfig(
                id = legacyKey,
                name = name.ifBlank { legacyKey },
                apiBase = obj.optString("apiBase", ""),
                apiKey = obj.optString("apiKey", ""),
                model = model,
                enabled = obj.optBoolean("enabled", true)
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 配置转 JSON
     */
    private fun configToJson(config: ApiAccountConfig): JSONObject {
        return JSONObject().apply {
            put("id", config.id)
            put("name", config.name)
            put("apiBase", config.apiBase)
            put("apiKey", config.apiKey)
            put("model", config.model)
            put("enabled", config.enabled)
        }
    }
}