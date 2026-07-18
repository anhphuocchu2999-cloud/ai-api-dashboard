package com.java.myapplication.adapter

import android.content.Context
import com.java.myapplication.DashboardApplication
import com.java.myapplication.config.InstanceKeyResolver
import com.java.myapplication.config.ModelInstanceRepository
import com.java.myapplication.config.ServiceType
import com.java.myapplication.config.ServiceHostMatcher
import com.java.myapplication.discovery.DashboardRecipeRepository

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

    fun getAdapter(
        platformName: String,
        apiBase: String,
        instanceId: String = platformName
    ): PlatformAdapter? {
        getDashboardRecipeAdapter(instanceId)?.let { return it }
        val persistedType = resolvePersistedServiceType(platformName, apiBase)
        val serviceType = persistedType ?: inferLegacyServiceType(platformName, apiBase)
        return getAdapterByServiceType(serviceType)
    }

    private fun getDashboardRecipeAdapter(instanceId: String): PlatformAdapter? {
        val context = DashboardApplication.appContextOrNull() ?: return null
        val recipe = DashboardRecipeRepository(context).loadRecipeMetadata() ?: return null
        val canonicalInstance = InstanceKeyResolver.canonicalInstanceId(instanceId)
        if (recipe.boundInstanceId != canonicalInstance) return null
        return NetworkAwareAdapter(
            delegate = DashboardRecipeAdapter(context),
            cacheNamespace = DashboardRecipeAdapter.cacheNamespace(recipe)
        )
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
            ServiceHostMatcher.matches(apiBase, "xiaomimimo.com") -> ServiceType.MIMO
            ServiceHostMatcher.matches(apiBase, "coolyeah.net") -> ServiceType.NEW_API
            ServiceHostMatcher.matches(apiBase, "api.deepseek.com") -> ServiceType.DEEPSEEK_OFFICIAL
            ServiceHostMatcher.matches(apiBase, "aihuangniu.com") -> ServiceType.AIHUANGNIU
            platformName.equals("MiMo", ignoreCase = true) -> ServiceType.MIMO
            platformName.equals("Kimi", ignoreCase = true) -> ServiceType.NEW_API
            else -> ServiceType.UNKNOWN
        }
    }

    private fun normalizeApiBase(apiBase: String): String {
        return apiBase.trim().trimEnd('/').lowercase()
    }
}
