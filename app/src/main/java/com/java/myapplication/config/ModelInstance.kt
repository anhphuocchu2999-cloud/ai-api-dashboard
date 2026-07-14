package com.java.myapplication.config

/**
 * 模型实例数据类
 * 代表一个独立的模型实例配置
 */
data class ModelInstance(
    val instanceId: String,      // 稳定唯一标识（如 legacy-kimi）
    val displayName: String,      // 用户可修改的卡片名称
    val serviceType: ServiceType, // 决定 Adapter 类型
    val apiBase: String,          // API Base URL
    val apiKey: String,            // 模型 API Key
    val modelName: String,         // 真实模型名称
    val enabled: Boolean           // 是否启用
)