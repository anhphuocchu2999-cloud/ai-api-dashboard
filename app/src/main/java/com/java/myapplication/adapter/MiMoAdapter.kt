package com.java.myapplication.adapter

import com.java.myapplication.DashboardApplication
import com.java.myapplication.adapter.auth.BackgroundAuthType
import com.java.myapplication.adapter.capability.DataCapability
import com.java.myapplication.adapter.capability.DataSourceType
import com.java.myapplication.adapter.capability.ProviderCapabilityProfile
import com.java.myapplication.stats.RecentUsageTracker
import com.java.myapplication.config.ServiceHostMatcher
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * MiMo 开放平台适配器。
 *
 * 账户余额与用量均来自网页登录 Cookie；模型 API Key 只用于模型连接，绝不冒充 Cookie。
 * 服务端累计请求、Token、金额交给 RecentUsageTracker 计算近期真实消耗。
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
        return ServiceHostMatcher.matches(apiBase, "xiaomimimo.com")
    }

    override fun fetchData(request: AdapterRequest): WidgetData {
        if (
            request.backgroundAuthType != BackgroundAuthType.COOKIE ||
            request.backgroundCredential.isBlank()
        ) {
            return WidgetData.error(platformName, "请先登录 MiMo 平台账户")
        }

        val cookie = request.backgroundCredential.trim()
        val accountIdentity = extractCookieValue(cookie, "userId") ?: cookie
        val usageIdentity = RecentUsageTracker.identity(
            provider = platformName,
            apiBase = PLATFORM_BASE,
            apiKey = accountIdentity,
            modelName = null
        )

        return fetchAccountData(cookie, request.modelName, usageIdentity)
    }

    /**
     * 旧入口不再把 apiKey 当 Cookie，避免把模型密钥发往账户接口。
     */
    override fun fetchData(apiBase: String, apiKey: String, modelName: String?): WidgetData {
        return WidgetData.error(platformName, "请先登录 MiMo 平台账户")
    }

    private fun fetchAccountData(
        cookie: String,
        modelName: String?,
        usageIdentity: String
    ): WidgetData {
        val balanceResult = fetchJson(BALANCE_URL, cookie)
        val balanceData = when (balanceResult) {
            is HttpJsonResult.Success -> parseBalanceResponse(balanceResult.body, modelName)
            HttpJsonResult.AuthExpired -> return WidgetData.error(platformName, "MiMo 登录已失效，请重新登录")
            HttpJsonResult.RateLimited -> return WidgetData.error(platformName, "请求过于频繁")
            HttpJsonResult.ServerError -> return WidgetData.error(platformName, "服务器错误")
            HttpJsonResult.NetworkError -> return WidgetData.error(platformName, "网络错误")
            HttpJsonResult.Unavailable -> return WidgetData.error(platformName, "余额同步失败")
        }

        if (!balanceData.isSuccess) return balanceData

        try {
            Thread.sleep(HOST_REQUEST_INTERVAL_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return balanceData.copy(statusText = "余额已同步，用量读取被中断")
        }

        return when (val usageResult = fetchJson(USAGE_URL, cookie)) {
            is HttpJsonResult.Success -> {
                val usageSummary = parseUsageResponse(
                    response = usageResult.body,
                    fallbackCurrency = balanceData.displayLabel.orEmpty()
                )
                if (usageSummary == null) {
                    balanceData.copy(statusText = "余额已同步，用量接口本次未返回可识别字段")
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
            HttpJsonResult.AuthExpired -> balanceData.copy(statusText = "余额已同步，MiMo 登录已失效")
            HttpJsonResult.RateLimited -> balanceData.copy(statusText = "余额已同步，用量请求过于频繁")
            HttpJsonResult.ServerError -> balanceData.copy(statusText = "余额已同步，用量服务器错误")
            HttpJsonResult.NetworkError -> balanceData.copy(statusText = "余额已同步，用量网络错误")
            HttpJsonResult.Unavailable -> balanceData.copy(statusText = "余额已同步，用量接口本次未返回")
        }
    }

    private fun fetchJson(url: String, cookie: String): HttpJsonResult {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Cookie", cookie)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Referer", "$PLATFORM_BASE/")
            conn.setRequestProperty("Origin", PLATFORM_BASE)
            conn.setRequestProperty("User-Agent", MOBILE_USER_AGENT)
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

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

            val balance = decimalAny(data, "balance")
                ?: return WidgetData.error(platformName, "余额解析失败")
            val frozenBalance = decimalAny(data, "frozenBalance", "frozen_balance")
            val overdraftLimit = decimalAny(data, "overdraftLimit", "overdraft_limit")
            val remainingOverdraftLimit = decimalAny(
                data,
                "remainingOverdraftLimit",
                "remaining_overdraft_limit"
            )
            val giftBalance = decimalAny(data, "giftBalance", "gift_balance")
            val cashBalance = decimalAny(data, "cashBalance", "cash_balance")
            val currency = stringAny(data, "currency").ifBlank { "CNY" }
            val symbol = currencySymbol(currency)

            val percentage = if (giftBalance != null && balance > BigDecimal.ZERO) {
                giftBalance
                    .multiply(BigDecimal("100"))
                    .divide(balance, 0, RoundingMode.HALF_UP)
                    .toInt()
                    .coerceIn(0, 100)
            } else {
                null
            }

            val auxiliaryMetrics = mutableListOf<WidgetData.DisplayMetric>()
            giftBalance?.let {
                auxiliaryMetrics.add(
                    WidgetData.DisplayMetric("赠送余额", "$symbol${formatMoney(it)}")
                )
            }
            cashBalance?.let {
                auxiliaryMetrics.add(
                    WidgetData.DisplayMetric("现金余额", "$symbol${formatMoney(it)}")
                )
            }
            frozenBalance?.let {
                auxiliaryMetrics.add(
                    WidgetData.DisplayMetric("冻结余额", "$symbol${formatMoney(it)}")
                )
            }
            remainingOverdraftLimit?.let {
                auxiliaryMetrics.add(
                    WidgetData.DisplayMetric("可用透支", "$symbol${formatMoney(it)}")
                )
            }
            overdraftLimit?.let {
                auxiliaryMetrics.add(
                    WidgetData.DisplayMetric("透支额度", "$symbol${formatMoney(it)}")
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
                percentageLabel = percentage?.let { "赠送占比" },
                auxiliaryMetrics = auxiliaryMetrics,
                statusText = "余额已同步",
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
        fallbackCurrency: String
    ): UsageSummary? {
        return try {
            val root = JSONObject(response)
            val data = root.optJSONObject("data") ?: return null
            val tokenUsage = data.optJSONObject("tokenUsage")
                ?: data.optJSONObject("token_usage")
            val costUsage = data.optJSONObject("costUsage")
                ?: data.optJSONObject("cost_usage")
            val pluginUsage = data.optJSONObject("pluginUsage")
                ?: data.optJSONObject("plugin_usage")
            val rateLimit = data.optJSONObject("accountRateLimit")
                ?: data.optJSONObject("account_rate_limit")

            val currencyRaw = stringAny(costUsage, "currency")
                .ifBlank { stringAny(data, "currency") }
                .ifBlank { fallbackCurrency }
                .ifBlank { "CNY" }
            val symbol = currencySymbol(currencyRaw)

            val currentMonthCost = decimalAny(
                costUsage,
                "currentMonthCost",
                "monthlyCost",
                "monthCost",
                "current_month_cost"
            )
            val totalCost = decimalAny(costUsage, "totalCost", "total_cost")

            val inputTokens = longAny(tokenUsage, "inputToken", "inputTokens", "input_token")
            val outputTokens = longAny(tokenUsage, "outputToken", "outputTokens", "output_token")
            val cacheTokens = longAny(tokenUsage, "cacheToken", "cacheTokens", "cache_token")
            val totalTokens = longAny(tokenUsage, "totalToken", "totalTokens", "total_token")
            val totalRequests = longAny(
                pluginUsage,
                "totalRequestCount",
                "requestCount",
                "total_requests"
            )
            val webSearchRequests = longAny(
                pluginUsage,
                "webSearchRequestCount",
                "webSearchCount",
                "web_search_request_count"
            )

            val auxiliaryMetrics = mutableListOf<WidgetData.DisplayMetric>()
            currentMonthCost?.let {
                auxiliaryMetrics.add(
                    WidgetData.DisplayMetric("本月消耗", "$symbol${formatMoney(it)}")
                )
            }
            totalCost?.let {
                auxiliaryMetrics.add(
                    WidgetData.DisplayMetric("累计消耗", "$symbol${formatMoney(it)}")
                )
            }
            totalRequests?.let {
                auxiliaryMetrics.add(
                    WidgetData.DisplayMetric("累计请求", "${formatCount(it)}次")
                )
            }
            totalTokens?.let {
                auxiliaryMetrics.add(
                    WidgetData.DisplayMetric("累计 Token", formatCount(it))
                )
            }
            inputTokens?.let {
                auxiliaryMetrics.add(
                    WidgetData.DisplayMetric("输入 Token", formatCount(it))
                )
            }
            outputTokens?.let {
                auxiliaryMetrics.add(
                    WidgetData.DisplayMetric("输出 Token", formatCount(it))
                )
            }
            cacheTokens?.let {
                auxiliaryMetrics.add(
                    WidgetData.DisplayMetric("缓存 Token", formatCount(it))
                )
            }
            webSearchRequests?.let {
                auxiliaryMetrics.add(
                    WidgetData.DisplayMetric("Web 搜索", "${formatCount(it)}次")
                )
            }
            longAny(rateLimit, "rpm")?.let {
                auxiliaryMetrics.add(WidgetData.DisplayMetric("RPM 上限", formatCount(it)))
            }
            longAny(rateLimit, "tpm")?.let {
                auxiliaryMetrics.add(WidgetData.DisplayMetric("TPM 上限", formatCount(it)))
            }
            longAny(rateLimit, "queryTpm", "query_tpm")?.let {
                auxiliaryMetrics.add(WidgetData.DisplayMetric("查询 TPM", formatCount(it)))
            }
            longAny(rateLimit, "concurrency")?.let {
                auxiliaryMetrics.add(WidgetData.DisplayMetric("并发上限", formatCount(it)))
            }

            if (
                totalRequests == null &&
                totalTokens == null &&
                totalCost == null &&
                currentMonthCost == null &&
                auxiliaryMetrics.isEmpty()
            ) {
                null
            } else {
                UsageSummary(
                    auxiliaryMetrics = auxiliaryMetrics.distinctBy { "${it.label}|${it.value}" },
                    totalRequests = totalRequests,
                    totalTokens = totalTokens,
                    totalCost = totalCost,
                    currency = currencyRaw
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
        return balanceData.copy(
            usageMetrics = emptyList(),
            auxiliaryMetrics = (balanceData.auxiliaryMetrics + summary.auxiliaryMetrics)
                .distinctBy { "${it.label}|${it.value}" },
            statusText = "MiMo 余额和用量已同步"
        )
    }

    private fun extractCookieValue(cookie: String, name: String): String? {
        return cookie.split(';')
            .asSequence()
            .map { it.trim() }
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) null
                else part.substring(0, separator).trim() to part.substring(separator + 1).trim()
            }
            .firstOrNull { (key, value) -> key == name && value.isNotBlank() }
            ?.second
    }

    private fun decimalAny(obj: JSONObject?, vararg keys: String): BigDecimal? {
        if (obj == null) return null
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            obj.opt(key)?.toString()?.toBigDecimalOrNull()?.let { return it }
        }
        return null
    }

    private fun longAny(obj: JSONObject?, vararg keys: String): Long? {
        return decimalAny(obj, *keys)?.toLong()
    }

    private fun stringAny(obj: JSONObject?, vararg keys: String): String {
        if (obj == null) return ""
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val value = obj.optString(key, "").trim()
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun currencySymbol(currency: String): String {
        return when (currency.trim().uppercase()) {
            "CNY", "RMB", "¥" -> "¥"
            "USD", "$" -> "$"
            else -> currency.trim().takeIf { it.isNotBlank() }?.plus(" ") ?: ""
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
            absolute >= 1_000_000_000 -> compact(value, 1_000_000_000.0, "B")
            absolute >= 1_000_000 -> compact(value, 1_000_000.0, "M")
            absolute >= 1_000 -> compact(value, 1_000.0, "K")
            else -> value.toString()
        }
    }

    private fun compact(value: Long, divisor: Double, suffix: String): String {
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
        private const val PLATFORM_BASE = "https://platform.xiaomimimo.com"
        private const val BALANCE_URL = "$PLATFORM_BASE/api/v1/balance"
        private const val USAGE_URL = "$PLATFORM_BASE/api/v1/usage"
        private const val HOST_REQUEST_INTERVAL_MS = 500L
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125 Mobile Safari/537.36"
    }
}
