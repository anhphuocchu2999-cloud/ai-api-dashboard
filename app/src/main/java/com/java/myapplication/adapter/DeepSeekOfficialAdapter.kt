package com.java.myapplication.adapter

import android.content.Context
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
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * DeepSeek 官方平台适配器。
 *
 * - API Key：读取官方余额；
 * - 平台账户：读取本月消耗、累计消耗、本月 Token、钱包和预计可用 Token；
 * - 本地：使用平台真实累计值计算近 1/6/12/24 小时消耗。
 */
class DeepSeekOfficialAdapter : PlatformAdapter {

    override val platformName: String = "DeepSeek"

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
            DataCapability.TOKENS
        )
    )

    override fun detect(apiBase: String, apiKey: String): Boolean {
        return ServiceHostMatcher.matches(apiBase, "api.deepseek.com")
    }

    override fun fetchData(request: AdapterRequest): WidgetData {
        val apiData = fetchApiBalance(
            apiBase = request.apiBase,
            apiKey = request.modelApiKey,
            modelName = request.modelName
        )

        if (
            request.backgroundAuthType != BackgroundAuthType.COOKIE ||
            request.backgroundCredential.isBlank()
        ) {
            return apiData
        }

        return when (val webResult = fetchWebSummary(request.backgroundCredential)) {
            is WebSummaryResult.Success -> {
                val summary = webResult.summary
                val merged = if (apiData.isSuccess && apiData.isAvailable) {
                    mergeWebSummary(apiData, summary)
                } else {
                    webSummaryOnly(request.modelName, summary, apiData.statusText)
                }
                RecentUsageTracker.apply(
                    context = DashboardApplication.appContextOrNull(),
                    identity = RecentUsageTracker.identity(
                        provider = platformName,
                        apiBase = request.apiBase,
                        apiKey = request.modelApiKey,
                        modelName = request.modelName
                    ),
                    data = merged,
                    cumulative = RecentUsageTracker.CumulativeUsage(
                        requests = null,
                        tokens = summary.cumulativeTokens,
                        cost = summary.cumulativeCost,
                        currency = summary.currency
                    )
                )
            }

            WebSummaryResult.AuthExpired -> if (apiData.isSuccess) {
                apiData.copy(statusText = "网页登录需重连")
            } else apiData
            WebSummaryResult.Unavailable -> apiData
        }
    }

    override fun fetchData(apiBase: String, apiKey: String, modelName: String?): WidgetData {
        return fetchApiBalance(apiBase, apiKey, modelName)
    }

    private fun fetchApiBalance(
        apiBase: String,
        apiKey: String,
        modelName: String?
    ): WidgetData {
        val normalizedBase = apiBase.trim().trimEnd('/').let {
            if (it.endsWith("/v1", ignoreCase = true)) it.dropLast(3) else it
        }
        if (apiKey.isBlank()) return WidgetData.error(platformName, "Key未配置")

        return try {
            val conn = URL("$normalizedBase/user/balance").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            val code = conn.responseCode
            if (code == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                parseBalanceResponse(body, modelName, normalizedBase, apiKey)
            } else {
                conn.disconnect()
                WidgetData.error(
                    platformName,
                    when (code) {
                        401 -> "Key无效"
                        403 -> "余额接口被拒绝"
                        404 -> "余额接口不存在"
                        429 -> "请求过于频繁"
                        in 500..599 -> "服务器错误"
                        else -> "请求失败 ($code)"
                    }
                )
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

    private fun parseBalanceResponse(
        response: String,
        configuredModelName: String?,
        normalizedBase: String,
        apiKey: String
    ): WidgetData {
        return try {
            val root = JSONObject(response)
            val available = root.optBoolean("is_available", false)
            val infos = root.optJSONArray("balance_infos")
            val info = infos?.optJSONObject(0)
                ?: return WidgetData.error(platformName, "余额解析失败")

            val currency = info.optString("currency", "")
            val symbol = currencySymbol(currency)
            val total = info.optString("total_balance", "").toBigDecimalOrNull()
                ?: return WidgetData.error(platformName, "余额解析失败")
            val granted = info.optString("granted_balance", "").toBigDecimalOrNull()
            val toppedUp = info.optString("topped_up_balance", "").toBigDecimalOrNull()

            val auxiliary = buildList {
                toppedUp?.let {
                    add(WidgetData.DisplayMetric("充值余额", "$symbol${formatAmount(it)}"))
                }
                granted?.let {
                    add(WidgetData.DisplayMetric("赠送余额", "$symbol${formatAmount(it)}"))
                }
                for (index in 1 until (infos?.length() ?: 0)) {
                    val extra = infos?.optJSONObject(index) ?: continue
                    val extraTotal = extra.optString("total_balance", "").toBigDecimalOrNull() ?: continue
                    val extraCurrency = extra.optString("currency", "")
                    add(WidgetData.DisplayMetric("余额 $extraCurrency", "${currencySymbol(extraCurrency)}${formatAmount(extraTotal)}"))
                }
            }

            WidgetData(
                platformName = platformName,
                modelName = configuredModelName?.trim()?.takeIf { it.isNotEmpty() },
                primaryMetric = WidgetData.DisplayMetric(
                    "余额",
                    "$symbol${formatAmount(total)}"
                ),
                usageMetrics = buildBalanceChangeMetrics(
                    normalizedBase = normalizedBase,
                    apiKey = apiKey,
                    currency = currency,
                    currencySymbol = symbol,
                    currentBalance = total
                ),
                auxiliaryMetrics = auxiliary,
                statusText = if (available) "正常" else "余额不足",
                isSuccess = true,
                displayLabel = symbol,
                total = total.toDouble(),
                remaining = total.toDouble(),
                isAvailable = true
            )
        } catch (_: Exception) {
            WidgetData.error(platformName, "余额解析失败")
        }
    }

    private fun fetchWebSummary(rawCredential: String): WebSummaryResult {
        val credential = parseWebCredential(rawCredential)
        if (credential.cookie.isBlank() && credential.accessToken.isBlank()) {
            return WebSummaryResult.AuthExpired
        }

        val refreshedToken = credential.cookie
            .takeIf { it.isNotBlank() }
            ?.let(::fetchCurrentAccessToken)
            .orEmpty()
        val accessToken = refreshedToken.ifBlank { credential.accessToken }

        if (accessToken.isBlank() && credential.cookie.isBlank()) {
            return WebSummaryResult.AuthExpired
        }
        return requestWebSummary(credential.cookie, accessToken)
    }

    private fun parseWebCredential(rawCredential: String): WebCredential {
        val trimmed = rawCredential.trim()
        if (!trimmed.startsWith("{")) {
            return WebCredential(cookie = trimmed, accessToken = "")
        }
        return try {
            val obj = JSONObject(trimmed)
            WebCredential(
                cookie = obj.optString("cookie", "").trim(),
                accessToken = obj.optString("accessToken", "").trim()
            )
        } catch (_: Exception) {
            WebCredential(cookie = trimmed, accessToken = "")
        }
    }

    private fun fetchCurrentAccessToken(cookie: String): String? {
        if (cookie.isBlank()) return null
        return try {
            val conn = URL(CURRENT_USER_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Cookie", cookie)
            applyWebHeaders(conn)
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            val code = conn.responseCode
            val body = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            conn.disconnect()

            if (code !in 200..299) return null
            val root = JSONObject(body)
            val data = root.optJSONObject("data") ?: return null
            if (root.optInt("code", -1) != 0 || data.optInt("biz_code", -1) != 0) {
                return null
            }
            data.optJSONObject("biz_data")
                ?.optString("token", "")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun requestWebSummary(cookie: String, accessToken: String): WebSummaryResult {
        return try {
            val conn = URL(WEB_SUMMARY_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            if (cookie.isNotBlank()) conn.setRequestProperty("Cookie", cookie)
            if (accessToken.isNotBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
            }
            applyWebHeaders(conn)
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            val code = conn.responseCode
            val body = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            conn.disconnect()

            when (code) {
                401, 403 -> WebSummaryResult.AuthExpired
                in 200..299 -> parseWebSummary(body)
                    ?.let(WebSummaryResult::Success)
                    ?: WebSummaryResult.Unavailable
                else -> WebSummaryResult.Unavailable
            }
        } catch (_: Exception) {
            WebSummaryResult.Unavailable
        }
    }

    private fun applyWebHeaders(conn: HttpURLConnection) {
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Referer", "https://platform.deepseek.com/usage")
        conn.setRequestProperty("Origin", "https://platform.deepseek.com")
        conn.setRequestProperty("User-Agent", MOBILE_USER_AGENT)
    }

    private fun parseWebSummary(response: String): WebSummary? {
        return try {
            val root = JSONObject(response)
            val data = root.optJSONObject("data") ?: return null
            if (root.optInt("code", -1) != 0 || data.optInt("biz_code", -1) != 0) {
                return null
            }
            val bizData = data.optJSONObject("biz_data") ?: return null

            val monthlyTokensAmount = bizData.optString("monthly_token_usage", "")
                .toBigDecimalOrNull()
            val monthlyCost = firstMoney(bizData.optJSONArray("monthly_costs"))
            val totalCost = firstMoney(bizData.optJSONArray("total_costs"))

            val auxiliary = mutableListOf<WidgetData.DisplayMetric>()

            monthlyCost?.let { (amount, currency) ->
                auxiliary.add(
                    WidgetData.DisplayMetric(
                        "网页·本月消耗",
                        "${currencySymbol(currency)}${formatAmount(amount)}"
                    )
                )
            }
            totalCost?.let { (amount, currency) ->
                auxiliary.add(
                    WidgetData.DisplayMetric(
                        "网页·累计消耗",
                        "${currencySymbol(currency)}${formatAmount(amount)}"
                    )
                )
            }
            monthlyTokensAmount?.let {
                auxiliary.add(
                    WidgetData.DisplayMetric("网页·本月 Token", formatTokenAmount(it))
                )
            }

            parseWalletArray(bizData.optJSONArray("normal_wallets"), "网页·充值余额")
                .forEach(auxiliary::add)
            parseWalletArray(bizData.optJSONArray("bonus_wallets"), "网页·赠送余额")
                .forEach(auxiliary::add)

            bizData.optString("total_available_token_estimation", "")
                .toBigDecimalOrNull()
                ?.let {
                    auxiliary.add(
                        WidgetData.DisplayMetric("网页·预计可用 Token", formatTokenAmount(it))
                    )
                }

            val cumulativeCost = totalCost ?: monthlyCost
            if (monthlyTokensAmount == null && cumulativeCost == null && auxiliary.isEmpty()) {
                null
            } else {
                WebSummary(
                    auxiliaryMetrics = auxiliary.distinctBy { "${it.label}|${it.value}" },
                    cumulativeTokens = monthlyTokensAmount?.toLong(),
                    cumulativeCost = cumulativeCost?.first,
                    currency = cumulativeCost?.second ?: "CNY"
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun firstMoney(array: JSONArray?): Pair<BigDecimal, String>? {
        if (array == null) return null
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val amount = item.optString("amount", "").toBigDecimalOrNull() ?: continue
            return amount to item.optString("currency", "CNY")
        }
        return null
    }

    private fun parseWalletArray(
        array: JSONArray?,
        label: String
    ): List<WidgetData.DisplayMetric> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val balance = item.optString("balance", "").toBigDecimalOrNull() ?: continue
                val symbol = currencySymbol(item.optString("currency", ""))
                add(WidgetData.DisplayMetric(label, "$symbol${formatAmount(balance)}"))
            }
        }
    }

    private fun mergeWebSummary(apiData: WidgetData, summary: WebSummary): WidgetData {
        return apiData.copy(
            usageMetrics = emptyList(),
            auxiliaryMetrics = (apiData.auxiliaryMetrics + summary.auxiliaryMetrics)
                .distinctBy { "${it.label}|${it.value}" },
            statusText = "网页账单已同步"
        )
    }

    private fun webSummaryOnly(
        modelName: String?,
        summary: WebSummary,
        apiError: String?
    ): WidgetData {
        val first = summary.auxiliaryMetrics.firstOrNull()
        return WidgetData(
            platformName = platformName,
            modelName = modelName,
            primaryMetric = first,
            auxiliaryMetrics = summary.auxiliaryMetrics.drop(1),
            statusText = "账户数据可用；API 余额失败${apiError?.let { "：$it" }.orEmpty()}",
            isSuccess = true,
            isAvailable = true
        )
    }

    private fun buildBalanceChangeMetrics(
        normalizedBase: String,
        apiKey: String,
        currency: String,
        currencySymbol: String,
        currentBalance: BigDecimal
    ): List<WidgetData.DisplayMetric> {
        val context = DashboardApplication.appContextOrNull() ?: return emptyList()
        val prefs = context.getSharedPreferences(SNAPSHOT_PREFS_NAME, Context.MODE_PRIVATE)
        val identity = snapshotIdentity(normalizedBase, apiKey)
        val balanceKey = "${identity}_balance"
        val currencyKey = "${identity}_currency"
        val timeKey = "${identity}_time"

        val previous = prefs.getString(balanceKey, null)?.toBigDecimalOrNull()
        val previousCurrency = prefs.getString(currencyKey, null)
        val previousTime = prefs.getLong(timeKey, -1L)
        val now = System.currentTimeMillis()

        fun save() {
            prefs.edit()
                .putString(balanceKey, currentBalance.toPlainString())
                .putString(currencyKey, currency.uppercase())
                .putLong(timeKey, now)
                .apply()
        }

        if (previous == null || previousTime < 0L || previousCurrency != currency.uppercase()) {
            save()
            return listOf(WidgetData.DisplayMetric("余额变化统计中…", ""))
        }

        val decrease = previous.subtract(currentBalance)
        val timeLabel = formatTimeDiff(now - previousTime)
        val metric = when {
            decrease > BigDecimal.ZERO -> WidgetData.DisplayMetric(
                "${timeLabel}余额净减少",
                "$currencySymbol${formatAmount(decrease)}"
            )
            decrease < BigDecimal.ZERO -> WidgetData.DisplayMetric(
                "${timeLabel}余额增加",
                "$currencySymbol${formatAmount(decrease.abs())}"
            )
            else -> WidgetData.DisplayMetric("${timeLabel}余额无变化～", "")
        }
        save()
        return listOf(metric)
    }

    private fun snapshotIdentity(normalizedBase: String, apiKey: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("${normalizedBase.trimEnd('/')}|$apiKey".toByteArray(Charsets.UTF_8))
        return bytes.take(12).joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun currencySymbol(currency: String): String {
        return when (currency.uppercase()) {
            "CNY" -> "¥"
            "USD" -> "$"
            else -> currency.uppercase().takeIf { it.isNotBlank() }?.plus(" ") ?: ""
        }
    }

    private fun formatAmount(value: BigDecimal): String {
        val rounded = value.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
        return if (rounded.scale() < 2) {
            rounded.setScale(2, RoundingMode.HALF_UP).toPlainString()
        } else {
            rounded.toPlainString()
        }
    }

    private fun formatTokenAmount(value: BigDecimal): String {
        val absolute = value.abs()
        val divisorAndSuffix = when {
            absolute >= BILLION -> BILLION to "B"
            absolute >= MILLION -> MILLION to "M"
            absolute >= THOUSAND -> THOUSAND to "K"
            else -> null
        }
        if (divisorAndSuffix == null) {
            return value.setScale(0, RoundingMode.HALF_UP).toPlainString()
        }
        val (divisor, suffix) = divisorAndSuffix
        val compact = value.divide(divisor, 2, RoundingMode.HALF_UP).stripTrailingZeros()
        return "${compact.toPlainString()}$suffix"
    }

    private fun formatTimeDiff(diffMs: Long): String {
        val minutes = diffMs.coerceAtLeast(0L) / 60_000L
        val hours = minutes / 60L
        val remainingMinutes = minutes % 60L
        return when {
            minutes < 1L -> "不到1分钟"
            hours == 0L -> "过去${minutes}分钟"
            remainingMinutes == 0L -> "过去${hours}小时"
            else -> "过去${hours}小时${remainingMinutes}分钟"
        }
    }

    private data class WebCredential(
        val cookie: String,
        val accessToken: String
    )

    private data class WebSummary(
        val auxiliaryMetrics: List<WidgetData.DisplayMetric>,
        val cumulativeTokens: Long?,
        val cumulativeCost: BigDecimal?,
        val currency: String
    )

    private sealed class WebSummaryResult {
        data class Success(val summary: WebSummary) : WebSummaryResult()
        data object AuthExpired : WebSummaryResult()
        data object Unavailable : WebSummaryResult()
    }

    companion object {
        private const val SNAPSHOT_PREFS_NAME = "deepseek_balance_snapshots"
        private const val CURRENT_USER_URL =
            "https://platform.deepseek.com/auth-api/v0/users/current"
        private const val WEB_SUMMARY_URL =
            "https://platform.deepseek.com/api/v0/users/get_user_summary"
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125 Mobile Safari/537.36"
        private val THOUSAND = BigDecimal("1000")
        private val MILLION = BigDecimal("1000000")
        private val BILLION = BigDecimal("1000000000")
    }
}
