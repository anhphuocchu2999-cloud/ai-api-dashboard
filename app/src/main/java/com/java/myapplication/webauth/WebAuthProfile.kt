package com.java.myapplication.webauth

import com.java.myapplication.adapter.auth.BackgroundAuthType

/**
 * 描述一个平台是否支持 App 内网页登录授权，以及如何检测授权成功。
 *
 * 已验证正式路径：
 * - MiMo Cookie
 * - DeepSeek Cookie + get_user_summary 验证
 * - 爱黄牛 localStorage Bearer Token
 *
 * sharedAuthKey 是平台级授权键，不属于任何历史槽位。多个槽位使用同一平台时，
 * 共用这一份已验证授权，再由配置层同步兼容副本给仍按槽位读取的 Widget。
 *
 * probeOnly=true 表示只做网页登录后的接口结构摸排；在真实字段与凭据有效性完成
 * 验证前，不得把摸排结果声明为正式数据能力。
 */
data class WebAuthProfile(
    val profileId: String,
    val sharedAuthKey: String,
    val legacyInstanceKeys: Set<String>,
    val displayName: String,
    val loginUrl: String,
    val cookieDomain: String,
    val authType: BackgroundAuthType,
    val requiredCookieNames: Set<String>,
    /** 用于根据用户填写的 API 地址自动匹配平台；API 域名和官网登录域名可不同。 */
    val apiBaseHostPatterns: Set<String>,
    /** 可选：localStorage 键名（如 auth_token），用于 BEARER_TOKEN 自动提取。 */
    val localStorageKey: String? = null,
    /** Cookie 名不固定时，用真实账户接口验证当前 Cookie 是否已经登录。 */
    val cookieVerificationUrl: String? = null,
    /** 只读摸排模式：捕获 endpoint、状态码和 JSON 字段结构，不保存响应值。 */
    val probeOnly: Boolean = false
) {
    /** 兼容旧调用方；新代码统一使用 sharedAuthKey。 */
    val instanceKey: String
        get() = sharedAuthKey
}
