package com.java.myapplication.adapter

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.java.myapplication.DashboardApplication
import com.java.myapplication.adapter.capability.ProviderCapabilityProfile

/**
 * 在进入具体平台请求前做一次系统网络状态快速判断。
 *
 * 断网或默认网络未通过系统验证时，立即返回可触发 Widget 最近成功数据兜底的
 * 临时网络错误，避免每个平台依次等待 HTTP 超时后才恢复缓存。
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
        if (!hasUsableNetwork()) {
            return WidgetData.error(platformName, "网络错误")
        }
        return delegate.fetchData(request)
    }

    override fun fetchData(
        apiBase: String,
        apiKey: String,
        modelName: String?
    ): WidgetData {
        if (!hasUsableNetwork()) {
            return WidgetData.error(platformName, "网络错误")
        }
        return delegate.fetchData(apiBase, apiKey, modelName)
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
