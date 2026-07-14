package com.java.myapplication.config

import java.util.Locale

/**
 * Stage 8A-3 的实例存储键解析器。
 *
 * 现有界面和 Provider 仍会暂时传入历史槽位名；持久化层统一把这些别名
 * 归一化为稳定 instanceId。旧键仅用于兼容读取，绝不删除。
 */
object InstanceKeyResolver {

    private val aliasToInstanceId = mapOf(
        "kimi" to "legacy-kimi",
        "mimo" to "legacy-mimo",
        "deepseek" to "legacy-deepseek",
        "openai" to "legacy-openai",
        // AihuangniuAdapter 的内部 platformName；当前由历史 OpenAI 槽位承载。
        "aihuangniu" to "legacy-openai"
    )

    private val legacyAliasesByInstanceId = mapOf(
        "legacy-kimi" to listOf("Kimi"),
        "legacy-mimo" to listOf("MiMo"),
        "legacy-deepseek" to listOf("DeepSeek"),
        "legacy-openai" to listOf("OpenAI", "Aihuangniu")
    )

    fun canonicalInstanceId(instanceKey: String): String {
        val trimmed = instanceKey.trim()
        if (trimmed.isEmpty()) return trimmed
        return aliasToInstanceId[trimmed.lowercase(Locale.ROOT)] ?: trimmed
    }

    fun legacyAliases(instanceKey: String): List<String> {
        val canonical = canonicalInstanceId(instanceKey)
        val aliases = mutableListOf<String>()

        val supplied = instanceKey.trim()
        if (supplied.isNotEmpty() && supplied != canonical) {
            aliases.add(supplied)
        }

        legacyAliasesByInstanceId[canonical].orEmpty().forEach { alias ->
            if (alias !in aliases) aliases.add(alias)
        }

        return aliases
    }
}
