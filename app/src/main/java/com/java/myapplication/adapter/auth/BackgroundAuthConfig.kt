package com.java.myapplication.adapter.auth

/**
 * 后台授权配置模型
 * 与 API Key 完全分离，只用于后台余额/用量接口
 */
data class BackgroundAuthConfig(
    /** 授权类型 */
    val authType: BackgroundAuthType = BackgroundAuthType.NONE,

    /** 授权值（如 auth_token 或 Cookie 字符串） */
    val authValue: String = "",

    /** 是否启用后台授权 */
    val enabled: Boolean = false,

    /** 上次更新时间戳 */
    val updatedAt: Long = 0L
)
