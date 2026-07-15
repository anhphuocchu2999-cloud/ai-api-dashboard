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
 * MiMo 开放平台适配器。
 *
 * 已验证网页 Cookie 接口：
 * - GET /api/v1/balance：余额、赠送余额、现金余额等；
 * - GET /api/v1/usage：Token、消费、请求次数和账户限流摘要。
 *
 * Billing 是网页账户数据来源，不是第三份凭据；两个接口均复用网页登录 Cookie。
 * 服务端累计值交给 RecentUsageTracker 在本机计算近 1/6/12/24 小时真实消耗。
 */
class MiMoAdapter : PlatformAdapter {

    override val platformName: String = "MiMo"

    override val capabilityProfile = ProviderCapabilityProfile(
        modelApiKeyRequired = true,
        backgroundAuthType = BackgroundAuthType.COOKIE,
        sources = setOf(
            DataSourceType.API,
            DataSourceType.WEB_AUTH,
            DataSourceType.BILLING
        ),
        capabilities = setOf(
            DataCapability.MODELS,
            DataCapability.BALANCE,
            DataCapability.USAGE,
            DataCapability.REQUESTS,
            DataCapability.TOKENS
        )
    )

    override fun detect(apiBase: String, apiKey: String): Boolean {
        return apiBase.contains("xiaomimimo.com", ignoreCase = true)
    }

    override fun fetchData(request: AdapterRequest): WidgetData {
        val cookie = if (
            request.backgroundAuthType == BackgroundAuthType.COOKIE &&
            request.backgroundCredential.isNotBlank()
        ) {
            request.backgroundCredential
        } else {
            // 兼容历史版本：旧配置曾把 Cookie 放在 apiKey 字段。
            request.modelApiKey
        }

        val usageIdentity = RecentUsageTracker.identity(
            provider = platformName,
            apiBase = request.apiBase,
            apiKey = request.modelApiKey,
            modelName = request.modelName
        )

        return fetchAccountData(cookie, request.modelName, usageIdentity)
    }

    override fun fetchData(apiBase: String, apiKey: String, modelName: String?): WidgetData {
        val usageIdentity = RecentUsageTracker.identity(
            provider = platformName,
            apiBase = apiBase,
            apiKey = apiKey,
            modelName = modelName
        )
        return fetchAccountData(apiKey, modelName, usageIdentity)
    }

    private fun fetchAccountData(
        cookie: String,
        modelName: String?,
        usageIdentity: String
    ): WidgetData {
        val cleanCookie = cookie.trim()
        if (cleanCookie.isBlank()) {
            return WidgetData.error(platformName, "网页登录未连接")
        }

        val balanceResult = fetchJson(BALANCE_URL, cleanCookie)
        val balanceData = when (balanceResult) {
            is HttpJsonResult.Success -> parseBalanceResponse(balanceResult.body, modelName)
            HttpJsonResult.AuthExpired -> return WidgetData.error(platformName, "网页登录需重连")
            HttpJsonResult.RateLimited -> return WidgetData.error(platformName, "请求过于频繁")
            HttpJsonResult.ServerError -> return WidgetData.error(platformName, "服务器错误")
            HttpJsonResult.NetworkError -> return WidgetData.error(platformName, "网络错误")
            HttpJsonResult.Unavailable -> return WidgetData.error(platformName, "余额同步失败")
        }

        if (!balanceData.isSuccess) return balanceData

        // 同一 Host 串行请求并保持最小间隔，避免对平台造成突发请求。
        try {
            Thread.sleep(HOST_REQUEST_INTERVAL_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return balanceData
        }

        return when (val usageResult = fetchJson(USAGE_URL, cleanCookie)) {
            is HttpJsonResult.Success -> {
                val usageSummary = parseUsageResponse(
                    response = usageResult.body,
                    currencySymbol = balanceData.displayLabel.orEmpty()
                )
                if (usageSummary == null) {
                    balanceData.copy(statusText = "余额已同步")
                } else {
                    val merged = mergeUsageSummary(balanceData, usageSummary)
                    RecentUsageTracker.apply(
                        context = DashboardApplication.appContextOrNull(),
                        identity = usageIdentity,
                        data = merged,
                        cumulative = RecentUsageTracker.CumulativeUsage(
                            requests = usageSummary.totalRequests,
                            tokens = usageSummary.totalTokens,
                            cost = usageSummary.totalCost,
                            currency = usageSummary.currency
                        )
                    )
                }
            }
            HttpJsonResult.AuthExpired -> balanceData.copy(statusText = "网页登录需重连")
            HttpJsonResult.RateLimited -> balanceData.copy(statusText = "用量请求过于频繁")
            HttpJsonResult.ServerError,
            HttpJsonResult.NetworkError,
            HttpJsonResult.Unavailable -> balanceData.copy(statusText = "余额已同步")
        }
    }

    private fun fetchJson(url: String, cookie: String): HttpJsonResult {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Cookie", cookie)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Referer", "https://platform.xiaomimimo.com/")
            conn.setRequestProperty("User-Agent", MOBILE_USER_AGENT)
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val responseCode = conn.responseCode
            val responseBody = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            conn.disconnect()

            when (responseCode) {
                in 200..299 -> HttpJsonResult.Success(responseBody)
                401, 403 -> HttpJsonResult.AuthExpired
                429 -> HttpJsonResult.RateLimited
                in 500..599 -> HttpJsonResult.ServerError
                else -> HttpJsonResult.Unavailable
            }
        } catch (_: java.net.SocketTimeoutException) {
            HttpJsonResult.NetworkError
        } catch (_: java.net.ConnectException) {
            HttpJsonResult.NetworkError
        } catch (_: java.net.UnknownHostException) {
            HttpJsonResult.NetworkError
        } catch (_: java.io.IOException) {
            HttpJsonResult.NetworkError
        } catch (_: Exception) {
            HttpJsonResult.Unavailable
        }
    }

    private fun parseBalanceResponse(response: String, modelName: String?): WidgetData {
        return try {
            val root = JSONObject(response)
            val data = root.optJSONObject("data")
                ?: return WidgetData.error(platformName, "余额解析失败")

            val balance = decimal(data, "balance")
                ?: return WidgetData.error(platformName, "余额解析失败")
            val frozenBalance = decimal(data, "frozenBalance")
            val remainingOverdraftLimit = decimal(data, "remainingOverdraftLimit")
            val giftBalance = decimal(data, "giftBalance") ?: BigDecimal.ZERO
            val cashBalance = decimal(data, "cashBalance")
            val currency = data.optString("currency", "CNY")
            val symbol = currencySymbol(currency)

            val percentage = if (balance > BigDecimal.ZERO) {
                giftBalance
                    .multiply(BigDecimal("100"))
                    .divide(balance, 0, RoundingMode.HALF_UP)
                    .toInt()
                    .coerceIn(0, 100)
            } else {
                null
            }

            val auxiliaryMetrics = mutableListOf<WidgetData.DisplayMetric>()
            auxiliaryMetrics.add(
                WidgetData.DisplayMetric("赠送余额", "$symbol${formatMoney(giftBalance)}")
            )
            cashBalance?.takeIf { it > BigDecimal.ZERO }?.let {
                auxiliaryMetrics.add(
                    WidgetData.DisplayMetric("现金余额", "$symbol${formatMoney(it)}")
                )
            }
            frozenBalance?.takeIf { it > BigDecimal.ZERO }?.let {
                auxiliaryMetrics.add(
                    WidgetData.DisplayMetric("冻结余额", "$symbol${formatMoney(it)}")
                )
            }
            remainingOverdraftLimit?.takeIf { it > BigDecimal.ZERO }?.let {
                auxiliaryMetrics.add(
                    WidgetData.DisplayMetric("可用透支", "$symbol${formatMoney(it)}")
                )
            }

            WidgetData(
                platformName = platformName,
                modelName = modelName,
                primaryMetric = WidgetData.DisplayMetric(
                    "余额",
                    "$symbol${formatMoney(balance)}"
                ),
                usageMetrics = emptyList(),
                percentage = percentage,
                percentageLabel = "赠送占比",
                auxiliaryMetrics = auxiliaryMetrics,
                statusText = "正常",
                isSuccess = true,
                displayLabel = symbol,
                total = balance.toDouble(),
                used = null,
                remaining = balance.toDouble(),
                usagePercent = percentage,
                isAvailable = true
            )
        } catch (_: Exception) {
            WidgetData.error(platformName, "余额解析失败")
        }
    }

    private fun parseUsageResponse(
        response: String,
        currencySymbol: String
    ): UsageSummary? {
        return try {
            val root = JSONObject(response)
            val data = root.optJSONObject("data") ?: return null
            val tokenUsage = data.optJSONObject("tokenUsage")
            val costUsage = data.optJSONObject("costUsage")
            val pluginUsage = data.optJSONObject("pluginUsage")
            val rateLimit = data.optJSONObject("accountRateLimit")
            val symbol = currencySymbol.ifBlank { "¥" }

            val totalRequests = longValue(pluginUsage, "totalRequestCount")
            val totalTokens = longValue(tokenUsage, "totalToken")
            val totalCost = decimal(costUsage, "totalCost")

            // 动态位②只放账户资源和诊断价值较高的数据，不重复近期消耗。
            val auxiliaryMetrics = mutableListOf<WidgetData.DisplayMetric>()
            longValue(tokenUsage, "cacheToken")?.let {
                auxiliaryMetrics.add(
                    WidgetData.DisplayMetric("缓存 Token", formatCount(it))
                )
            }
            longValue(pluginUsage, "webSearchRequestCount")?.let {
                auxiliaryMetrics.add(
                    WidgetData.DisplayMetric("Web 搜索", "${formatCount(it)}次")
                )
            }
            longValue(rateLimit, "rpm")?.let {
                auxiliaryMetrics.add(WidgetData.DisplayMetric("RPM 上限", formatCount(it)))
            }
            longValue(rateLimit, "tpm")?.let {
                auxiliaryMetrics.add(WidgetData.DisplayMetric("TPM 上限", formatCount(it)))
            }
            longValue(rateLimit, "queryTpm")?.let {
                auxiliaryMetrics.add(WidgetData.DisplayMetric("查询 TPM", formatCount(it)))
            }
            longValue(rateLimit, "concurrency")?.let {
                auxiliaryMetrics.add(WidgetData.DisplayMetric("并发上限", formatCount(it)))
            }

            if (totalRequests == null && totalTokens == null && totalCost == null &&
                auxiliaryMetrics.isEmpty()
            ) {
                null
            } else {
                UsageSummary(
                    auxiliaryMetrics = auxiliaryMetrics,
                    totalRequests = totalRequests,
                    totalTokens = totalTokens,
                    totalCost = totalCost,
                    currency = symbol
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun mergeUsageSummary(
        balanceData: WidgetData,
        summary: UsageSummary
    ): WidgetData {
        val auxiliaryMetrics = (balanceData.auxiliaryMetrics + summary.auxiliaryMetrics)
            .distinctBy { "${it.label}|${it.value}" }

        return balanceData.copy(
            usageMetrics = emptyList(),
            auxiliaryMetrics = auxiliaryMetrics,
            statusText = "网页账单已同步"
        )
    }

    private fun decimal(obj: JSONObject?, key: String): BigDecimal? {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null
        return obj.opt(key)?.toString()?.toBigDecimalOrNull()
    }

    private fun longValue(obj: JSONObject?, key: String): Long? {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null
        return obj.opt(key)?.toString()?.toBigDecimalOrNull()?.toLong()
    }

    private fun currencySymbol(currency: String): String {
        return when (currency.uppercase()) {
            "CNY" -> "¥"
            "USD" -> "$"
            else -> currency.uppercase().takeIf { it.isNotBlank() }?.plus(" ") ?: ""
        }
    }

    private fun formatMoney(value: BigDecimal): String {
        val rounded = value.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
        return if (rounded.scale() < 2) {
            rounded.setScale(2, RoundingMode.HALF_UP).toPlainString()
        } else {
            rounded.toPlainString()
        }
    }

    private fun formatCount(value: Long): String {
        val absolute = kotlin.math.abs(value.toDouble())
        return when {
            absolute >= 1_000_000_000 -> formatCompact(value, 1_000_000_000.0, "B")
            absolute >= 1_000_000 -> formatCompact(value, 1_000_000.0, "M")
            absolute >= 1_000 -> formatCompact(value, 1_000.0, "K")
            else -> value.toString()
        }
    }

    private fun formatCompact(value: Long, divisor: Double, suffix: String): String {
        val compact = BigDecimal.valueOf(value / divisor)
            .setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        return "${compact.toPlainString()}$suffix"
    }

    private data class UsageSummary(
        val auxiliaryMetrics: List<WidgetData.DisplayMetric>,
        val totalRequests: Long?,
        val totalTokens: Long?,
        val totalCost: BigDecimal?,
        val currency: String
    )

    private sealed class HttpJsonResult {
        data class Success(val body: String) : HttpJsonResult()
        data object AuthExpired : HttpJsonResult()
        data object RateLimited : HttpJsonResult()
        data object ServerError : HttpJsonResult()
        data object NetworkError : HttpJsonResult()
        data object Unavailable : HttpJsonResult()
    }

    companion object {
        private const val BALANCE_URL =
            "https://platform.xiaomimimo.com/api/v1/balance"
        private const val USAGE_URL =
            "https://platform.xiaomimimo.com/api/v1/usage"
        private const val HOST_REQUEST_INTERVAL_MS = 500L
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125 Mobile Safari/537.36"
    }
}
