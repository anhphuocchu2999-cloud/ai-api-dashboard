package com.java.myapplication.config

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 配置仓库
 * 唯一配置入口，负责新旧配置兼容。
 *
 * Stage 8A-4：当 ModelInstance 结构有效时，读取结果以 ModelInstanceRepository
 * 为真实来源；旧 Kimi / MiMo / DeepSeek / OpenAI 键仅作为配置页兼容写入来源，
 * 在读取时同步到对应的稳定 instanceId。
 *
 * Stage 8B：固定四个视觉窗口允许切换服务。serviceType 优先根据当前 API Base
 * 推断，只有无法识别时才使用历史槽位默认值，因此窗口名称不再强制决定 Adapter。
 */
object ConfigRepository {

    private const val NEW_CONFIG_KEY = "api_configs"

    private val FIXED_SLOT_BINDINGS = linkedMapOf(
        "Kimi" to "legacy-kimi",
        "MiMo" to "legacy-mimo",
        "DeepSeek" to "legacy-deepseek",
        "OpenAI" to "legacy-openai"
    )

    fun loadAllConfigs(prefs: SharedPreferences): List<ApiAccountConfig> {
        if (ModelInstanceRepository.hasValidInstances(prefs)) {
            val storedInstances = ModelInstanceRepository.loadAllInstances(prefs)
            val synchronizedInstances = synchronizeFixedInstances(
                instances = storedInstances,
                sourceConfigs = loadLegacySourceConfigs(prefs)
            )

            if (synchronizedInstances != storedInstances) {
                ModelInstanceRepository.saveInstances(prefs, synchronizedInstances)
            }

            return instancesToCompatibilityConfigs(synchronizedInstances)
        }

        return loadLegacySourceConfigs(prefs)
    }

    fun getEnabledConfigs(prefs: SharedPreferences): List<ApiAccountConfig> {
        return loadAllConfigs(prefs).filter { it.enabled }
    }

    fun saveConfigs(prefs: SharedPreferences, configs: List<ApiAccountConfig>) {
        val jsonArray = JSONArray()
        for (config in configs) {
            jsonArray.put(configToJson(config))
        }
        prefs.edit().putString(NEW_CONFIG_KEY, jsonArray.toString()).apply()

        if (ModelInstanceRepository.hasValidInstances(prefs)) {
            val storedInstances = ModelInstanceRepository.loadAllInstances(prefs)
            val synchronizedInstances = synchronizeFixedInstances(storedInstances, configs)
            if (synchronizedInstances != storedInstances) {
                ModelInstanceRepository.saveInstances(prefs, synchronizedInstances)
            }
        }
    }

    fun saveConfig(prefs: SharedPreferences, config: ApiAccountConfig) {
        val configs = loadAllConfigs(prefs).toMutableList()
        val index = configs.indexOfFirst { it.id.equals(config.id, ignoreCase = true) }
        if (index >= 0) {
            configs[index] = config
        } else {
            configs.add(config)
        }
        saveConfigs(prefs, configs)
    }

    private fun loadLegacySourceConfigs(prefs: SharedPreferences): List<ApiAccountConfig> {
        val configs = mutableListOf<ApiAccountConfig>()

        val newConfigJson = prefs.getString(NEW_CONFIG_KEY, null)
        if (!newConfigJson.isNullOrBlank()) {
            try {
                val jsonArray = JSONArray(newConfigJson)
                for (i in 0 until jsonArray.length()) {
                    configs.add(parseConfigFromJson(jsonArray.getJSONObject(i)))
                }
            } catch (_: Exception) {
                configs.clear()
            }
        }

        for (slotName in FIXED_SLOT_BINDINGS.keys) {
            val raw = prefs.getString(slotName, null) ?: continue
            val directConfig = parseLegacyConfig(slotName, raw) ?: continue
            val existingIndex = configs.indexOfFirst { it.id.equals(slotName, ignoreCase = true) }
            if (existingIndex >= 0) {
                configs[existingIndex] = directConfig
            } else {
                configs.add(directConfig)
            }
        }

        return configs
    }

    private fun synchronizeFixedInstances(
        instances: List<ModelInstance>,
        sourceConfigs: List<ApiAccountConfig>
    ): List<ModelInstance> {
        if (instances.isEmpty() || sourceConfigs.isEmpty()) return instances

        val sourceBySlot = sourceConfigs
            .filter { config -> FIXED_SLOT_BINDINGS.keys.any { it.equals(config.id, ignoreCase = true) } }
            .associateBy { it.id.lowercase() }

        return instances.map { instance ->
            val slotName = FIXED_SLOT_BINDINGS.entries
                .firstOrNull { it.value == instance.instanceId }
                ?.key
                ?: return@map instance
            val source = sourceBySlot[slotName.lowercase()] ?: return@map instance

            instance.copy(
                displayName = source.name,
                serviceType = inferServiceType(slotName, source.apiBase),
                apiBase = source.apiBase,
                apiKey = source.apiKey,
                modelName = source.model,
                enabled = source.enabled
            )
        }
    }

    private fun instancesToCompatibilityConfigs(instances: List<ModelInstance>): List<ApiAccountConfig> {
        val byId = instances.associateBy { it.instanceId }
        val result = mutableListOf<ApiAccountConfig>()

        for ((slotName, instanceId) in FIXED_SLOT_BINDINGS) {
            val instance = byId[instanceId] ?: continue
            result.add(instance.toApiAccountConfig(slotName))
        }

        instances
            .filter { it.instanceId !in FIXED_SLOT_BINDINGS.values }
            .forEach { instance -> result.add(instance.toApiAccountConfig(instance.instanceId)) }

        return result
    }

    private fun ModelInstance.toApiAccountConfig(compatibilityId: String): ApiAccountConfig {
        return ApiAccountConfig(
            id = compatibilityId,
            name = displayName,
            apiBase = apiBase,
            apiKey = apiKey,
            model = modelName,
            enabled = enabled
        )
    }

    /**
     * API Base 是服务选择的真实来源；历史槽位名只做无法识别时的兼容回退。
     */
    private fun inferServiceType(slotName: String, apiBase: String): ServiceType {
        return when {
            apiBase.contains("coolyeah.net", ignoreCase = true) -> ServiceType.NEW_API
            apiBase.contains("api.deepseek.com", ignoreCase = true) -> ServiceType.DEEPSEEK_OFFICIAL
            apiBase.contains("aihuangniu.com", ignoreCase = true) -> ServiceType.AIHUANGNIU
            apiBase.contains("platform.xiaomimimo.com", ignoreCase = true) -> ServiceType.MIMO
            slotName.equals("Kimi", ignoreCase = true) -> ServiceType.NEW_API
            slotName.equals("MiMo", ignoreCase = true) -> ServiceType.MIMO
            slotName.equals("DeepSeek", ignoreCase = true) -> ServiceType.OPENAI_COMPATIBLE
            slotName.equals("OpenAI", ignoreCase = true) -> ServiceType.OPENAI_COMPATIBLE
            else -> ServiceType.UNKNOWN
        }
    }

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
