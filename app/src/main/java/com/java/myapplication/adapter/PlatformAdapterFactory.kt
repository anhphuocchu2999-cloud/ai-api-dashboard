package com.java.myapplication.adapter

/**
 * 平台适配器工厂
 * 负责根据平台类型创建对应的适配器
 */
object PlatformAdapterFactory {

    /**
     * 所有已注册的适配器列表
     */
    private val adapters = listOf<PlatformAdapter>(
        NewApiAdapter(),
        // 后续添加：
        // OpenAIAdapter(),
        // OneApiAdapter(),
        // DeepSeekAdapter(),
        // MiMoAdapter()
    )

    /**
     * 根据 API Base 和 API Key 自动探测平台类型
     * @return 匹配的适配器，如果没有匹配则返回 null
     */
    fun detect(apiBase: String, apiKey: String): PlatformAdapter? {
        for (adapter in adapters) {
            if (adapter.detect(apiBase, apiKey)) {
                return adapter
            }
        }
        return null
    }

    /**
     * 获取指定名称的适配器
     */
    fun getAdapter(platformName: String): PlatformAdapter? {
        return adapters.find { it.platformName == platformName }
    }

    /**
     * 获取所有适配器名称列表
     */
    fun getAllPlatformNames(): List<String> {
        return adapters.map { it.platformName }
    }
}