package com.java.myapplication.adapter.auth

import android.content.SharedPreferences
import java.util.Locale
import org.json.JSONObject

/**
 * 后台授权凭据的唯一持久化入口。
 *
 * 保持现有 `${instanceKey}_auth` 键名和 JSON 字段兼容，
 * 不改变已保存的 MiMo Cookie / Bearer Token 格式。
 */
object BackgroundAuthRepository {

    fun load(prefs: SharedPreferences, instanceKey: String): BackgroundAuthConfig {
        val raw = prefs.getString(storageKey(instanceKey), null)
        if (raw.isNullOrBlank()) {
            return BackgroundAuthConfig()
        }

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
            BackgroundAuthConfig()
        }
    }

    fun save(
        prefs: SharedPreferences,
        instanceKey: String,
        config: BackgroundAuthConfig
    ) {
        val json = JSONObject()
            .put("authType", config.authType.name)
            .put("authValue", config.authValue)
            .put("enabled", config.enabled)
            .put("updatedAt", config.updatedAt)
            .toString()

        prefs.edit().putString(storageKey(instanceKey), json).apply()
    }

    fun clear(prefs: SharedPreferences, instanceKey: String) {
        prefs.edit().remove(storageKey(instanceKey)).apply()
    }

    private fun storageKey(instanceKey: String): String = "${instanceKey}_auth"
}
