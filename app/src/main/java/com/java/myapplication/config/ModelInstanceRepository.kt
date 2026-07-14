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
 * 1. 实例有效性检查：JSON 完整、七个字段必须存在且类型正确、instanceId 非空且不重复、
 *    serviceType 必须是枚举声明的持久化字符串（未知字符串非法）
 * 2. 写入分两步：先写数据，commit 成功后再写 schema version
 * 3. ensureMigrated 区分三种状态：有效+schema正确 / 有效+schema缺失 / 无效
 * 4. 固定槽位 instanceId 严格映射，额外配置使用稳定哈希生成 ID
 * 5. 旧配置永不删除，作为兼容回滚来源
 * 6. OpenAI 历史槽位无法识别域名时回退到 OPENAI_COMPATIBLE（不是 UNKNOWN）
 */
object ModelInstanceRepository {

    private const val INSTANCES_KEY = "model_instances_v1"
    private const val SCHEMA_VERSION_KEY = "model_instance_schema_version"
    private const val CURRENT_SCHEMA_VERSION = 1

    // 必须存在于每个实例 JSON 对象中的字段
    private val REQUIRED_FIELDS = listOf(
        "instanceId", "displayName", "serviceType",
        "apiBase", "apiKey", "modelName", "enabled"
    )

    // 固定槽位映射（大小写不敏感匹配，输出严格固定）
    private val FIXED_INSTANCE_IDS = mapOf(
        "kimi" to "legacy-kimi",
        "mimo" to "legacy-mimo",
        "deepseek" to "legacy-deepseek",
        "openai" to "legacy-openai"
    )

    // 需要特殊回退的固定槽位（OpenAI 无法识别时回退到 OPENAI_COMPATIBLE，不是 UNKNOWN）
    private val FIXED_OPENAI_COMPAT_IDS = setOf("openai")

    /**
     * 确保迁移完成
     *
     * 三种状态：
     * A. 实例数据有效 + schema version 正确 → true
     * B. 实例数据有效 + schema version 缺失或错误 → 只补写 schema version，不重迁移
     * C. 实例数据无效 → 从旧配置迁移
     */
    fun ensureMigrated(prefs: SharedPreferences): Boolean {
        // A. 完全有效（数据 + schema version 都正确）
        if (hasValidInstances(prefs) && hasCurrentSchemaVersion(prefs)) {
            return true
        }

        // B. 数据有效但 schema version 缺失或错误 → 只补写 schema version
        if (hasValidInstances(prefs) && !hasCurrentSchemaVersion(prefs)) {
            return commitSchemaVersionOnly(prefs)
        }

        // C. 数据无效 → 从旧配置迁移
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
        return commitSchemaVersionOnly(prefs)
    }

    /**
     * 检查 schema version 是否为当前版本
     */
    private fun hasCurrentSchemaVersion(prefs: SharedPreferences): Boolean {
        return prefs.getInt(SCHEMA_VERSION_KEY, -1) == CURRENT_SCHEMA_VERSION
    }

    /**
     * 只补写 schema version（不触碰实例数据）
     * @return commit 是否成功
     */
    private fun commitSchemaVersionOnly(prefs: SharedPreferences): Boolean {
        val schemaEditor = prefs.edit()
        schemaEditor.putInt(SCHEMA_VERSION_KEY, CURRENT_SCHEMA_VERSION)
        return schemaEditor.commit()
    }

    /**
     * 检查是否有有效的实例数据
     *
     * 有效性条件（全部必须满足）：
     * 1. model_instances_v1 存在且是非空 JSON 数组
     * 2. 每一项都必须包含全部七个字段（instanceId, displayName, serviceType, apiBase, apiKey, modelName, enabled）
     * 3. 每个 instanceId 都非空
     * 4. instanceId 不能重复
     * 5. serviceType 必须是 ServiceType 中明确声明的持久化字符串（"unknown" 合法，"abc"/"kimi" 等非法）
     * 6. enabled 必须是布尔类型
     * 7. 数组中不存在解析失败后被静默丢弃的对象
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

                // 条件 2：七个字段必须全部存在
                for (field in REQUIRED_FIELDS) {
                    if (!obj.has(field)) {
                        return false
                    }
                }

                // 条件 3：instanceId 非空
                val instanceId = obj.getString("instanceId")
                if (instanceId.isBlank()) {
                    return false
                }

                // 条件 4：instanceId 不重复
                if (instanceId in seenIds) {
                    return false
                }
                seenIds.add(instanceId)

                // 条件 5：serviceType 严格校验（只接受枚举声明的持久化字符串）
                val serviceTypeStr = obj.getString("serviceType")
                if (ServiceType.fromStringStrict(serviceTypeStr) == null) {
                    return false
                }

                // 条件 6：enabled 必须是布尔类型
                obj.getBoolean("enabled")

                // 条件 7：完整解析（类型不匹配会抛异常，外层 catch 返回 false）
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
        val collapsed = normalized.replace(Regex("-+-"), "-")
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
            // OpenAI 槽位和额外配置都按 apiBase 推断，但回退值不同
            config.id.equals("OpenAI", ignoreCase = true) ->
                inferServiceTypeByApiBase(config.apiBase, defaultForOpenAiSlot = ServiceType.OPENAI_COMPATIBLE)
            else ->
                inferServiceTypeByApiBase(config.apiBase, defaultForOpenAiSlot = null)
        }
    }

    /**
     * 按 apiBase 域名推断服务类型
     * @param defaultForOpenAiSlot 非 null 时表示这是 OpenAI 历史槽位，无法识别时回退到此值；
     *                             null 表示额外配置，无法识别时返回 UNKNOWN
     */
    private fun inferServiceTypeByApiBase(
        apiBase: String,
        defaultForOpenAiSlot: ServiceType?
    ): ServiceType {
        return when {
            apiBase.contains("coolyeah.net", ignoreCase = true) -> ServiceType.NEW_API
            apiBase.contains("api.deepseek.com", ignoreCase = true) -> ServiceType.DEEPSEEK_OFFICIAL
            apiBase.contains("aihuangniu.com", ignoreCase = true) -> ServiceType.AIHUANGNIU
            apiBase.contains("platform.xiaomimimo.com", ignoreCase = true) -> ServiceType.MIMO
            defaultForOpenAiSlot != null -> defaultForOpenAiSlot  // OpenAI 槽位回退到 OPENAI_COMPATIBLE
            else -> ServiceType.UNKNOWN  // 额外配置无法识别
        }
    }

    /**
     * 从 JSON 严格解析实例
     * 所有七个字段必须存在且类型正确，否则抛异常
     */
    private fun parseInstanceFromJson(obj: JSONObject): ModelInstance {
        return ModelInstance(
            instanceId = obj.getString("instanceId"),
            displayName = obj.getString("displayName"),
            serviceType = ServiceType.fromString(obj.getString("serviceType")),
            apiBase = obj.getString("apiBase"),
            apiKey = obj.getString("apiKey"),
            modelName = obj.getString("modelName"),
            enabled = obj.getBoolean("enabled")
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