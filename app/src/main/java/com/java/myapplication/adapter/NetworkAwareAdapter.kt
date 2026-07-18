package com.java.myapplication.adapter

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.java.myapplication.DashboardApplication
import com.java.myapplication.adapter.capability.ProviderCapabilityProfile
import com.java.myapplication.config.InstanceKeyResolver
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
    private val delegate: PlatformAdapter,
    private val cacheNamespace: String = ""
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
     * Cookie / Token 不进入身份，避免会话轮换制造无限缓存键。账户切换成功时由
     * WebAuthActivity 清除当前槽位缓存，槽位本身仍保证不同账户不会串读。
     */
    private fun cacheIdentity(request: AdapterRequest): String {
        val canonicalInstance = InstanceKeyResolver.canonicalInstanceId(
            request.instanceId.trim().ifBlank { "unbound-instance" }
        )
        val materialParts = mutableListOf(canonicalInstance)
        if (cacheNamespace.isNotBlank()) materialParts += cacheNamespace
        materialParts += request.apiBase.trim().trimEnd('/').lowercase()
        materialParts += request.modelName.orEmpty().trim()
        materialParts += request.backgroundAuthType.name
        materialParts += request.modelApiKey.trim()
        val material = materialParts.joinToString("\u001F")

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        val safeInstance = canonicalInstance.lowercase().replace(Regex("[^a-z0-9_-]"), "_")
        return "model-cache-${safeInstance}-${digest.take(32)}"
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

            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: SecurityException) {
            true
        } catch (_: Exception) {
            true
        }
    }
}
