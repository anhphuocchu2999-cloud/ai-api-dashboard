package com.java.myapplication.adapter

import android.content.Context
import com.java.myapplication.DashboardApplication
import com.java.myapplication.adapter.auth.BackgroundAuthType
import com.java.myapplication.adapter.capability.DataCapability
import com.java.myapplication.adapter.capability.DataSourceType
import com.java.myapplication.adapter.capability.ProviderCapabilityProfile
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

/**
 * DeepSeek 官方平台适配器。
 *
 * 已验证的官方账户接口只有 GET /user/balance。
 * “近期余额变化”通过两次成功余额快照的真实差值计算：
 * - 余额下降：显示“余额净减少”
 * - 余额上升：显示“余额增加”（不误报为负消费）
 *
 * 该差值不是 DeepSeek 官方消费明细；充值、赠送、退款也可能影响余额。
 */
class DeepSeekOfficialAdapter : PlatformAdapter {

    override val platformName: String = "DeepSeek"

    override val capabilityProfile = ProviderCapabilityProfile(
        modelApiKeyRequired = true,
        backgroundAuthType = BackgroundAuthType.NONE,
        sources = setOf(DataSourceType.API),
        capabilities = setOf(
            DataCapability.MODELS,
            DataCapability.BALANCE
        )
    )

    override fun detect(apiBase: String, apiKey: String): Boolean {
        return apiBase.contains("api.deepseek.com", ignoreCase = true)
    }

    override fun fetchData(apiBase: String, apiKey: String, modelName: String?): WidgetData {
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
            val totalBalanceRaw = balanceInfo.optString("total_balance", "")
            val grantedBalanceRaw = balanceInfo.optString("granted_balance", "")
            val toppedUpBalanceRaw = balanceInfo.optString("topped_up_balance", "")

            val totalBalance = totalBalanceRaw.toBigDecimalOrNull()
                ?: return WidgetData.error(platformName, "余额解析失败")
            val grantedBalance = grantedBalanceRaw.toBigDecimalOrNull()
            val toppedUpBalance = toppedUpBalanceRaw.toBigDecimalOrNull()
            val currencySymbol = currencySymbol(currency)

            val auxiliaryMetrics = mutableListOf<WidgetData.DisplayMetric>()
            if (grantedBalance != null) {
                auxiliaryMetrics.add(
                    WidgetData.DisplayMetric(
                        "赠送余额",
                        "$currencySymbol${formatAmount(grantedBalance)}"
                    )
                )
            }
            if (toppedUpBalance != null) {
                auxiliaryMetrics.add(
                    WidgetData.DisplayMetric(
                        "充值余额",
                        "$currencySymbol${formatAmount(toppedUpBalance)}"
                    )
                )
            }

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

    /**
     * 用真实余额快照计算两次成功刷新之间的余额变化。
     * API Key 只参与 SHA-256 指纹计算，不保存原文，也不写日志。
     */
    private fun buildBalanceChangeMetrics(
        normalizedBase: String,
        apiKey: String,
        currency: String,
        currencySymbol: String,
        currentBalance: BigDecimal
    ): List<WidgetData.DisplayMetric> {
        val context = DashboardApplication.appContextOrNull()
            ?: return emptyList()
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

    private fun formatTimeDiff(diffMs: Long): String {
        val minutes = (diffMs.coerceAtLeast(0L)) / 60000L
        val hours = minutes / 60L
        val remainingMinutes = minutes % 60L
        return when {
            minutes < 1L -> "不到1分钟"
            hours == 0L -> "过去${minutes}分钟"
            remainingMinutes == 0L -> "过去${hours}小时"
            else -> "过去${hours}小时${remainingMinutes}分钟"
        }
    }

    companion object {
        private const val SNAPSHOT_PREFS_NAME = "deepseek_balance_snapshots"
    }
}
