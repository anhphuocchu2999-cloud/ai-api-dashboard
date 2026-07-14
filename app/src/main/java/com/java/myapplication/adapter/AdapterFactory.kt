package com.java.myapplication.adapter

import android.content.Context
import com.java.myapplication.DashboardApplication
import com.java.myapplication.config.InstanceKeyResolver
import com.java.myapplication.config.ModelInstanceRepository
import com.java.myapplication.config.ServiceType

/**
 * Adapter 工厂。
 *
 * Stage 8A-4：Adapter 的正式路由依据是 ModelInstance.serviceType。
 * 旧 getAdapter(platformName, apiBase) 保留给尚未迁移的调用方；它会先把历史槽位名
 * 解析为稳定 instanceId，并在当前 apiBase 与实例配置一致时使用持久化 serviceType。
 * 若调用方正在编辑尚未同步的新 apiBase，则回退到旧域名规则，避免配置页行为回归。
 *
 * 所有已识别 Adapter 均由 NetworkAwareAdapter 包装：系统确认断网时立即返回
 * 临时网络错误，让 Widget 直接读取最近成功数据，不再等待逐个平台 HTTP 超时。
 */
object AdapterFactory {

    /**
     * 按稳定服务协议路由 Adapter。
     */
    fun getAdapterByServiceType(serviceType: ServiceType): PlatformAdapter? {
        val adapter = when (serviceType) {
            ServiceType.NEW_API -> NewApiAdapter()
            ServiceType.MIMO -> MiMoAdapter()
            ServiceType.DEEPSEEK_OFFICIAL -> DeepSeekOfficialAdapter()
            ServiceType.AIHUANGNIU -> AihuangniuAdapter()
            ServiceType.OPENAI_COMPATIBLE,
            ServiceType.UNKNOWN -> null
        }

        return adapter?.let(::NetworkAwareAdapter)
    }

    /**
     * 历史兼容入口。
     *
     * @param platformName 当前固定布局槽位别名，例如 Kimi / MiMo / DeepSeek / OpenAI
     * @param apiBase 当前配置的 API Base
     */
    fun getAdapter(platformName: String, apiBase: String): PlatformAdapter? {
        val persistedType = resolvePersistedServiceType(platformName, apiBase)
        val serviceType = persistedType ?: inferLegacyServiceType(platformName, apiBase)
        return getAdapterByServiceType(serviceType)
    }

    private fun resolvePersistedServiceType(
        platformName: String,
        apiBase: String
    ): ServiceType? {
        val context = DashboardApplication.appContextOrNull() ?: return null
        val prefs = context.getSharedPreferences("api_config", Context.MODE_PRIVATE)
        if (!ModelInstanceRepository.hasValidInstances(prefs)) return null

        val instanceId = InstanceKeyResolver.canonicalInstanceId(platformName)
        val instance = ModelInstanceRepository.loadAllInstances(prefs)
            .firstOrNull { it.instanceId == instanceId }
            ?: return null

        return if (normalizeApiBase(instance.apiBase) == normalizeApiBase(apiBase)) {
            instance.serviceType
        } else {
            null
        }
    }

    private fun inferLegacyServiceType(platformName: String, apiBase: String): ServiceType {
        return when {
            platformName.equals("MiMo", ignoreCase = true) -> ServiceType.MIMO
            platformName.equals("Kimi", ignoreCase = true) -> ServiceType.NEW_API
            apiBase.contains("coolyeah.net", ignoreCase = true) -> ServiceType.NEW_API
            apiBase.contains("api.deepseek.com", ignoreCase = true) -> ServiceType.DEEPSEEK_OFFICIAL
            apiBase.contains("aihuangniu.com", ignoreCase = true) -> ServiceType.AIHUANGNIU
            apiBase.contains("platform.xiaomimimo.com", ignoreCase = true) -> ServiceType.MIMO
            else -> ServiceType.UNKNOWN
        }
    }

    private fun normalizeApiBase(apiBase: String): String {
        return apiBase.trim().trimEnd('/').lowercase()
    }
}
