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
 * 在读取时同步到对应的稳定 instanceId。这样现有配置页和固定四卡外观无需改动，
 * Provider 仍通过本仓库即可取得绑定到 legacy-* 实例的数据。
 */
object ConfigRepository {

    private const val NEW_CONFIG_KEY = "api_configs"

    private val FIXED_SLOT_BINDINGS = linkedMapOf(
        "Kimi" to "legacy-kimi",
        "MiMo" to "legacy-mimo",
        "DeepSeek" to "legacy-deepseek",
        "OpenAI" to "legacy-openai"
    )

    /**
     * 加载所有配置。
     *
     * 新实例结构有效时：
     * 1. 读取 ModelInstanceRepository；
     * 2. 吸收旧配置页对固定四槽的最新写入；
     * 3. 以稳定实例为来源返回兼容 ApiAccountConfig。
     *
     * 新实例结构尚不存在时，继续使用原有新旧配置兼容读取，供首次迁移使用。
     */
    fun loadAllConfigs(prefs: SharedPreferences): List<ApiAccountConfig> {
        if (ModelInstanceRepository.hasValidInstances(prefs)) {
            val storedInstances = ModelInstanceRepository.loadAllInstances(prefs)
            val synchronizedInstances = synchronizeFixedInstances(
                instances = storedInstances,
                sourceConfigs = loadLegacySourceConfigs(prefs)
            )

            if (synchronizedInstances != storedInstances) {
                // 同步失败时保留旧实例数据；不得删除任何旧配置。
                ModelInstanceRepository.saveInstances(prefs, synchronizedInstances)
            }

            return instancesToCompatibilityConfigs(synchronizedInstances)
        }

        return loadLegacySourceConfigs(prefs)
    }

    /**
     * 获取启用的配置
     */
    fun getEnabledConfigs(prefs: SharedPreferences): List<ApiAccountConfig> {
        return loadAllConfigs(prefs).filter { it.enabled }
    }

    /**
     * 保存所有配置（兼容格式）。
     *
     * 旧 api_configs 继续保留；若实例结构已经存在，同时把固定四槽的可变配置
     * 同步到对应 legacy-* 实例，instanceId 始终保持不变。
     */
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

    /**
     * 保存单个配置
     */
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

    /**
     * 原配置结构读取。
     *
     * api_configs 先加载；固定四槽的独立 SharedPreferences 键再覆盖同名项，
     * 因为当前配置页仍实时写这些独立键。该方法在首次 ModelInstance 迁移时也使用，
     * 不得调用 ModelInstanceRepository，避免迁移递归。
     */
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

    /**
     * 把旧配置页的固定四槽写入同步到稳定模型实例。
     * 只更新可变配置字段；instanceId 永不改变。
     */
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

    /**
     * 将稳定实例映射为当前固定配置页 / Widget 仍能读取的兼容结构。
     * 固定四槽的 id 仅作为视觉槽位别名；真实归属仍是对应 legacy-* instanceId。
     */
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

    private fun inferServiceType(slotName: String, apiBase: String): ServiceType {
        return when {
            slotName.equals("Kimi", ignoreCase = true) -> ServiceType.NEW_API
            slotName.equals("MiMo", ignoreCase = true) -> ServiceType.MIMO
            slotName.equals("DeepSeek", ignoreCase = true) -> {
                if (apiBase.contains("api.deepseek.com", ignoreCase = true)) {
                    ServiceType.DEEPSEEK_OFFICIAL
                } else {
                    ServiceType.OPENAI_COMPATIBLE
                }
            }
            apiBase.contains("coolyeah.net", ignoreCase = true) -> ServiceType.NEW_API
            apiBase.contains("api.deepseek.com", ignoreCase = true) -> ServiceType.DEEPSEEK_OFFICIAL
            apiBase.contains("aihuangniu.com", ignoreCase = true) -> ServiceType.AIHUANGNIU
            apiBase.contains("platform.xiaomimimo.com", ignoreCase = true) -> ServiceType.MIMO
            slotName.equals("OpenAI", ignoreCase = true) -> ServiceType.OPENAI_COMPATIBLE
            else -> ServiceType.UNKNOWN
        }
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
