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
 * 断网或默认网络未通过系统验证时，立即读取当前模型实例与当前账户自己的最近成功数据，
 * 避免同一平台的不同配置或不同网页登录账户共用缓存。
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
     * 使用实例、API配置和网页登录账户的 SHA-256 指纹生成本地缓存键。
     *
     * API Key、Cookie和Bearer Token只参与内存中的摘要计算，不会写入缓存键、日志或界面。
     * 任一凭据发生变化都会建立新的缓存空间，避免展示上一个账户的数据。
     */
    private fun cacheIdentity(request: AdapterRequest): String {
        val material = listOf(
            request.instanceId.trim().ifBlank { "unbound-instance" },
            request.apiBase.trim().trimEnd('/').lowercase(),
            request.modelName.orEmpty().trim(),
            request.backgroundAuthType.name,
            request.modelApiKey.trim(),
            request.backgroundCredential.trim()
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
