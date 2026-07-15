package com.java.myapplication.adapter

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.java.myapplication.DashboardApplication
import com.java.myapplication.adapter.capability.ProviderCapabilityProfile
import java.security.MessageDigest

/**
 * 在进入具体平台请求前做一次系统网络状态快速判断。
 *
 * 断网或默认网络未通过系统验证时，立即读取当前模型实例自己的最近成功数据，
 * 避免同一平台的不同配置共用缓存。
 *
 * 网络状态服务不可用或权限异常时采用 fail-open，不阻断原 Adapter 请求。
 */
internal class NetworkAwareAdapter(
    private val delegate: PlatformAdapter
) : PlatformAdapter {

    override val platformName: String
        get() = delegate.platformName

    override val capabilityProfile: ProviderCapabilityProfile
        get() = delegate.capabilityProfile

    override fun detect(apiBase: String, apiKey: String): Boolean {
        if (!hasUsableNetwork()) return false
        return delegate.detect(apiBase, apiKey)
    }

    override fun fetchData(request: AdapterRequest): WidgetData {
        val instanceKey = cacheIdentity(request)
        if (!hasUsableNetwork()) {
            return WidgetData.error(platformName, "网络错误", instanceKey)
        }

        return try {
            delegate.fetchData(request).bindCacheKey(instanceKey)
        } catch (_: Exception) {
            WidgetData.error(platformName, "获取失败", instanceKey)
        }
    }

    override fun fetchData(
        apiBase: String,
        apiKey: String,
        modelName: String?
    ): WidgetData {
        val request = AdapterRequest(
            apiBase = apiBase,
            modelApiKey = apiKey,
            modelName = modelName
        )
        return fetchData(request)
    }

    /**
     * 优先使用调用方传入的稳定实例ID。
     *
     * 兼容尚未迁移的调用方时，用 API Base、模型和 API Key 的 SHA-256 指纹生成本地键；
     * 原始 API Key 不会写入缓存键、日志或界面。
     */
    private fun cacheIdentity(request: AdapterRequest): String {
        request.instanceId.trim().takeIf { it.isNotBlank() }?.let { return it }

        val material = listOf(
            request.apiBase.trim().trimEnd('/').lowercase(),
            request.modelName.orEmpty().trim(),
            request.modelApiKey.trim()
        ).joinToString("\u001F")

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return "model-cache-${digest.take(32)}"
    }

    private fun hasUsableNetwork(): Boolean {
        val context = DashboardApplication.appContextOrNull() ?: return true

        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager
                ?: return true
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                ?: return false

            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: SecurityException) {
            true
        } catch (_: Exception) {
            true
        }
    }
}
