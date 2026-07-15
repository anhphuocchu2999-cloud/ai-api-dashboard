package com.java.myapplication.adapter

import android.content.Context
import com.java.myapplication.DashboardApplication
import com.java.myapplication.config.InstanceKeyResolver
import com.java.myapplication.config.ModelInstanceRepository
import com.java.myapplication.config.ServiceType

/**
 * Adapter 工厂。
 *
 * Adapter 的正式路由依据是 ModelInstance.serviceType；尚未同步的新地址使用域名规则回退。
 * 所有已识别 Adapter 均由 NetworkAwareAdapter 包装。
 */
object AdapterFactory {

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
            apiBase.contains("xiaomimimo.com", ignoreCase = true) -> ServiceType.MIMO
            apiBase.contains("coolyeah.net", ignoreCase = true) -> ServiceType.NEW_API
            apiBase.contains("api.deepseek.com", ignoreCase = true) -> ServiceType.DEEPSEEK_OFFICIAL
            apiBase.contains("aihuangniu.com", ignoreCase = true) -> ServiceType.AIHUANGNIU
            platformName.equals("MiMo", ignoreCase = true) -> ServiceType.MIMO
            platformName.equals("Kimi", ignoreCase = true) -> ServiceType.NEW_API
            else -> ServiceType.UNKNOWN
        }
    }

    private fun normalizeApiBase(apiBase: String): String {
        return apiBase.trim().trimEnd('/').lowercase()
    }
}
