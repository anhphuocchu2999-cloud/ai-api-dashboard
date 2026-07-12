package com.java.myapplication.webauth

import com.java.myapplication.adapter.auth.BackgroundAuthType

/**
 * 描述一个模型实例是否支持 App 内网页登录授权，以及如何检测授权成功。
 *
 * Stage 7A-3A 只实现已经验证过的 COOKIE 路径；未来其他认证方式必须单独验证后再扩展。
 */
data class WebAuthProfile(
    val profileId: String,
    val instanceKey: String,
    val displayName: String,
    val loginUrl: String,
    val cookieDomain: String,
    val authType: BackgroundAuthType,
    val requiredCookieNames: Set<String>
)
