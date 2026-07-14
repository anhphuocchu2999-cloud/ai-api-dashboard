package com.java.myapplication.webauth

import com.java.myapplication.adapter.auth.BackgroundAuthType

/**
 * 描述一个模型实例是否支持 App 内网页登录授权，以及如何检测授权成功。
 *
 * 已验证正式路径：
 * - MiMo Cookie
 * - 爱黄牛 localStorage Bearer Token
 *
 * probeOnly=true 表示只做网页登录后的接口结构摸排；在真实字段与凭据有效性完成
 * 验证前，不得把摸排结果声明为正式数据能力。
 */
data class WebAuthProfile(
    val profileId: String,
    val instanceKey: String,
    val displayName: String,
    val loginUrl: String,
    val cookieDomain: String,
    val authType: BackgroundAuthType,
    val requiredCookieNames: Set<String>,
    /** 可选：用于按 apiBase 匹配平台（如 aihuangniu.com） */
    val apiBaseHostContains: String? = null,
    /** 可选：localStorage 键名（如 auth_token），用于 BEARER_TOKEN 自动提取 */
    val localStorageKey: String? = null,
    /** 只读摸排模式：捕获 endpoint、状态码和 JSON 字段结构，不保存响应值。 */
    val probeOnly: Boolean = false
)
