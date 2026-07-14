package com.java.myapplication.adapter

/**
 * 平台适配器接口
 * 所有平台适配器必须实现此接口
 */
interface PlatformAdapter {
    /**
     * 平台名称，例如 "Kimi", "OpenAI"
     */
    val platformName: String

    /**
     * 探测平台类型
     * @return 是否匹配该平台
     */
    fun detect(apiBase: String, apiKey: String): Boolean

    /**
     * 获取数据
     * @param apiBase API Base URL
     * @param apiKey API Key 或授权 Token
     * @param modelName 配置的模型名称（可选，用于用量查询等场景）
     * @return WidgetData 统一数据模型
     */
    fun fetchData(apiBase: String, apiKey: String, modelName: String? = null): WidgetData
}