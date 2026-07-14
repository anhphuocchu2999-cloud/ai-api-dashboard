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
        /**
         * 宽松解析：未知字符串回退到 UNKNOWN（用于运行时适配）
         */
        fun fromString(value: String?): ServiceType {
            return values().find { it.value == value } ?: UNKNOWN
        }

        /**
         * 严格校验：只接受枚举中明确声明的持久化字符串
         * "unknown" 字面值合法；"abc"、"kimi"、"openai" 等非法
         * @return 对应的 ServiceType，或 null 表示非法值
         */
        fun fromStringStrict(value: String?): ServiceType? {
            if (value == null) return null
            return values().find { it.value == value }
        }
    }
}