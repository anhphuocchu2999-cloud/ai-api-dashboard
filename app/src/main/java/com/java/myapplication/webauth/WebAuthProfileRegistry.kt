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
     * 根据当前卡片和 API Base 查找已经验证过的网页登录方案。
     *
     * Stage 8B 修正：卡片不再天生等于某个平台。配置了 host 约束的 Profile
     * 以 API Base 为准，因此任意卡片切换为 MiMo 或爱黄牛后都能开放对应网页登录。
     * 没有 host 约束的旧 Profile 仍按 instanceKey 匹配，保留兼容行为。
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
