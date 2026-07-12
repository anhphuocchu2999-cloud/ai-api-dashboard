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
        )
    )

    fun findByProfileId(profileId: String): WebAuthProfile? {
        return profiles.firstOrNull { it.profileId == profileId }
    }

    fun findByInstanceKey(instanceKey: String): WebAuthProfile? {
        return profiles.firstOrNull { it.instanceKey == instanceKey }
    }
}
