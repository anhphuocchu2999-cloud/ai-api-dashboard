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
     *
     * Profile 没有 apiBaseHostContains 时，仅要求 instanceKey 匹配；
     * Profile 配置了 apiBaseHostContains 时，instanceKey 与 apiBase 必须同时匹配。
     */
    fun findFor(instanceKey: String, apiBase: String): WebAuthProfile? {
        return profiles.firstOrNull { profile ->
            if (profile.instanceKey != instanceKey) {
                false
            } else {
                val hostConstraint = profile.apiBaseHostContains
                hostConstraint.isNullOrBlank() ||
                    apiBase.contains(hostConstraint, ignoreCase = true)
            }
        }
    }
}
