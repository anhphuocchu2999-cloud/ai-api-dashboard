package com.java.myapplication.adapter.auth

import android.content.SharedPreferences
import com.java.myapplication.config.InstanceKeyResolver
import com.java.myapplication.config.LocalCredentialCipher
import java.util.Locale
import org.json.JSONObject

/**
 * 后台授权凭据的唯一持久化入口。
 *
 * Stage 8A-3：实例凭据统一使用稳定 instanceId；历史 `${instanceKey}_auth`
 * 键只用于兼容读取。
 *
 * Stage 8E-S：新网页登录凭据按槽位独立保存；平台公共键仅用于一次性兼容迁移。
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
            decode(raw)?.let { decoded ->
                if (containsPlaintextCredential(raw)) save(prefs, canonicalId, decoded)
                return decoded
            }
        }

        val fallbackKeys = buildList {
            addAll(InstanceKeyResolver.legacyAliases(instanceKey))
            addAll(platformCompatibilityAliases[canonicalId].orEmpty())
        }.distinct().filter { it != canonicalId }

        for (fallbackKey in fallbackKeys) {
            val raw = readRaw(prefs, fallbackKey) ?: continue
            val decoded = decode(raw) ?: continue

            // 迁移采用“复制而不删除”：写入失败时仍返回旧凭据，下次继续重试。
            save(prefs, canonicalId, decoded)
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
        val encryptedAuthValue = LocalCredentialCipher.encrypt(config.authValue) ?: return false
        val json = JSONObject()
            .put("authType", config.authType.name)
            .put("authValue", encryptedAuthValue)
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
                    authValue = LocalCredentialCipher.decrypt(obj.optString("authValue", "")).orEmpty(),
                    enabled = authType != BackgroundAuthType.NONE,
                    updatedAt = obj.optLong("updatedAt", 0L)
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun containsPlaintextCredential(raw: String): Boolean = try {
        val stored = JSONObject(raw).optString("authValue", "")
        stored.isNotBlank() && !LocalCredentialCipher.isEncrypted(stored)
    } catch (_: Exception) {
        false
    }

    private fun storageKey(instanceKey: String): String = "${instanceKey}_auth"
}
