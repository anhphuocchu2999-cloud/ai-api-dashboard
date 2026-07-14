package com.java.myapplication.config

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * 模型实例仓库
 * 负责 ModelInstance 的持久化、加载和旧配置迁移
 *
 * 安全规则：
 * 1. 实例有效性检查：JSON 完整、instanceId 非空且不重复、serviceType 可解析
 * 2. 写入分两步：先写数据，commit 成功后再写 schema version
 * 3. 固定槽位 instanceId 严格映射，额外配置使用稳定哈希生成 ID
 * 4. 旧配置永不删除，作为兼容回滚来源
 */
object ModelInstanceRepository {

    private const val INSTANCES_KEY = "model_instances_v1"
    private const val SCHEMA_VERSION_KEY = "model_instance_schema_version"
    private const val CURRENT_SCHEMA_VERSION = 1

    // 固定槽位映射（大小写不敏感匹配，输出严格固定）
    private val FIXED_INSTANCE_IDS = mapOf(
        "kimi" to "legacy-kimi",
        "mimo" to "legacy-mimo",
        "deepseek" to "legacy-deepseek",
        "openai" to "legacy-openai"
    )

    /**
     * 确保迁移完成
     * 如果新结构不存在或无效，从旧配置迁移
     * @return true 表示已有有效结构或迁移成功，false 表示迁移失败
     */
    fun ensureMigrated(prefs: SharedPreferences): Boolean {
        // 检查是否已有有效的新结构
        if (hasValidInstances(prefs)) {
            return true
        }

        // 从旧配置迁移
        val instances = migrateFromLegacy(prefs)
        return if (instances.isNotEmpty()) {
            saveInstances(prefs, instances)
        } else {
            false
        }
    }

    /**
     * 加载所有实例
     * 注意：本方法不执行完整性检查，直接解析。如需验证请用 hasValidInstances。
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
     * 两步写入：先写实例数据，commit 成功后再写 schema version
     * @return true 表示两步写入全部成功
     */
    fun saveInstances(prefs: SharedPreferences, instances: List<ModelInstance>): Boolean {
        val jsonArray = JSONArray()
        for (instance in instances) {
            jsonArray.put(instanceToJson(instance))
        }

        // 第一步：写入实例数据
        val dataEditor = prefs.edit()
        dataEditor.putString(INSTANCES_KEY, jsonArray.toString())
        val dataCommitted = dataEditor.commit()
        if (!dataCommitted) {
            return false
        }

        // 第二步：写入 schema version（仅在数据写入成功后）
        val schemaEditor = prefs.edit()
        schemaEditor.putInt(SCHEMA_VERSION_KEY, CURRENT_SCHEMA_VERSION)
        val schemaCommitted = schemaEditor.commit()
        return schemaCommitted
    }

    /**
     * 检查是否有有效的实例数据
     * 有效性条件：
     * 1. model_instances_v1 存在且是非空 JSON 数组
     * 2. 每一项都能完整解析
     * 3. 每个 instanceId 都非空
     * 4. instanceId 不能重复
     * 5. serviceType 字段能够解析
     * 6. 数组中不存在解析失败后被静默丢弃的对象
     */
    fun hasValidInstances(prefs: SharedPreferences): Boolean {
        val json = prefs.getString(INSTANCES_KEY, null)
        if (json.isNullOrBlank()) {
            return false
        }

        return try {
            val jsonArray = JSONArray(json)
            if (jsonArray.length() == 0) {
                return false
            }

            val seenIds = mutableSetOf<String>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)

                // 条件 3：instanceId 非空
                val instanceId = obj.optString("instanceId", "")
                if (instanceId.isBlank()) {
                    return false
                }

                // 条件 4：instanceId 不重复
                if (instanceId in seenIds) {
                    return false
                }
                seenIds.add(instanceId)

                // 条件 5：serviceType 可解析（UNKNOWN 是合法显式类型）
                val serviceTypeStr = obj.optString("serviceType", "")
                if (serviceTypeStr.isBlank()) {
                    return false
                }
                // fromString 会返回 UNKNOWN 作为兜底，但如果输入为空/空白则上面已拦截
                ServiceType.fromString(serviceTypeStr)

                // 条件 2：完整解析（所有必要字段存在）
                // 如果解析抛出异常，外层 catch 会返回 false
                parseInstanceFromJson(obj)
            }

            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 从旧配置迁移
     * 固定槽位使用严格映射，额外配置使用稳定哈希生成 ID
     */
    private fun migrateFromLegacy(prefs: SharedPreferences): List<ModelInstance> {
        val legacyConfigs = ConfigRepository.loadAllConfigs(prefs)
        val instances = mutableListOf<ModelInstance>()
        val usedIds = mutableSetOf<String>()

        for ((index, config) in legacyConfigs.withIndex()) {
            val serviceType = inferServiceType(config)
            val instanceId = generateStableInstanceId(config, index, usedIds)

            // 确定性消歧：如果生成的 ID 已被使用，追加索引直到唯一
            val uniqueId = resolveUniqueId(instanceId, usedIds)
            usedIds.add(uniqueId)

            instances.add(
                ModelInstance(
                    instanceId = uniqueId,
                    displayName = config.name,
                    serviceType = serviceType,
                    apiBase = config.apiBase,
                    apiKey = config.apiKey,
                    modelName = config.model,
                    enabled = config.enabled
                )
            )
        }

        return instances
    }

    /**
     * 生成稳定的 instanceId
     * 固定槽位严格映射，额外配置使用稳定哈希
     */
    private fun generateStableInstanceId(config: ApiAccountConfig, index: Int, usedIds: Set<String>): String {
        val normalizedId = config.id.trim()
        val lowerId = normalizedId.lowercase()

        // 固定槽位：严格映射
        FIXED_INSTANCE_IDS[lowerId]?.let { return it }

        // 额外配置：legacy-extra-<安全slug>-<稳定短摘要>
        val slug = safeSlug(config.name.ifBlank { config.id })
        val hashInput = buildString {
            append(config.id)
            append("|")
            append(config.name)
            append("|")
            append(normalizeApiBase(config.apiBase))
            append("|")
            append(config.model)
            append("|")
            append(index)
        }
        val hash = sha256Prefix(hashInput, 12)
        return "legacy-extra-$slug-$hash"
    }

    /**
     * 确定性消歧：确保 instanceId 在列表内唯一
     */
    private fun resolveUniqueId(baseId: String, usedIds: Set<String>): String {
        if (baseId !in usedIds) {
            return baseId
        }
        var counter = 1
        var candidate = "$baseId-$counter"
        while (candidate in usedIds) {
            counter++
            candidate = "$baseId-$counter"
        }
        return candidate
    }

    /**
     * 生成安全 slug：只保留小写字母、数字和短横线
     */
    private fun safeSlug(input: String): String {
        val normalized = input.lowercase()
            .map { char ->
                when {
                    char in 'a'..'z' -> char.toString()
                    char in '0'..'9' -> char.toString()
                    else -> "-"
                }
            }
            .joinToString("")
        // 连续非法字符合并为一个短横线
        val collapsed = normalized.replace(Regex("-+-"), "-")
        // 首尾短横线移除
        val trimmed = collapsed.trim('-')
        return trimmed.ifBlank { "unnamed" }
    }

    /**
     * 规范化 API Base URL（用于稳定哈希输入）
     */
    private fun normalizeApiBase(apiBase: String): String {
        return apiBase.trim().lowercase().removeSuffix("/")
    }

    /**
     * SHA-256 前 n 位十六进制摘要
     */
    private fun sha256Prefix(input: String, length: Int): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(length)
    }

    /**
     * 根据旧配置推断服务类型
     * 固定槽位按 id 推断，额外配置按 apiBase 域名推断
     */
    private fun inferServiceType(config: ApiAccountConfig): ServiceType {
        return when {
            config.id.equals("Kimi", ignoreCase = true) -> ServiceType.NEW_API
            config.id.equals("MiMo", ignoreCase = true) -> ServiceType.MIMO
            config.id.equals("DeepSeek", ignoreCase = true) -> {
                if (config.apiBase.contains("api.deepseek.com", ignoreCase = true)) {
                    ServiceType.DEEPSEEK_OFFICIAL
                } else {
                    ServiceType.OPENAI_COMPATIBLE
                }
            }
            config.id.equals("OpenAI", ignoreCase = true) -> inferServiceTypeByApiBase(config.apiBase)
            else -> inferServiceTypeByApiBase(config.apiBase)
        }
    }

    /**
     * 按 apiBase 域名推断服务类型
     */
    private fun inferServiceTypeByApiBase(apiBase: String): ServiceType {
        return when {
            apiBase.contains("coolyeah.net", ignoreCase = true) -> ServiceType.NEW_API
            apiBase.contains("api.deepseek.com", ignoreCase = true) -> ServiceType.DEEPSEEK_OFFICIAL
            apiBase.contains("aihuangniu.com", ignoreCase = true) -> ServiceType.AIHUANGNIU
            apiBase.contains("platform.xiaomimimo.com", ignoreCase = true) -> ServiceType.MIMO
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