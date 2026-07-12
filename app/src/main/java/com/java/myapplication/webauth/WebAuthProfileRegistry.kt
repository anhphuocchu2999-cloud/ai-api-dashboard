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
            )
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
        return profiles.firstOrNull { it.instanceKey == instanceKey }
    }

    /**
     * 根据平台实例键和 apiBase 查找匹配的网页登录配置。
     * 优先精确匹配 instanceKey，其次按 apiBase 包含匹配。
     */
    fun findFor(instanceKey: String, apiBase: String): WebAuthProfile? {
        // 1. 精确匹配 instanceKey
        val exact = profiles.firstOrNull { it.instanceKey == instanceKey }
        if (exact != null) return exact

        // 2. 按 apiBase 包含匹配（用于 OpenAI 槽位配置爱黄牛等场景）
        return profiles.firstOrNull {
            it.apiBaseHostContains != null &&
            apiBase.contains(it.apiBaseHostContains, ignoreCase = true)
        }
    }
}
