package com.java.myapplication.webauth

import com.java.myapplication.adapter.auth.BackgroundAuthType
import com.java.myapplication.config.ServiceHostMatcher

object WebAuthProfileRegistry {
    private val profiles = listOf(
        WebAuthProfile(
            profileId = "mimo",
            sharedAuthKey = "platform-auth-mimo",
            legacyInstanceKeys = setOf("MiMo", "legacy-mimo"),
            displayName = "MiMo",
            loginUrl = "https://platform.xiaomimimo.com/#/console/balance",
            cookieDomain = "platform.xiaomimimo.com",
            authType = BackgroundAuthType.COOKIE,
            requiredCookieNames = setOf(
                "api-platform_serviceToken",
                "userId"
            ),
            apiBaseHostPatterns = setOf(
                "api.xiaomimimo.com",
                "platform.xiaomimimo.com"
            ),
            cookieVerificationUrl = "https://platform.xiaomimimo.com/api/v1/balance"
        ),
        WebAuthProfile(
            profileId = "deepseek",
            sharedAuthKey = "platform-auth-deepseek",
            legacyInstanceKeys = setOf("DeepSeek", "legacy-deepseek"),
            displayName = "DeepSeek",
            loginUrl = "https://platform.deepseek.com/usage",
            cookieDomain = "https://platform.deepseek.com",
            authType = BackgroundAuthType.COOKIE,
            requiredCookieNames = emptySet(),
            apiBaseHostPatterns = setOf(
                "api.deepseek.com",
                "platform.deepseek.com"
            ),
            cookieVerificationUrl =
                "https://platform.deepseek.com/api/v0/users/get_user_summary"
        ),
        WebAuthProfile(
            profileId = "aihuangniu",
            sharedAuthKey = "platform-auth-aihuangniu",
            legacyInstanceKeys = setOf("OpenAI", "Aihuangniu", "legacy-openai"),
            displayName = "爱黄牛",
            loginUrl = "https://sub2.aihuangniu.com",
            cookieDomain = "sub2.aihuangniu.com",
            authType = BackgroundAuthType.BEARER_TOKEN,
            requiredCookieNames = emptySet(),
            apiBaseHostPatterns = setOf("aihuangniu.com"),
            localStorageKey = "auth_token"
        )
    )

    fun findByProfileId(profileId: String): WebAuthProfile? {
        return profiles.firstOrNull { it.profileId.equals(profileId, ignoreCase = true) }
    }

    fun findByInstanceKey(instanceKey: String): WebAuthProfile? {
        return profiles.firstOrNull { profile ->
            profile.sharedAuthKey.equals(instanceKey, ignoreCase = true) ||
                profile.legacyInstanceKeys.any { it.equals(instanceKey, ignoreCase = true) }
        }
    }

    /**
     * 只根据当前 API 地址自动匹配网页登录方案。
     * 四个槽位执行完全相同的规则，历史槽位名称不参与服务识别。
     */
    fun findFor(instanceKey: String, apiBase: String): WebAuthProfile? {
        if (ServiceHostMatcher.hostOf(apiBase) == null) return null

        return profiles.firstOrNull { profile ->
            ServiceHostMatcher.matchesAny(apiBase, profile.apiBaseHostPatterns)
        }
    }
}
