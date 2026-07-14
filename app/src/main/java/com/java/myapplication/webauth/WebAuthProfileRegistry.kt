package com.java.myapplication.webauth

import com.java.myapplication.adapter.auth.BackgroundAuthType

object WebAuthProfileRegistry {
    private val profiles = listOf(
        WebAuthProfile(
            profileId = "mimo",
            instanceKey = "MiMo",
            displayName = "MiMo",
            loginUrl = "https://platform.xiaomimimo.com/#/console/balance",
            cookieDomain = "platform.xiaomimimo.com",
            authType = BackgroundAuthType.COOKIE,
            requiredCookieNames = setOf(
                "api-platform_serviceToken",
                "userId"
            ),
            apiBaseHostContains = "platform.xiaomimimo.com"
        ),
        WebAuthProfile(
            profileId = "deepseek",
            instanceKey = "DeepSeek",
            displayName = "DeepSeek",
            loginUrl = "https://platform.deepseek.com/usage",
            cookieDomain = "https://platform.deepseek.com",
            authType = BackgroundAuthType.COOKIE,
            requiredCookieNames = emptySet(),
            apiBaseHostContains = "api.deepseek.com",
            cookieVerificationUrl =
                "https://platform.deepseek.com/api/v0/users/get_user_summary"
        ),
        WebAuthProfile(
            profileId = "aihuangniu",
            instanceKey = "OpenAI",
            displayName = "爱黄牛",
            loginUrl = "https://sub2.aihuangniu.com",
            cookieDomain = "sub2.aihuangniu.com",
            authType = BackgroundAuthType.BEARER_TOKEN,
            requiredCookieNames = emptySet(),
            apiBaseHostContains = "aihuangniu.com",
            localStorageKey = "auth_token"
        )
    )

    fun findByProfileId(profileId: String): WebAuthProfile? {
        return profiles.firstOrNull { it.profileId == profileId }
    }

    fun findByInstanceKey(instanceKey: String): WebAuthProfile? {
        return profiles.firstOrNull { it.instanceKey.equals(instanceKey, ignoreCase = true) }
    }

    /**
     * 根据当前卡片和 API Base 查找网页登录方案。
     * 配置 host 约束的 Profile 以 API Base 为准，使任意窗口切换服务后都能获得
     * 对应入口；没有 host 约束的旧 Profile 继续按 instanceKey 兼容匹配。
     */
    fun findFor(instanceKey: String, apiBase: String): WebAuthProfile? {
        return profiles.firstOrNull { profile ->
            val hostConstraint = profile.apiBaseHostContains
            if (!hostConstraint.isNullOrBlank()) {
                apiBase.contains(hostConstraint, ignoreCase = true)
            } else {
                profile.instanceKey.equals(instanceKey, ignoreCase = true)
            }
        }
    }
}
