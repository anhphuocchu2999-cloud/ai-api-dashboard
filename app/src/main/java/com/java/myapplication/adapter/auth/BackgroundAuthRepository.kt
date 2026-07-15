package com.java.myapplication.adapter.auth

import android.content.SharedPreferences
import com.java.myapplication.config.InstanceKeyResolver
import java.util.Locale
import org.json.JSONObject

/**
 * 后台授权凭据的唯一持久化入口。
 *
 * Stage 8A-3：实例凭据统一使用稳定 instanceId；历史 `${instanceKey}_auth`
 * 键只用于兼容读取。
 *
 * Stage 8B-R：网页登录凭据改为平台公共键。读取公共键时会兼容旧的固定槽位键，
 * 采用“复制而不删除”迁移；用户明确断开平台账户时才同时清理公共键和兼容旧键。
 */
object BackgroundAuthRepository {

    private val platformCompatibilityAliases = mapOf(
        "platform-auth-mimo" to listOf("MiMo", "legacy-mimo"),
        "platform-auth-deepseek" to listOf("DeepSeek", "legacy-deepseek"),
        "platform-auth-aihuangniu" to listOf("OpenAI", "Aihuangniu", "legacy-openai")
    )

    fun load(prefs: SharedPreferences, instanceKey: String): BackgroundAuthConfig {
        val canonicalId = InstanceKeyResolver.canonicalInstanceId(instanceKey)

        readRaw(prefs, canonicalId)?.let { raw ->
            decode(raw)?.let { return it }
        }

        val fallbackKeys = buildList {
            addAll(InstanceKeyResolver.legacyAliases(instanceKey))
            addAll(platformCompatibilityAliases[canonicalId].orEmpty())
        }.distinct().filter { it != canonicalId }

        for (fallbackKey in fallbackKeys) {
            val raw = readRaw(prefs, fallbackKey) ?: continue
            val decoded = decode(raw) ?: continue

            // 迁移采用“复制而不删除”：写入失败时仍返回旧凭据，下次继续重试。
            prefs.edit().putString(storageKey(canonicalId), raw).commit()
            return decoded
        }

        return BackgroundAuthConfig()
    }

    fun save(
        prefs: SharedPreferences,
        instanceKey: String,
        config: BackgroundAuthConfig
    ): Boolean {
        val canonicalId = InstanceKeyResolver.canonicalInstanceId(instanceKey)
        val json = JSONObject()
            .put("authType", config.authType.name)
            .put("authValue", config.authValue)
            .put("enabled", config.enabled)
            .put("updatedAt", config.updatedAt)
            .toString()

        return prefs.edit().putString(storageKey(canonicalId), json).commit()
    }

    /**
     * 用户明确清除授权时，同时移除目标键、历史实例别名和平台公共键的兼容旧键，
     * 防止兼容回退把已经断开的凭据再次恢复出来。
     */
    fun clear(prefs: SharedPreferences, instanceKey: String): Boolean {
        val canonicalId = InstanceKeyResolver.canonicalInstanceId(instanceKey)
        val editor = prefs.edit().remove(storageKey(canonicalId))

        InstanceKeyResolver.legacyAliases(instanceKey).forEach { alias ->
            editor.remove(storageKey(alias))
        }
        platformCompatibilityAliases[canonicalId].orEmpty().forEach { alias ->
            editor.remove(storageKey(alias))
        }

        return editor.commit()
    }

    private fun readRaw(prefs: SharedPreferences, instanceKey: String): String? {
        return prefs.getString(storageKey(instanceKey), null)?.takeIf { it.isNotBlank() }
    }

    private fun decode(raw: String): BackgroundAuthConfig? {
        return try {
            val obj = JSONObject(raw)
            val enabled = obj.optBoolean("enabled", false)
            if (!enabled) {
                BackgroundAuthConfig()
            } else {
                val authType = try {
                    BackgroundAuthType.valueOf(
                        obj.optString("authType", BackgroundAuthType.NONE.name)
                            .uppercase(Locale.ROOT)
                    )
                } catch (_: Exception) {
                    BackgroundAuthType.NONE
                }

                BackgroundAuthConfig(
                    authType = authType,
                    authValue = obj.optString("authValue", ""),
                    enabled = authType != BackgroundAuthType.NONE,
                    updatedAt = obj.optLong("updatedAt", 0L)
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun storageKey(instanceKey: String): String = "${instanceKey}_auth"
}
