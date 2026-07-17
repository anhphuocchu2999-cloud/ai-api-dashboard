package com.java.myapplication.adapter

import com.java.myapplication.adapter.auth.BackgroundAuthType
import com.java.myapplication.adapter.capability.DataCapability
import com.java.myapplication.adapter.capability.DataSourceType
import com.java.myapplication.adapter.capability.ProviderCapabilityProfile
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL

/**
 * New API 平台适配器（蜜音AI / OneAPI 等基于 NewAPI 的中转站）
 * 支持次数卡查询：GET /api/usage/token
 */
class NewApiAdapter : PlatformAdapter {

    override val platformName: String = "Kimi"

    override val capabilityProfile = ProviderCapabilityProfile(
        modelApiKeyRequired = true,
        backgroundAuthType = BackgroundAuthType.NONE,
        sources = setOf(DataSourceType.API, DataSourceType.BILLING),
        capabilities = setOf(
            DataCapability.MODELS,
            DataCapability.QUOTA,
            DataCapability.USAGE,
            DataCapability.REQUESTS
        )
    )

    override fun detect(apiBase: String, apiKey: String): Boolean {
        return try {
            val normalizedBase = apiBase.trim().trimEnd('/')
            val url = "$normalizedBase/api/usage/token"
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

    override fun fetchData(apiBase: String, apiKey: String, modelName: String?): WidgetData {
        val serviceRoot = normalizeServiceRoot(apiBase)

        if (apiKey.isBlank()) {
            return WidgetData.error(platformName, "Key未配置")
        }

        // API 次数卡与 Billing 是两条独立数据来源；任一成功都应尽量返回真实数据。
        val tokenData = fetchTokenData(serviceRoot, apiKey)
        waitForSameHostInterval()
        val billingSnapshot = fetchBillingSnapshot(serviceRoot, apiKey)

        return mergeTokenAndBilling(
            tokenData = tokenData,
            billingSnapshot = billingSnapshot,
            configuredModelName = modelName
        )
    }

    private fun fetchTokenData(serviceRoot: String, apiKey: String): WidgetData {
        val url = "$serviceRoot/api/usage/token"

        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                    parseTokenResponse(responseBody)
                } else {
                    val errorMessage = when (responseCode) {
                        401 -> "Key无效"
                        403 -> "访问被拒绝"
                        404 -> "接口不存在"
                        429 -> "请求过于频繁"
                        in 500..599 -> "服务器错误"
                        else -> "请求失败 ($responseCode)"
                    }
                    WidgetData.error(platformName, errorMessage)
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: java.net.SocketTimeoutException) {
            WidgetData.error(platformName, "连接超时")
        } catch (e: java.net.ConnectException) {
            WidgetData.error(platformName, "连接失败")
        } catch (e: java.net.UnknownHostException) {
            WidgetData.error(platformName, "域名解析失败")
        } catch (e: java.io.IOException) {
            WidgetData.error(platformName, "网络错误")
        } catch (_: Exception) {
            WidgetData.error(platformName, "未知错误")
        }
    }

    private fun fetchBillingSnapshot(serviceRoot: String, apiKey: String): BillingSnapshot? {
        val subscription = fetchJsonObject(
            "$serviceRoot/v1/dashboard/billing/subscription",
            apiKey
        )

        waitForSameHostInterval()

        val usage = fetchJsonObject(
            "$serviceRoot/v1/dashboard/billing/usage",
            apiKey
        )

        val softLimitRaw = readDecimal(subscription, "soft_limit_usd", "soft_limit")
        val totalUsageRaw = readDecimal(usage, "total_usage")

        return if (softLimitRaw == null && totalUsageRaw == null) {
            null
        } else {
            BillingSnapshot(
                softLimitRaw = softLimitRaw,
                totalUsageRaw = totalUsageRaw
            )
        }
    }

    private fun fetchJsonObject(url: String, apiKey: String): JSONObject? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                if (conn.responseCode != 200) {
                    null
                } else {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    JSONObject(body)
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun mergeTokenAndBilling(
        tokenData: WidgetData,
        billingSnapshot: BillingSnapshot?,
        configuredModelName: String?
    ): WidgetData {
        if (billingSnapshot == null) {
            return tokenData
        }

        // 中性 Billing 表达（无货币符号）
        val billingParts = mutableListOf<String>()
        billingSnapshot.softLimitRaw?.let {
            billingParts.add("额度 ${formatDecimal(it)}")
        }
        billingSnapshot.totalUsageRaw?.let {
            billingParts.add("用量 ${formatDecimal(it)}")
        }

        if (billingParts.isEmpty()) {
            return tokenData
        }

        val billingMetric = WidgetData.DisplayMetric(
            "Billing",
            billingParts.joinToString(" · ")
        )

        // 如果次数卡数据成功，将 Billing 作为辅助指标追加
        if (tokenData.isSuccess && tokenData.isAvailable) {
            return tokenData.copy(
                modelName = tokenData.modelName ?: configuredModelName,
                auxiliaryMetrics = listOf(billingMetric) + tokenData.auxiliaryMetrics
            )
        }

        // 如果次数卡数据失败，尝试仅用 Billing 构建主指标
        val primaryMetric = billingSnapshot.softLimitRaw?.let {
            WidgetData.DisplayMetric("Billing额度", formatDecimal(it))
        } ?: billingSnapshot.totalUsageRaw?.let {
            WidgetData.DisplayMetric("Billing用量", formatDecimal(it))
        }

        val auxiliaryMetrics = if (
            billingSnapshot.softLimitRaw != null && billingSnapshot.totalUsageRaw != null
        ) {
            listOf(
                WidgetData.DisplayMetric(
                    "Billing用量",
                    formatDecimal(billingSnapshot.totalUsageRaw)
                )
            )
        } else {
            emptyList()
        }

        return WidgetData(
            platformName = platformName,
            modelName = configuredModelName,
            primaryMetric = primaryMetric,
            auxiliaryMetrics = auxiliaryMetrics,
            statusText = "Billing 已同步",
            isSuccess = true,
            isAvailable = true
        )
    }

    private fun readDecimal(json: JSONObject?, vararg keys: String): BigDecimal? {
        if (json == null) return null

        for (key in keys) {
            if (!json.has(key) || json.isNull(key)) continue
            val value = json.opt(key)?.toString()?.trim()?.toBigDecimalOrNull()
            if (value != null) return value
        }
        return null
    }

    private fun formatDecimal(value: BigDecimal): String {
        return value
            .setScale(2, RoundingMode.HALF_UP)
            .toPlainString()
    }

    private fun normalizeServiceRoot(apiBase: String): String {
        val normalized = apiBase.trim().trimEnd('/')
        return if (normalized.endsWith("/v1", ignoreCase = true)) {
            normalized.dropLast(3).trimEnd('/')
        } else {
            normalized
        }
    }

    private fun waitForSameHostInterval() {
        try {
            Thread.sleep(500L)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private data class BillingSnapshot(
        val softLimitRaw: BigDecimal?,
        val totalUsageRaw: BigDecimal?
    )

    /**
     * 解析次数卡接口返回的 JSON
     * 格式：{ "code": true, "data": { ... }, "message": "ok" }
     * 
     * 返回 WidgetData，字段为 null 表示没有该数据
     */
    private fun parseTokenResponse(response: String): WidgetData {
        return try {
            val root = JSONObject(response)
            val code = when (val raw = root.opt("code")) {
                is Boolean -> raw
                is Number -> raw.toInt() != 0
                else -> raw?.toString()?.toBooleanStrictOrNull() ?: false
            }
            if (!code) {
                val message = root.optString("message", "请求失败")
                return WidgetData.error(platformName, message)
            }

            val data = root.optJSONObject("data")
                ?: return WidgetData.error(platformName, "返回格式错误")

            fun decimal(key: String) = data.opt(key)?.toString()?.toDoubleOrNull()
            val totalGranted = decimal("total_granted")
            val totalUsed = decimal("total_used")
            val totalAvailable = decimal("total_available")
            val callCount = data.opt("call_count")?.toString()?.toBigDecimalOrNull()?.toLong()
            val perCallQuota = decimal("per_call_quota")
            val perCallDisplayLabel = data.optString("per_call_display_label", "次").let(::cleanLabel)

            val modelLimits = data.opt("model_limits")
            val modelName = when (modelLimits) {
                is JSONObject -> modelLimits.keys().asSequence().firstOrNull()
                is org.json.JSONArray -> modelLimits.optString(0, "")
                is String -> modelLimits
                else -> null
            }?.let(::cleanLabel)?.takeIf { it.isNotBlank() }

            // 计算次数（如果有 perCallQuota）
            val total: Double?
            val used: Double?
            val remaining: Double?
            
            if (perCallQuota != null && perCallQuota > 0) {
                total = totalGranted?.let { (it / perCallQuota).toLong().toDouble().coerceAtLeast(0.0) }
                used = totalUsed?.let { (it / perCallQuota).toLong().toDouble().coerceAtLeast(0.0) }
                remaining = totalAvailable?.let { (it / perCallQuota).toLong().toDouble().coerceAtLeast(0.0) }
            } else {
                total = totalGranted?.coerceAtLeast(0.0)
                used = totalUsed?.coerceAtLeast(0.0)
                remaining = totalAvailable?.coerceAtLeast(0.0)
            }

            // 累计已用次数（使用 call_count，与 auxiliaryMetrics 中"已用 X 次"保持一致）
            val cumulativeUsedCalls = callCount

            // 计算使用率（已用比例）
            val usagePercent = if (totalGranted != null && totalGranted > 0 && totalUsed != null) {
                ((totalUsed * 100) / totalGranted).toInt().coerceIn(0, 100)
            } else null

            // 构建五层结构
            val primaryMetric = if (remaining != null && remaining >= 0) {
                WidgetData.DisplayMetric("剩余", "${WidgetData.formatNumber(remaining)} ${perCallDisplayLabel}")
            } else if (total != null && total >= 0) {
                WidgetData.DisplayMetric("总额", "${WidgetData.formatNumber(total)} ${perCallDisplayLabel}")
            } else null

            val auxiliaryList = mutableListOf<WidgetData.DisplayMetric>()
            if (used != null && used >= 0) {
                auxiliaryList.add(WidgetData.DisplayMetric("已用", "${WidgetData.formatNumber(used)} ${perCallDisplayLabel}"))
            }
            if (callCount != null && callCount >= 0) {
                auxiliaryList.add(WidgetData.DisplayMetric("调用", "${callCount} 次"))
            }

            WidgetData(
                platformName = platformName,
                modelName = modelName,
                // 第2层：Core 1 固定核心指标
                primaryMetric = primaryMetric,
                // 第3层：Core 2 动态轮播位①（当前无时间窗口数据，保持空）
                usageMetrics = emptyList(),
                // 第4层：百分比视觉层
                percentage = usagePercent?.let { 100 - it },  // 剩余比例
                percentageLabel = "剩余比例",
                // 第5层：B级辅助轮播位②
                auxiliaryMetrics = auxiliaryList,
                // 状态
                statusText = "正常",
                isSuccess = true,
                // 兼容旧字段
                displayLabel = perCallDisplayLabel,
                total = total,
                used = used,
                remaining = remaining,
                callCount = callCount,
                usagePercent = usagePercent,
                isAvailable = true,
                cumulativeUsedCalls = cumulativeUsedCalls
            )
        } catch (e: Exception) {
            WidgetData.error(platformName, "数据解析失败")
        }
    }

    /**
     * 解析 model_limits JSON 对象
     * 格式：{"kimi-k2.6": true, "kimi-k2.5": true}
     * 返回第一个 key
     */
    private fun parseModelLimits(modelLimitsJson: String): String {
        if (modelLimitsJson.isBlank()) return ""
        
        // 如果是对象格式 {"key": true}
        if (modelLimitsJson.startsWith("{")) {
            // 提取第一个 key
            var i = 1
            while (i < modelLimitsJson.length) {
                if (modelLimitsJson[i] == '"') {
                    val keyEnd = modelLimitsJson.indexOf('"', i + 1)
                    if (keyEnd != -1) {
                        return modelLimitsJson.substring(i + 1, keyEnd)
                    }
                }
                i++
            }
        }
        
        // 如果是数组格式 ["kimi-k2.6"]
        if (modelLimitsJson.startsWith("[")) {
            val end = modelLimitsJson.indexOf(']')
            if (end > 1) {
                val content = modelLimitsJson.substring(1, end)
                val first = content.split(",").firstOrNull()?.trim()?.trim('"')
                return first ?: ""
            }
        }
        
        // 简单字符串
        return modelLimitsJson.trim('"')
    }

    /**
     * 提取 data 对象内容
     */
    private fun extractDataObject(response: String): String? {
        val dataStart = response.indexOf("\"data\":")
        if (dataStart == -1) return null

        var braceCount = 0
        var dataObjStart = -1
        var dataObjEnd = -1
        for (i in dataStart + 7 until response.length) {
            if (response[i] == '{') {
                if (braceCount == 0) dataObjStart = i
                braceCount++
            } else if (response[i] == '}') {
                braceCount--
                if (braceCount == 0) {
                    dataObjEnd = i + 1
                    break
                }
            }
        }

        return if (dataObjStart != -1 && dataObjEnd != -1) {
            response.substring(dataObjStart, dataObjEnd)
        } else null
    }

    /**
     * 清理标签字符串中的多余引号
     */
    private fun cleanLabel(input: String): String {
        return input.trim()
            .removePrefix("\"")
            .removeSuffix("\"")
            .replace("\\\"", "\"")
            .trim()
    }

    /**
     * 从 JSON 中提取指定 key 的值
     */
    private fun extractJsonValue(json: String, key: String): String? {
        val keyStr = "\"$key\":"
        val idx = json.indexOf(keyStr)
        if (idx == -1) return null
        val valueStart = idx + keyStr.length
        var i = valueStart
        while (i < json.length && (json[i] == ' ' || json[i] == '"')) i++
        val endIdx = when {
            json[i] == '"' -> {
                val start = i + 1
                json.indexOf('"', start)
            }
            else -> {
                var end = i
                while (end < json.length && json[end] !in setOf(',', '}', ']')) end++
                end
            }
        }
        return if (endIdx > i) json.substring(i, endIdx).trim() else null
    }
}
