package com.java.myapplication.adapter

import com.java.myapplication.DashboardApplication
import com.java.myapplication.adapter.auth.BackgroundAuthFactory
import com.java.myapplication.adapter.auth.BackgroundAuthType
import com.java.myapplication.adapter.capability.DataCapability
import com.java.myapplication.adapter.capability.DataSourceType
import com.java.myapplication.adapter.capability.ProviderCapabilityProfile
import com.java.myapplication.stats.RecentUsageTracker
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject

/**
 * 爱黄牛中转站适配器。
 *
 * - 网页 Bearer Token：GET /api/v1/user/profile，读取余额和账户资料；
 * - 模型 API Key：GET /v1/usage，读取累计请求、Token 和实际消耗；
 * - 服务端累计值在本机计算近 1/6/12/24 小时真实消耗。
 */
class AihuangniuAdapter(
    private val backgroundBearerToken: String? = null
) : PlatformAdapter {

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
        return try {
            val normalizedBase = apiBase.trim().trimEnd('/')
            val url = "$normalizedBase/api/v1/user/profile"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val code = conn.responseCode
            conn.disconnect()
            code == 200
        } catch (_: Exception) {
            false
        }
    }

    override fun fetchData(request: AdapterRequest): WidgetData {
        val backgroundToken = if (
            request.backgroundAuthType == BackgroundAuthType.BEARER_TOKEN &&
            request.backgroundCredential.isNotBlank()
        ) {
            request.backgroundCredential
        } else {
            backgroundBearerToken.orEmpty()
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
            backgroundToken = backgroundBearerToken.orEmpty()
        )
    }

    private fun fetchDataWithCredentials(
        apiBase: String,
        modelApiKey: String,
        modelName: String?,
        backgroundToken: String
    ): WidgetData {
        val normalizedBase = apiBase.trim().trimEnd('/')

        if (modelApiKey.isBlank()) {
            return WidgetData.error(platformName, "Key未配置")
        }

        // 1. 用户资料（余额等）优先使用网页登录获得的后台 Bearer Token。
        val profileCredential = backgroundToken.ifBlank { modelApiKey }
        val profileData = fetchProfileData(normalizedBase, profileCredential)
        if (!profileData.isSuccess) return profileData

        // 2. 累计统计始终使用模型 API Key。
        val effectiveModelName = modelName?.takeIf { it.isNotBlank() } ?: profileData.modelName
        val cumulative = fetchUsageCumulative(normalizedBase, modelApiKey, effectiveModelName)
            ?: return profileData.copy(statusText = "账户数据已同步")

        val (requests, tokens, actualCostText) = cumulative
        val actualCost = actualCostText.toBigDecimalOrNull()

        // 动态位②：整体账户概况，不与动态位①的近期窗口重复。
        val auxiliaryMetrics = buildList {
            addAll(profileData.auxiliaryMetrics)
            add(WidgetData.DisplayMetric("累计 Token", formatCompactCount(tokens)))
            add(WidgetData.DisplayMetric("累计调用", "${formatCompactCount(requests)}次"))
            actualCost?.let {
                add(WidgetData.DisplayMetric("累计消耗", formatCost(it)))
            }
        }.distinctBy { "${it.label}|${it.value}" }

        val merged = profileData.copy(
            modelName = effectiveModelName,
            usageMetrics = emptyList(),
            auxiliaryMetrics = auxiliaryMetrics,
            statusText = "账户数据已同步",
            // 不再交给 Provider 的旧单次差值逻辑，统一由 RecentUsageTracker 处理。
            cumulativeUsageRequests = null,
            cumulativeUsageTokens = null,
            cumulativeUsageActualCost = null
        )

        return RecentUsageTracker.apply(
            context = DashboardApplication.appContextOrNull(),
            identity = RecentUsageTracker.identity(
                provider = platformName,
                apiBase = normalizedBase,
                apiKey = modelApiKey,
                modelName = effectiveModelName
            ),
            data = merged,
            cumulative = RecentUsageTracker.CumulativeUsage(
                requests = requests,
                tokens = tokens,
                cost = actualCost,
                currency = "CNY"
            )
        )
    }

    private fun fetchProfileData(apiBase: String, apiKey: String): WidgetData {
        val url = "$apiBase/api/v1/user/profile"

        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val strategy = BackgroundAuthFactory.getStrategy(BackgroundAuthType.BEARER_TOKEN)
            strategy?.apply(conn, apiKey.trim())

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                parseProfileResponse(responseBody)
            } else {
                conn.disconnect()
                val errorMessage = when (responseCode) {
                    401 -> "授权无效"
                    403 -> "访问被拒绝"
                    404 -> "接口不存在"
                    429 -> "请求过于频繁"
                    in 500..599 -> "服务器错误"
                    else -> "请求失败 ($responseCode)"
                }
                WidgetData.error(platformName, errorMessage)
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
            WidgetData.error(platformName, "未知错误")
        }
    }

    /** 返回 Triple(requests, total_tokens, actual_cost_string)。 */
    private fun fetchUsageCumulative(
        apiBase: String,
        modelApiKey: String,
        modelName: String?
    ): Triple<Long, Long, String>? {
        if (modelName.isNullOrBlank()) return null

        val url = "$apiBase/v1/usage"
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${modelApiKey.trim()}")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                conn.disconnect()
                return null
            }

            val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            parseUsageCumulative(responseBody, modelName)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseUsageCumulative(
        response: String,
        modelName: String
    ): Triple<Long, Long, String>? {
        return try {
            val root = JSONObject(response)
            val modelStats = root.optJSONArray("model_stats")
            if (modelStats == null || modelStats.length() == 0) return null

            for (index in 0 until modelStats.length()) {
                val item = modelStats.getJSONObject(index)
                if (item.optString("model", "") != modelName) continue

                val requests = item.optLong("requests", -1).takeIf { it >= 0 } ?: return null
                val totalTokens = item.optLong("total_tokens", -1).takeIf { it >= 0 } ?: return null
                val actualCost = item.optDouble("actual_cost", -1.0).takeIf { it >= 0 }
                    ?: return null
                val costText = BigDecimal(actualCost.toString())
                    .setScale(6, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString()
                return Triple(requests, totalTokens, costText)
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /** 历史明细接口保留，供后续详情页使用。 */
    private fun fetchUsagePage(
        apiBase: String,
        apiKey: String,
        page: Int,
        pageSize: Int
    ): List<UsageRecord> {
        val url = "$apiBase/api/v1/usage?page=$page&page_size=$pageSize"

        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val strategy = BackgroundAuthFactory.getStrategy(BackgroundAuthType.BEARER_TOKEN)
            strategy?.apply(conn, apiKey.trim())

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                conn.disconnect()
                return emptyList()
            }

            val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            parseUsageResponse(responseBody)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseUsageResponse(response: String): List<UsageRecord> {
        return try {
            val root = JSONObject(response)
            val items = root.optJSONObject("data")?.optJSONArray("items")
            if (items == null || items.length() == 0) return emptyList()

            buildList {
                for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    val model = item.optString("model", "")
                    val createdAt = item.optString("created_at", "")
                    val timestamp = parseTimestamp(createdAt)
                    if (model.isBlank() || timestamp <= 0L) continue
                    add(
                        UsageRecord(
                            model = model,
                            timestamp = timestamp,
                            inputTokens = item.optInt("input_tokens", 0).coerceAtLeast(0),
                            outputTokens = item.optInt("output_tokens", 0).coerceAtLeast(0),
                            actualCost = item.optDouble("actual_cost", 0.0).coerceAtLeast(0.0)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseTimestamp(createdAt: String): Long {
        return try {
            val normalized = if (createdAt.contains(".")) {
                val dotIndex = createdAt.indexOf('.')
                val plusIndex = createdAt.indexOf('+', dotIndex)
                val zIndex = createdAt.indexOf('Z', dotIndex)
                val endIndex = if (plusIndex != -1) plusIndex else if (zIndex != -1) zIndex else createdAt.length
                val fraction = createdAt.substring(dotIndex + 1, endIndex)
                val truncatedFraction = fraction.take(3).padEnd(3, '0')
                createdAt.substring(0, dotIndex + 1) + truncatedFraction + createdAt.substring(endIndex)
            } else {
                createdAt
            }
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(normalized)?.time ?: 0L
        } catch (_: Exception) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(createdAt)?.time ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
    }

    private fun aggregateUsageMetrics(records: List<UsageRecord>): List<WidgetData.DisplayMetric> {
        val now = System.currentTimeMillis()
        val windows = listOf(
            "近1分钟" to 60_000L,
            "近10分钟" to 600_000L,
            "近30分钟" to 1_800_000L,
            "近12小时" to 43_200_000L,
            "近24小时" to 86_400_000L
        )
        val metrics = mutableListOf<WidgetData.DisplayMetric>()

        for ((label, windowMs) in windows) {
            val windowRecords = records.filter { it.timestamp >= now - windowMs }
            if (windowRecords.isEmpty()) continue
            val calls = windowRecords.size
            val tokens = windowRecords.sumOf { (it.inputTokens + it.outputTokens).toLong() }
            val cost = windowRecords.sumOf { it.actualCost }
            metrics.add(WidgetData.DisplayMetric("${label}调用", "${calls}次"))
            if (tokens > 0) metrics.add(WidgetData.DisplayMetric("${label}Token", formatCompactCount(tokens)))
            if (cost > 0) metrics.add(WidgetData.DisplayMetric("${label}消耗", formatCost(BigDecimal(cost.toString()))))
        }
        return metrics
    }

    private fun parseProfileResponse(response: String): WidgetData {
        return try {
            val root = JSONObject(response)
            val data = root.optJSONObject("data")
            val balance = data?.optDouble("balance", -1.0) ?: -1.0
            if (balance < 0) return WidgetData.error(platformName, "返回格式错误")

            val totalRecharged = data?.optDouble("total_recharged", -1.0)?.takeIf { it >= 0 }
            val concurrency = data?.optInt("concurrency", -1)?.takeIf { it >= 0 }
            val status = data?.optString("status", null)
            val lastActiveAt = data?.optString("last_active_at", null)
            val percentage = if (totalRecharged != null && totalRecharged > 0) {
                balance / totalRecharged * 100
            } else {
                null
            }

            val auxiliary = mutableListOf<WidgetData.DisplayMetric>()
            totalRecharged?.let {
                auxiliary.add(
                    WidgetData.DisplayMetric("累计充值", "${WidgetData.formatNumber(it)} ¥")
                )
            }
            concurrency?.let {
                auxiliary.add(WidgetData.DisplayMetric("并发上限", it.toString()))
            }
            status?.takeIf { it.isNotBlank() }?.let {
                auxiliary.add(WidgetData.DisplayMetric("账户状态", it))
            }
            lastActiveAt?.takeIf { it.isNotBlank() }?.let {
                auxiliary.add(
                    WidgetData.DisplayMetric("最近活跃", it.substring(0, minOf(10, it.length)))
                )
            }

            WidgetData(
                platformName = platformName,
                modelName = null,
                primaryMetric = WidgetData.DisplayMetric(
                    "余额",
                    "${WidgetData.formatNumber(balance)} ¥"
                ),
                usageMetrics = emptyList(),
                percentage = percentage?.let { (it + 0.5).toInt() },
                percentageLabel = percentage?.let { "余额占充值" },
                auxiliaryMetrics = auxiliary,
                statusText = "正常",
                isSuccess = true,
                displayLabel = "¥",
                total = balance,
                used = null,
                remaining = balance,
                usagePercent = null,
                isAvailable = true
            )
        } catch (_: Exception) {
            WidgetData.error(platformName, "数据解析失败")
        }
    }

    private fun formatCompactCount(value: Long): String {
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
        return "¥${cost.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()}"
    }

    private data class UsageRecord(
        val model: String,
        val timestamp: Long,
        val inputTokens: Int,
        val outputTokens: Int,
        val actualCost: Double
    )
}
