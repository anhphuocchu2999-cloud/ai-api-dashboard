package com.java.myapplication.adapter

import com.java.myapplication.DashboardApplication
import com.java.myapplication.adapter.auth.BackgroundAuthType
import com.java.myapplication.adapter.capability.DataCapability
import com.java.myapplication.adapter.capability.DataSourceType
import com.java.myapplication.adapter.capability.ProviderCapabilityProfile
import com.java.myapplication.stats.RecentUsageTracker
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * 爱黄牛中转站适配器。
 *
 * 两条数据源完全独立：
 * - 模型 API Key：GET /v1/usage，读取累计请求、Token 和实际消耗；
 * - 网页 Bearer Token：GET /api/v1/user/profile，读取余额和账户资料。
 *
 * 未登录平台账户时，API 用量仍然正常读取并参与近期消耗计算。
 */
class AihuangniuAdapter : PlatformAdapter {

    override val platformName: String = "Aihuangniu"

    override val capabilityProfile = ProviderCapabilityProfile(
        modelApiKeyRequired = true,
        backgroundAuthType = BackgroundAuthType.BEARER_TOKEN,
        sources = setOf(DataSourceType.API, DataSourceType.WEB_AUTH),
        capabilities = setOf(
            DataCapability.MODELS,
            DataCapability.BALANCE,
            DataCapability.USAGE,
            DataCapability.REQUESTS,
            DataCapability.TOKENS,
            DataCapability.PROFILE
        )
    )

    override fun detect(apiBase: String, apiKey: String): Boolean {
        return apiBase.contains("aihuangniu.com", ignoreCase = true)
    }

    override fun fetchData(request: AdapterRequest): WidgetData {
        val backgroundToken = if (
            request.backgroundAuthType == BackgroundAuthType.BEARER_TOKEN &&
            request.backgroundCredential.isNotBlank()
        ) {
            request.backgroundCredential.trim()
        } else {
            ""
        }

        return fetchDataWithCredentials(
            apiBase = request.apiBase,
            modelApiKey = request.modelApiKey,
            modelName = request.modelName,
            backgroundToken = backgroundToken
        )
    }

    override fun fetchData(apiBase: String, apiKey: String, modelName: String?): WidgetData {
        return fetchDataWithCredentials(
            apiBase = apiBase,
            modelApiKey = apiKey,
            modelName = modelName,
            backgroundToken = ""
        )
    }

    private fun fetchDataWithCredentials(
        apiBase: String,
        modelApiKey: String,
        modelName: String?,
        backgroundToken: String
    ): WidgetData {
        val rootBase = normalizeRootBase(apiBase)
        if (modelApiKey.isBlank()) {
            return WidgetData.error(platformName, "Key未配置")
        }

        val usageResult = fetchUsageCumulative(rootBase, modelApiKey, modelName)

        val profileData = if (backgroundToken.isNotBlank()) {
            try {
                Thread.sleep(HOST_REQUEST_INTERVAL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            fetchProfileData(rootBase, backgroundToken)
        } else {
            null
        }

        val usage = (usageResult as? UsageFetchResult.Success)?.usage
        val profileSuccess = profileData?.takeIf { it.isSuccess && it.isAvailable }

        if (usage == null && profileSuccess == null) {
            return when {
                usageResult is UsageFetchResult.ModelNotFound -> {
                    WidgetData.error(platformName, "当前模型没有返回独立用量")
                }
                profileData != null -> profileData
                else -> WidgetData.error(platformName, "用量接口本次未返回")
            }
        }

        val baseData = profileSuccess?.copy(modelName = modelName) ?: buildApiOnlyData(
            modelName = modelName,
            usage = usage
        )

        val auxiliary = mutableListOf<WidgetData.DisplayMetric>()
        auxiliary.addAll(baseData.auxiliaryMetrics)
        usage?.let {
            auxiliary.add(WidgetData.DisplayMetric("累计 Token", formatCount(it.tokens)))
            auxiliary.add(WidgetData.DisplayMetric("累计调用", "${formatCount(it.requests)}次"))
            auxiliary.add(WidgetData.DisplayMetric("累计消耗", formatCost(it.cost)))
        }

        val status = when {
            profileSuccess != null && usage != null -> "账户余额和 API 用量已同步"
            profileSuccess != null -> when (usageResult) {
                UsageFetchResult.ModelNotFound -> "账户余额已同步，当前模型没有独立用量"
                else -> "账户余额已同步，API 用量本次未返回"
            }
            backgroundToken.isNotBlank() -> "API 用量已同步，平台账户授权本次不可用"
            else -> "API 用量已同步，登录平台账户后补充余额"
        }

        val merged = baseData.copy(
            modelName = modelName,
            usageMetrics = emptyList(),
            auxiliaryMetrics = auxiliary.distinctBy { "${it.label}|${it.value}" },
            statusText = status,
            cumulativeUsageRequests = null,
            cumulativeUsageTokens = null,
            cumulativeUsageActualCost = null
        )

        if (usage == null) return merged

        return RecentUsageTracker.apply(
            context = DashboardApplication.appContextOrNull(),
            identity = RecentUsageTracker.identity(
                provider = platformName,
                apiBase = rootBase,
                apiKey = modelApiKey,
                modelName = modelName
            ),
            data = merged,
            cumulative = RecentUsageTracker.CumulativeUsage(
                requests = usage.requests,
                tokens = usage.tokens,
                cost = usage.cost,
                currency = "CNY"
            )
        )
    }

    private fun buildApiOnlyData(
        modelName: String?,
        usage: UsageCumulative?
    ): WidgetData {
        val primaryMetric = usage?.let {
            WidgetData.DisplayMetric("累计消耗", formatCost(it.cost))
        }

        return WidgetData(
            platformName = platformName,
            modelName = modelName,
            primaryMetric = primaryMetric,
            usageMetrics = emptyList(),
            percentage = null,
            percentageLabel = null,
            auxiliaryMetrics = emptyList(),
            statusText = "API 用量已同步",
            isSuccess = usage != null,
            displayLabel = "¥",
            total = usage?.cost?.toDouble(),
            used = usage?.cost?.toDouble(),
            remaining = null,
            usagePercent = null,
            isAvailable = usage != null
        )
    }

    private fun fetchProfileData(rootBase: String, token: String): WidgetData {
        val url = "$rootBase/api/v1/user/profile"
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                parseProfileResponse(body)
            } else {
                conn.disconnect()
                val message = when (responseCode) {
                    401 -> "爱黄牛登录已失效，请重新登录"
                    403 -> "平台账户访问被拒绝"
                    404 -> "账户资料接口不存在"
                    429 -> "请求过于频繁"
                    in 500..599 -> "服务器错误"
                    else -> "账户资料请求失败 ($responseCode)"
                }
                WidgetData.error(platformName, message)
            }
        } catch (_: java.net.SocketTimeoutException) {
            WidgetData.error(platformName, "连接超时")
        } catch (_: java.net.ConnectException) {
            WidgetData.error(platformName, "连接失败")
        } catch (_: java.net.UnknownHostException) {
            WidgetData.error(platformName, "域名解析失败")
        } catch (_: java.io.IOException) {
            WidgetData.error(platformName, "网络错误")
        } catch (_: Exception) {
            WidgetData.error(platformName, "账户资料读取失败")
        }
    }

    private fun fetchUsageCumulative(
        rootBase: String,
        modelApiKey: String,
        modelName: String?
    ): UsageFetchResult {
        if (modelName.isNullOrBlank()) return UsageFetchResult.ModelNotFound

        val url = "$rootBase/v1/usage"
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${modelApiKey.trim()}")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                conn.disconnect()
                return UsageFetchResult.Unavailable
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            parseUsageCumulative(body, modelName)
                ?.let { UsageFetchResult.Success(it) }
                ?: UsageFetchResult.ModelNotFound
        } catch (_: Exception) {
            UsageFetchResult.Unavailable
        }
    }

    private fun parseUsageCumulative(
        response: String,
        modelName: String
    ): UsageCumulative? {
        return try {
            val root = JSONObject(response)
            val modelStats = root.optJSONArray("model_stats")
                ?: root.optJSONObject("data")?.optJSONArray("model_stats")
                ?: return null

            var matched: JSONObject? = null
            for (index in 0 until modelStats.length()) {
                val item = modelStats.optJSONObject(index) ?: continue
                if (item.optString("model", "").equals(modelName, ignoreCase = true)) {
                    matched = item
                    break
                }
            }

            val item = matched ?: return null
            val requests = decimal(item, "requests")?.toLong() ?: return null
            val tokens = decimal(item, "total_tokens")?.toLong() ?: return null
            val cost = decimal(item, "actual_cost") ?: return null
            if (requests < 0L || tokens < 0L || cost < BigDecimal.ZERO) return null

            UsageCumulative(requests, tokens, cost)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseProfileResponse(response: String): WidgetData {
        return try {
            val root = JSONObject(response)
            val data = root.optJSONObject("data")
                ?: return WidgetData.error(platformName, "账户资料返回格式错误")
            val balance = decimal(data, "balance")
                ?: return WidgetData.error(platformName, "账户余额未返回")
            val totalRecharged = decimal(data, "total_recharged")
            val concurrency = decimal(data, "concurrency")?.toLong()
            val status = data.optString("status", "").trim()
            val lastActiveAt = data.optString("last_active_at", "").trim()

            val percentage = if (totalRecharged != null && totalRecharged > BigDecimal.ZERO) {
                balance.multiply(BigDecimal("100"))
                    .divide(totalRecharged, 0, RoundingMode.HALF_UP)
                    .toInt()
                    .coerceIn(0, 100)
            } else {
                null
            }

            val auxiliary = mutableListOf<WidgetData.DisplayMetric>()
            totalRecharged?.let {
                auxiliary.add(WidgetData.DisplayMetric("累计充值", formatCost(it)))
            }
            concurrency?.let {
                auxiliary.add(WidgetData.DisplayMetric("并发上限", it.toString()))
            }
            status.takeIf { it.isNotBlank() }?.let {
                auxiliary.add(WidgetData.DisplayMetric("账户状态", it))
            }
            lastActiveAt.takeIf { it.isNotBlank() }?.let {
                auxiliary.add(WidgetData.DisplayMetric("最近活跃", it.take(10)))
            }

            WidgetData(
                platformName = platformName,
                modelName = null,
                primaryMetric = WidgetData.DisplayMetric("余额", formatCost(balance)),
                usageMetrics = emptyList(),
                percentage = percentage,
                percentageLabel = percentage?.let { "余额占充值" },
                auxiliaryMetrics = auxiliary,
                statusText = "平台账户已同步",
                isSuccess = true,
                displayLabel = "¥",
                total = balance.toDouble(),
                used = null,
                remaining = balance.toDouble(),
                usagePercent = percentage,
                isAvailable = true
            )
        } catch (_: Exception) {
            WidgetData.error(platformName, "账户资料解析失败")
        }
    }

    private fun normalizeRootBase(apiBase: String): String {
        return apiBase.trim().trimEnd('/').removeSuffix("/v1")
    }

    private fun decimal(obj: JSONObject, key: String): BigDecimal? {
        if (!obj.has(key) || obj.isNull(key)) return null
        return obj.opt(key)?.toString()?.toBigDecimalOrNull()
    }

    private fun formatCount(value: Long): String {
        val absolute = kotlin.math.abs(value.toDouble())
        return when {
            absolute >= 1_000_000_000 -> compact(value, 1_000_000_000.0, "B")
            absolute >= 1_000_000 -> compact(value, 1_000_000.0, "M")
            absolute >= 1_000 -> compact(value, 1_000.0, "K")
            else -> value.toString()
        }
    }

    private fun compact(value: Long, divisor: Double, suffix: String): String {
        val result = BigDecimal.valueOf(value / divisor)
            .setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        return "${result.toPlainString()}$suffix"
    }

    private fun formatCost(cost: BigDecimal): String {
        val scale = when {
            cost.abs() >= BigDecimal.ONE -> 2
            cost.abs() >= BigDecimal("0.001") -> 4
            else -> 6
        }
        val value = cost.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        return "¥$value"
    }

    private data class UsageCumulative(
        val requests: Long,
        val tokens: Long,
        val cost: BigDecimal
    )

    private sealed class UsageFetchResult {
        data class Success(val usage: UsageCumulative) : UsageFetchResult()
        data object ModelNotFound : UsageFetchResult()
        data object Unavailable : UsageFetchResult()
    }

    companion object {
        private const val HOST_REQUEST_INTERVAL_MS = 500L
    }
}
