package com.java.myapplication.config

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 配置仓库
 * 唯一配置入口，负责新旧配置兼容。
 *
 * Stage 8A-4：当 ModelInstance 结构有效时，读取结果以 ModelInstanceRepository
 * 为兼容底座；旧 Kimi / MiMo / DeepSeek / OpenAI 键继续作为固定槽位的直接保存来源。
 *
 * Stage 8B：固定四个视觉窗口允许切换服务。serviceType 优先根据当前 API Base
 * 推断，只有无法识别时才使用历史槽位默认值，因此窗口名称不再强制决定 Adapter。
 *
 * 保存规则：
 * 1. api_configs 与四个固定槽位 JSON 使用同一个 SharedPreferences.Editor 同步 commit；
 * 2. 配置提交成功后再同步 ModelInstance；
 * 3. 读取时，刚保存的配置覆盖兼容 ModelInstance，避免旧实例反向覆盖新输入。
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
        val savedConfigs = loadLegacySourceConfigs(prefs)
        if (containsPlaintextApiKey(prefs)) {
            saveConfigs(prefs, savedConfigs)
        }

        if (ModelInstanceRepository.hasValidInstances(prefs)) {
            val storedInstances = ModelInstanceRepository.loadAllInstances(prefs)
            val synchronizedInstances = synchronizeFixedInstances(
                instances = storedInstances,
                sourceConfigs = savedConfigs
            )

            if (synchronizedInstances != storedInstances) {
                ModelInstanceRepository.saveInstances(prefs, synchronizedInstances)
            }

            val compatibilityConfigs = instancesToCompatibilityConfigs(synchronizedInstances)
            return mergeConfigs(
                fallback = compatibilityConfigs,
                preferred = savedConfigs
            )
        }

        return savedConfigs
    }

    fun getEnabledConfigs(prefs: SharedPreferences): List<ApiAccountConfig> {
        return loadAllConfigs(prefs).filter { it.enabled }
    }

    fun saveConfigs(prefs: SharedPreferences, configs: List<ApiAccountConfig>): Boolean {
        val normalizedConfigs = normalizeFixedSlotIds(configs)
        val encryptedApiKeys = normalizedConfigs.map { config ->
            LocalCredentialCipher.encrypt(config.apiKey) ?: return false
        }
        val jsonArray = JSONArray()
        normalizedConfigs.forEachIndexed { index, config ->
            jsonArray.put(configToJson(config, encryptedApiKeys[index]))
        }

        // 关键配置必须同步提交，避免用户点击返回后进程或生命周期切换导致数据丢失。
        val editor = prefs.edit().putString(NEW_CONFIG_KEY, jsonArray.toString())
        normalizedConfigs.forEachIndexed { index, config ->
            canonicalFixedSlotName(config.id)?.let { slotName ->
                editor.putString(
                    slotName,
                    legacyConfigToJson(config.copy(id = slotName), encryptedApiKeys[index])
                )
            }
        }
        val committed = editor.commit()
        if (!committed) return false

        if (ModelInstanceRepository.hasValidInstances(prefs)) {
            val storedInstances = ModelInstanceRepository.loadAllInstances(prefs)
            val synchronizedInstances = synchronizeFixedInstances(storedInstances, normalizedConfigs)
            if (synchronizedInstances != storedInstances) {
                return ModelInstanceRepository.saveInstances(prefs, synchronizedInstances)
            }
        }
        return true
    }

    fun saveConfig(prefs: SharedPreferences, config: ApiAccountConfig): Boolean {
        val canonicalSlot = canonicalFixedSlotName(config.id)
        val normalizedConfig = if (canonicalSlot != null) config.copy(id = canonicalSlot) else config

        val configs = loadAllConfigs(prefs).toMutableList()
        val index = configs.indexOfFirst { it.id.equals(normalizedConfig.id, ignoreCase = true) }
        if (index >= 0) {
            configs[index] = normalizedConfig
        } else {
            configs.add(normalizedConfig)
        }
        return saveConfigs(prefs, configs)
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

        // 固定槽位直接键最后读取并覆盖同 ID 项，保证返回页面后读取到刚保存的草稿。
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

        return normalizeFixedSlotIds(configs)
    }

    private fun containsPlaintextApiKey(prefs: SharedPreferences): Boolean {
        fun plaintext(obj: JSONObject): Boolean {
            val stored = obj.optString("apiKey", "")
            return stored.isNotBlank() && !LocalCredentialCipher.isEncrypted(stored)
        }
        return try {
            val array = JSONArray(prefs.getString(NEW_CONFIG_KEY, "[]") ?: "[]")
            if ((0 until array.length()).any { plaintext(array.getJSONObject(it)) }) return true
            FIXED_SLOT_BINDINGS.keys.any { slot ->
                prefs.getString(slot, null)?.let { plaintext(JSONObject(it)) } == true
            }
        } catch (_: Exception) {
            false
        }
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

    /** API Base 是服务选择的真实来源；历史槽位名只做无法识别时的兼容回退。 */
    private fun inferServiceType(slotName: String, apiBase: String): ServiceType {
        return when {
            ServiceHostMatcher.matches(apiBase, "coolyeah.net") -> ServiceType.NEW_API
            ServiceHostMatcher.matches(apiBase, "api.deepseek.com") -> ServiceType.DEEPSEEK_OFFICIAL
            ServiceHostMatcher.matches(apiBase, "aihuangniu.com") -> ServiceType.AIHUANGNIU
            ServiceHostMatcher.matches(apiBase, "xiaomimimo.com") -> ServiceType.MIMO
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
            apiKey = LocalCredentialCipher.decrypt(obj.optString("apiKey", "")).orEmpty(),
            model = obj.optString("model", ""),
            enabled = obj.optBoolean("enabled", true)
        )
    }

    private fun parseLegacyConfig(legacyKey: String, json: String): ApiAccountConfig? {
        return try {
            val obj = JSONObject(json)
            val name = obj.optString("name", legacyKey)
            ApiAccountConfig(
                id = legacyKey,
                name = name.ifBlank { legacyKey },
                apiBase = obj.optString("apiBase", ""),
                apiKey = LocalCredentialCipher.decrypt(obj.optString("apiKey", "")).orEmpty(),
                model = obj.optString("model", ""),
                enabled = obj.optBoolean("enabled", true)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun configToJson(config: ApiAccountConfig, encryptedApiKey: String): JSONObject {
        return JSONObject().apply {
            put("id", config.id)
            put("name", config.name)
            put("apiBase", config.apiBase)
            put("apiKey", encryptedApiKey)
            put("model", config.model)
            put("enabled", config.enabled)
        }
    }

    private fun legacyConfigToJson(config: ApiAccountConfig, encryptedApiKey: String): String {
        return JSONObject()
            .put("name", config.name)
            .put("apiBase", config.apiBase)
            .put("apiKey", encryptedApiKey)
            .put("model", config.model)
            .put("enabled", config.enabled)
            .toString()
    }

    private fun normalizeFixedSlotIds(configs: List<ApiAccountConfig>): List<ApiAccountConfig> {
        return configs.map { config ->
            canonicalFixedSlotName(config.id)?.let { canonical -> config.copy(id = canonical) } ?: config
        }
    }

    private fun canonicalFixedSlotName(id: String): String? {
        return FIXED_SLOT_BINDINGS.keys.firstOrNull { it.equals(id, ignoreCase = true) }
    }

    private fun mergeConfigs(
        fallback: List<ApiAccountConfig>,
        preferred: List<ApiAccountConfig>
    ): List<ApiAccountConfig> {
        val merged = linkedMapOf<String, ApiAccountConfig>()
        fallback.forEach { config -> merged[config.id.lowercase()] = config }
        preferred.forEach { config -> merged[config.id.lowercase()] = config }
        return merged.values.toList()
    }
}
