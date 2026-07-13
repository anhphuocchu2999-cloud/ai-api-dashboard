package com.java.myapplication.adapter.capability

/**
 * Provider 实际使用的数据获取来源。
 *
 * 注意：Billing 是数据接口来源，不是认证类型。
 */
enum class DataSourceType(val displayName: String) {
    API("API"),
    WEB_AUTH("网页授权"),
    BILLING("Billing")
}
