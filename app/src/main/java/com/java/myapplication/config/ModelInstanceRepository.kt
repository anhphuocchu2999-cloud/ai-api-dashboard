package com.java.myapplication.config

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 模型实例仓库
 * 负责 ModelInstance 的持久化、加载和旧配置迁移
 */
object ModelInstanceRepository {

    private const val INSTANCES_KEY = "model_instances_v1"
    private const val SCHEMA_VERSION_KEY = "model_instance_schema_version"
    private const val CURRENT_SCHEMA_VERSION = 1

    /**
     * 确保迁移完成
     * 如果新结构不存在或无效，从旧配置迁移
     */
    fun ensureMigrated(prefs: SharedPreferences) {
        // 检查是否已有有效的新结构
        if (hasValidInstances(prefs)) {
            return
        }

        // 从旧配置迁移
        val instances = migrateFromLegacy(prefs)
        if (instances.isNotEmpty()) {
            saveInstances(prefs, instances)
        }
    }

    /**
     * 加载所有实例
     */
    fun loadAllInstances(prefs: SharedPreferences): List<ModelInstance> {
        val json = prefs.getString(INSTANCES_KEY, null)
        if (json.isNullOrBlank()) {
            return emptyList()
        }

        return try {
            val jsonArray = JSONArray(json)
            val instances = mutableListOf<ModelInstance>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                instances.add(parseInstanceFromJson(obj))
            }
            instances
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 保存所有实例
     */
    fun saveInstances(prefs: SharedPreferences, instances: List<ModelInstance>) {
        val jsonArray = JSONArray()
        for (instance in instances) {
            jsonArray.put(instanceToJson(instance))
        }

        val editor = prefs.edit()
        editor.putString(INSTANCES_KEY, jsonArray.toString())
        editor.putInt(SCHEMA_VERSION_KEY, CURRENT_SCHEMA_VERSION)
        editor.commit()
    }

    /**
     * 检查是否有有效的实例数据
     */
    private fun hasValidInstances(prefs: SharedPreferences): Boolean {
        val json = prefs.getString(INSTANCES_KEY, null)
        if (json.isNullOrBlank()) {
            return false
        }

        return try {
            val jsonArray = JSONArray(json)
            jsonArray.length() > 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 从旧配置迁移
     */
    private fun migrateFromLegacy(prefs: SharedPreferences): List<ModelInstance> {
        val legacyConfigs = ConfigRepository.loadAllConfigs(prefs)
        return legacyConfigs.map { config ->
            val serviceType = inferServiceType(config)
            ModelInstance(
                instanceId = "legacy-${config.id.lowercase()}",
                displayName = config.name,
                serviceType = serviceType,
                apiBase = config.apiBase,
                apiKey = config.apiKey,
                modelName = config.model,
                enabled = config.enabled
            )
        }
    }

    /**
     * 根据旧配置推断服务类型
     */
    private fun inferServiceType(config: ApiAccountConfig): ServiceType {
        return when {
            config.id == "Kimi" -> ServiceType.NEW_API
            config.id == "MiMo" -> ServiceType.MIMO
            config.id == "DeepSeek" -> {
                if (config.apiBase.contains("api.deepseek.com", ignoreCase = true)) {
                    ServiceType.DEEPSEEK_OFFICIAL
                } else {
                    ServiceType.OPENAI_COMPATIBLE
                }
            }
            config.id == "OpenAI" -> {
                when {
                    config.apiBase.contains("coolyeah.net", ignoreCase = true) -> ServiceType.NEW_API
                    config.apiBase.contains("api.deepseek.com", ignoreCase = true) -> ServiceType.DEEPSEEK_OFFICIAL
                    config.apiBase.contains("aihuangniu.com", ignoreCase = true) -> ServiceType.AIHUANGNIU
                    config.apiBase.contains("platform.xiaomimimo.com", ignoreCase = true) -> ServiceType.MIMO
                    else -> ServiceType.OPENAI_COMPATIBLE
                }
            }
            else -> ServiceType.UNKNOWN
        }
    }

    /**
     * 从 JSON 解析实例
     */
    private fun parseInstanceFromJson(obj: JSONObject): ModelInstance {
        return ModelInstance(
            instanceId = obj.optString("instanceId", ""),
            displayName = obj.optString("displayName", ""),
            serviceType = ServiceType.fromString(obj.optString("serviceType", "unknown")),
            apiBase = obj.optString("apiBase", ""),
            apiKey = obj.optString("apiKey", ""),
            modelName = obj.optString("modelName", ""),
            enabled = obj.optBoolean("enabled", true)
        )
    }

    /**
     * 实例转 JSON
     */
    private fun instanceToJson(instance: ModelInstance): JSONObject {
        return JSONObject().apply {
            put("instanceId", instance.instanceId)
            put("displayName", instance.displayName)
            put("serviceType", instance.serviceType.value)
            put("apiBase", instance.apiBase)
            put("apiKey", instance.apiKey)
            put("modelName", instance.modelName)
            put("enabled", instance.enabled)
        }
    }
}