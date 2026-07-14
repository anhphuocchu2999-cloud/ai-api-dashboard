package com.java.myapplication.config

/**
 * 服务类型枚举
 * 表示 Adapter / 服务协议，不表示模型品牌或卡片标题
 */
enum class ServiceType(val value: String) {
    NEW_API("newapi"),
    MIMO("mimo"),
    DEEPSEEK_OFFICIAL("deepseek-official"),
    AIHUANGNIU("aihuangniu"),
    OPENAI_COMPATIBLE("openai-compatible"),
    UNKNOWN("unknown");

    companion object {
        fun fromString(value: String?): ServiceType {
            return values().find { it.value == value } ?: UNKNOWN
        }
    }
}