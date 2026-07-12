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
     * 使用明确区分的凭据输入获取数据。
     *
     * 默认实现仍调用旧方法，供只需要模型 API Key 的 Adapter 兼容使用。
     * 需要 Cookie / Bearer Token 的 Adapter 应覆盖本方法。
     */
    fun fetchData(request: AdapterRequest): WidgetData {
        return fetchData(request.apiBase, request.modelApiKey, request.modelName)
    }

    /**
     * 旧版兼容入口。
     *
     * 新代码不得再把 Cookie / Bearer Token 作为 apiKey 传入。
     */
    fun fetchData(apiBase: String, apiKey: String, modelName: String? = null): WidgetData
}