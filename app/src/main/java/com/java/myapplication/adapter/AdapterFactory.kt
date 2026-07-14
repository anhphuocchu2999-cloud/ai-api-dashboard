package com.java.myapplication.adapter

/**
 * Adapter 工厂
 * 根据平台配置返回对应的 PlatformAdapter
 *
 * 路由规则（按优先级）：
 * 1. apiBase 包含 coolyeah.net → NewApiAdapter
 * 2. apiBase 包含 api.deepseek.com → DeepSeekOfficialAdapter
 * 3. apiBase 包含 aihuangniu.com → AihuangniuAdapter
 * 4. apiBase 包含 platform.xiaomimimo.com → MiMoAdapter
 * 5. platformName == "Kimi" → NewApiAdapter
 * 6. platformName == "MiMo" → MiMoAdapter
 * 7. 其它 → null
 */
object AdapterFactory {

    /**
     * 根据平台配置获取对应的 Adapter
     * @param platformName SharedPreferences 中的平台键名，例如 "Kimi"
     * @param apiBase 用户配置的 API Base URL
     * @return PlatformAdapter? 对应的适配器，如果不支持则返回 null
     */
    fun getAdapter(platformName: String, apiBase: String): PlatformAdapter? {
        return when {
            // 优先按平台键名路由，确保固定槽位不受旧 apiBase 影响
            platformName == "MiMo" -> MiMoAdapter()
            platformName == "Kimi" -> NewApiAdapter()
            // 按 apiBase 域名路由
            apiBase.contains("coolyeah.net", ignoreCase = true) -> NewApiAdapter()
            // DeepSeek 官方 API
            apiBase.contains("api.deepseek.com", ignoreCase = true) -> DeepSeekOfficialAdapter()
            // 爱黄牛中转站
            apiBase.contains("aihuangniu.com", ignoreCase = true) -> AihuangniuAdapter()
            // MiMo 平台（按 apiBase 兜底）
            apiBase.contains("platform.xiaomimimo.com", ignoreCase = true) -> MiMoAdapter()
            else -> null
        }
    }
}