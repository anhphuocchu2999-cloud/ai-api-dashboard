package com.java.myapplication.adapter

import android.content.Context
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
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * DeepSeek 官方平台适配器。
 *
 * 数据来源：
 * 1. API Key：GET /user/balance，读取官方余额；
 * 2. 平台账户：先用网页登录 Cookie 获取临时访问 Token，再读取
 *    /api/v0/users/get_user_summary 的 Token、累计消耗和钱包余额；
 * 3. 服务端累计值由 RecentUsageTracker 在本机计算近 1/6/12/24 小时消耗。
 *
 * 网页汇总接口失效时仍保留 API Key 余额，不用网页失败覆盖真实余额。
 * 访问 Token 只作为内部凭据使用，不输出到日志或界面。
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
        return apiBase.contains("api.deepseek.com", ignoreCase = true)
    }

    override fun fetchData(request: AdapterRequest): WidgetData {
        val apiData = fetchApiBalance(
            apiBase = request.apiBase,
            apiKey = request.modelApiKey,
            modelName = request.modelName
        )

        if (!apiData.isSuccess ||
            request.backgroundAuthType != BackgroundAuthType.COOKIE ||
            request.backgroundCredential.isBlank()
        ) {
            return apiData
        }

        return when (val webResult = fetchWebSummary(request.backgroundCredential)) {
            is WebSummaryResult.Success -> {
                val merged = mergeWebSummary(apiData, webResult.summary)
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
                        tokens = webResult.summary.cumulativeTokens,
                        cost = webResult.summary.cumulativeCost,
                        currency = webResult.summary.currency
                    )
                )
            }
            WebSummaryResult.AuthExpired -> apiData.copy(statusText = "网页登录需重连")
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
        val normalizedBase = apiBase.trim().trimEnd('/').removeSuffix("/v1")

        if (apiKey.isBlank()) {
            return WidgetData.error(platformName, "Key未配置")
        }

        val url = "$normalizedBase/user/balance"

        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                parseBalanceResponse(
                    response = responseBody,
                    configuredModelName = modelName,
                    normalizedBase = normalizedBase,
                    apiKey = apiKey
                )
            } else {
                conn.disconnect()
                val errorMessage = when (responseCode) {
                    401 -> "Key无效"
                    403 -> "余额接口被拒绝"
                    404 -> "余额接口不存在"
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

    private fun parseBalanceResponse(
        response: String,
        configuredModelName: String?,
        normalizedBase: String,
        apiKey: String
    ): WidgetData {
        return try {
            val root = JSONObject(response)
            val isAvailable = root.optBoolean("is_available", false)
            val effectiveModelName = configuredModelName?.trim()?.takeIf { it.isNotEmpty() }
            val balanceInfos = root.optJSONArray("balance_infos")

            if (balanceInfos == null || balanceInfos.length() == 0) {
                return WidgetData.error(platformName, "余额解析失败")
            }

            val balanceInfo = balanceInfos.getJSONObject(0)
            val currency = balanceInfo.optString("currency", "")
            val totalBalance = balanceInfo.optString("total_balance", "").toBigDecimalOrNull()
                ?: return WidgetData.error(platformName, "余额解析失败")
            val grantedBalance = balanceInfo.optString("granted_balance", "").toBigDecimalOrNull()
            val toppedUpBalance = balanceInfo.optString("topped_up_balance", "").toBigDecimalOrNull()
            val currencySymbol = currencySymbol(currency)

            val auxiliaryMetrics = mutableListOf<WidgetData.DisplayMetric>()
            toppedUpBalance?.let {
                auxiliaryMetrics.add(
                    WidgetData.DisplayMetric("充值余额", "$currencySymbol${formatAmount(it)}")
                )
            }
            grantedBalance?.let {
                auxiliaryMetrics.add(
                    WidgetData.DisplayMetric("赠送余额", "$currencySymbol${formatAmount(it)}")
                )
            }

            // API-only 时保留明确标注的余额变化；平台账户成功后会被近期消耗替换。
            val balanceChangeMetrics = buildBalanceChangeMetrics(
                normalizedBase = normalizedBase,
                apiKey = apiKey,
                currency = currency,
                currencySymbol = currencySymbol,
                currentBalance = totalBalance
            )

            WidgetData(
                platformName = platformName,
                modelName = effectiveModelName,
                primaryMetric = WidgetData.DisplayMetric(
                    "余额",
                    "$currencySymbol${formatAmount(totalBalance)}"
                ),
                usageMetrics = balanceChangeMetrics,
                percentage = null,
                percentageLabel = null,
                auxiliaryMetrics = auxiliaryMetrics,
                statusText = if (isAvailable) "正常" else "余额不足",
                isSuccess = true,
                displayLabel = currencySymbol,
                total = totalBalance.toDouble(),
                used = null,
                remaining = totalBalance.toDouble(),
                usagePercent = null,
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

        val refreshedToken = credential.cookie.takeIf { it.isNotBlank() }
            ?.let(::fetchCurrentAccessToken)
            .orEmpty()
        val accessToken = refreshedToken.ifBlank { credential.accessToken }

        if (accessToken.isBlank() && credential.cookie.isBlank()) {
            return WebSummaryResult.AuthExpired
        }

        return requestWebSummary(
            cookie = credential.cookie,
            accessToken = accessToken
        )
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
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Referer", "https://platform.deepseek.com/usage")
            conn.setRequestProperty("Origin", "https://platform.deepseek.com")
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

            if (responseCode !in 200..299) return null
            val root = JSONObject(responseBody)
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
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Referer", "https://platform.deepseek.com/usage")
            conn.setRequestProperty("Origin", "https://platform.deepseek.com")
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
                401, 403 -> WebSummaryResult.AuthExpired
                in 200..299 -> {
                    val summary = parseWebSummary(responseBody)
                    if (summary != null) {
                        WebSummaryResult.Success(summary)
                    } else {
                        WebSummaryResult.AuthExpired
                    }
                }
                else -> WebSummaryResult.Unavailable
            }
        } catch (_: Exception) {
            WebSummaryResult.Unavailable
        }
    }

    private fun parseWebSummary(response: String): WebSummary? {
        return try {
            val root = JSONObject(response)
            val data = root.optJSONObject("data") ?: return null
            if (root.optInt("code", -1) != 0 || data.optInt("biz_code", -1) != 0) {
                return null
            }
            val bizData = data.optJSONObject("biz_data") ?: return null

            val monthlyTokens = bizData.optString("monthly_token_usage", "")
                .toBigDecimalOrNull()
                ?.toLong()

            val totalCost = firstMoney(bizData.optJSONArray("total_costs"))
                ?: firstMoney(bizData.optJSONArray("monthly_costs"))

            // 动态位②只放账户资源，不重复动态位①的近期消耗。
            val auxiliaryMetrics = mutableListOf<WidgetData.DisplayMetric>()
            parseWalletArray(bizData.optJSONArray("normal_wallets"), "充值余额")
                .forEach(auxiliaryMetrics::add)
            parseWalletArray(bizData.optJSONArray("bonus_wallets"), "赠送余额")
                .forEach(auxiliaryMetrics::add)

            bizData.optString("total_available_token_estimation", "")
                .toBigDecimalOrNull()
                ?.let { tokenEstimate ->
                    auxiliaryMetrics.add(
                        WidgetData.DisplayMetric(
                            "预计可用 Token",
                            formatTokenAmount(tokenEstimate)
                        )
                    )
                }

            if (monthlyTokens == null && totalCost == null && auxiliaryMetrics.isEmpty()) {
                null
            } else {
                WebSummary(
                    auxiliaryMetrics = auxiliaryMetrics,
                    cumulativeTokens = monthlyTokens,
                    cumulativeCost = totalCost?.first,
                    currency = totalCost?.second ?: "CNY"
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
            val currency = item.optString("currency", "CNY")
            return amount to currency
        }
        return null
    }

    private fun parseWalletArray(array: JSONArray?, label: String): List<WidgetData.DisplayMetric> {
        if (array == null) return emptyList()
        val result = mutableListOf<WidgetData.DisplayMetric>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val balance = item.optString("balance", "").toBigDecimalOrNull() ?: continue
            val symbol = currencySymbol(item.optString("currency", ""))
            result.add(WidgetData.DisplayMetric(label, "$symbol${formatAmount(balance)}"))
        }
        return result
    }

    private fun mergeWebSummary(apiData: WidgetData, summary: WebSummary): WidgetData {
        val mergedAuxiliary = (apiData.auxiliaryMetrics + summary.auxiliaryMetrics)
            .distinctBy { "${it.label}|${it.value}" }

        return apiData.copy(
            usageMetrics = emptyList(),
            auxiliaryMetrics = mergedAuxiliary,
            statusText = "网页账单已同步"
        )
    }

    /**
     * API-only 时用真实余额快照计算两次成功刷新之间的余额变化。
     * API Key 只参与 SHA-256 指纹计算，不保存原文，也不写日志。
     */
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

        val previousBalance = prefs.getString(balanceKey, null)?.toBigDecimalOrNull()
        val previousCurrency = prefs.getString(currencyKey, null)
        val previousTime = prefs.getLong(timeKey, -1L)
        val now = System.currentTimeMillis()

        fun saveCurrentSnapshot() {
            prefs.edit()
                .putString(balanceKey, currentBalance.toPlainString())
                .putString(currencyKey, currency.uppercase())
                .putLong(timeKey, now)
                .apply()
        }

        if (previousBalance == null || previousTime < 0L || previousCurrency != currency.uppercase()) {
            saveCurrentSnapshot()
            return listOf(WidgetData.DisplayMetric("余额变化统计中…", ""))
        }

        val netDecrease = previousBalance.subtract(currentBalance)
        val timeLabel = formatTimeDiff(now - previousTime)
        val metric = when {
            netDecrease > BigDecimal.ZERO -> WidgetData.DisplayMetric(
                "${timeLabel}余额净减少",
                "$currencySymbol${formatAmount(netDecrease)}"
            )
            netDecrease < BigDecimal.ZERO -> WidgetData.DisplayMetric(
                "${timeLabel}余额增加",
                "$currencySymbol${formatAmount(netDecrease.abs())}"
            )
            else -> WidgetData.DisplayMetric("${timeLabel}余额无变化～", "")
        }

        saveCurrentSnapshot()
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
        val minutes = diffMs.coerceAtLeast(0L) / 60000L
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
