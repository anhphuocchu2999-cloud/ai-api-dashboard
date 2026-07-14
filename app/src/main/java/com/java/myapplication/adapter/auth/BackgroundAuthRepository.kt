package com.java.myapplication.adapter.auth

import android.content.SharedPreferences
import com.java.myapplication.config.InstanceKeyResolver
import java.util.Locale
import org.json.JSONObject

/**
 * 后台授权凭据的唯一持久化入口。
 *
 * Stage 8A-3：新写入统一使用稳定 instanceId；历史 `${instanceKey}_auth`
 * 键只用于兼容读取。旧 MiMo Cookie / 爱黄牛 Bearer Token 的 JSON 格式不变。
 */
object BackgroundAuthRepository {

    fun load(prefs: SharedPreferences, instanceKey: String): BackgroundAuthConfig {
        val canonicalId = InstanceKeyResolver.canonicalInstanceId(instanceKey)

        readRaw(prefs, canonicalId)?.let { raw ->
            decode(raw)?.let { return it }
        }

        for (legacyAlias in InstanceKeyResolver.legacyAliases(instanceKey)) {
            val raw = readRaw(prefs, legacyAlias) ?: continue
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
     * 用户明确清除授权时，同时移除新 instanceId 键和历史别名键，
     * 防止兼容回退把旧凭据再次恢复出来。
     */
    fun clear(prefs: SharedPreferences, instanceKey: String): Boolean {
        val canonicalId = InstanceKeyResolver.canonicalInstanceId(instanceKey)
        val editor = prefs.edit().remove(storageKey(canonicalId))
        InstanceKeyResolver.legacyAliases(instanceKey).forEach { alias ->
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
