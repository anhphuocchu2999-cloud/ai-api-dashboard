package com.java.myapplication.config

/**
 * API 账户配置模型
 * 一个配置代表一个 API 账户
 */
data class ApiAccountConfig(
    val id: String,           // 唯一标识（UUID 或旧平台名）
    val name: String,         // 用户备注（如"个人Kimi中转"）
    val apiBase: String,      // API Base URL
    val apiKey: String,       // API Key
    val model: String,        // 模型名称（如"kimi-k2.6"）
    val enabled: Boolean      // 是否启用
)