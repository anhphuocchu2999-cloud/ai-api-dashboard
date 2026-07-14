package com.java.myapplication.adapter

import java.net.HttpURLConnection
import java.net.URL

/**
 * DeepSeek 官方平台适配器
 * 支持余额查询：GET /user/balance
 */
class DeepSeekOfficialAdapter : PlatformAdapter {

    override val platformName: String = "DeepSeek"

    override fun detect(apiBase: String, apiKey: String): Boolean {
        return apiBase.contains("api.deepseek.com", ignoreCase = true)
    }

    override fun fetchData(apiBase: String, apiKey: String, modelName: String?): WidgetData {
        val normalizedBase = apiBase.trim().trimEnd('/').removeSuffix("/v1")
        
        // 检查 API Key 是否为空
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
                parseBalanceResponse(responseBody)
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
        } catch (e: java.net.SocketTimeoutException) {
            WidgetData.error(platformName, "连接超时")
        } catch (e: java.net.ConnectException) {
            WidgetData.error(platformName, "连接失败")
        } catch (e: java.net.UnknownHostException) {
            WidgetData.error(platformName, "域名解析失败")
        } catch (e: java.io.IOException) {
            WidgetData.error(platformName, "网络错误")
        } catch (e: Exception) {
            WidgetData.error(platformName, "未知错误")
        }
    }

    /**
     * 解析 DeepSeek 官方余额接口返回
     * 格式示例：
     * {
     *   "code": 0,
     *   "data": {
     *     "is_available": true,
     *     "balance_infos": [
     *       {
     *         "currency": "CNY",
     *         "total_balance": "123.45",
     *         "granted_balance": "100.00",
     *         "topped_up_balance": "23.45"
     *       }
     *     ]
     *   }
     * }
     */
    private fun parseBalanceResponse(response: String): WidgetData {
        return try {
            val root = org.json.JSONObject(response)
            val isAvailable = root.optBoolean("is_available", false)
            val balanceInfos = root.optJSONArray("balance_infos")

            if (balanceInfos != null && balanceInfos.length() > 0) {
                val balanceInfo = balanceInfos.getJSONObject(0)
                val currency = balanceInfo.optString("currency", "")
                val totalBalance = balanceInfo.optString("total_balance", "").toDoubleOrNull()
                val grantedBalance = balanceInfo.optString("granted_balance", "").toDoubleOrNull()
                val toppedUpBalance = balanceInfo.optString("topped_up_balance", "").toDoubleOrNull()

                if (totalBalance != null) {
                    val currencySymbol = when (currency.uppercase()) {
                        "CNY" -> "¥"
                        "USD" -> "$"
                        else -> ""
                    }

                    val displayLabel = if (isAvailable) "可用" else "余额不足"

                    // 构建 auxiliaryMetrics（真实可用字段）
                    val auxList = mutableListOf<WidgetData.DisplayMetric>()
                    if (grantedBalance != null && grantedBalance > 0) {
                        auxList.add(WidgetData.DisplayMetric("赠送", "${WidgetData.formatNumber(grantedBalance)} ${currencySymbol}"))
                    }
                    if (toppedUpBalance != null && toppedUpBalance > 0) {
                        auxList.add(WidgetData.DisplayMetric("充值", "${WidgetData.formatNumber(toppedUpBalance)} ${currencySymbol}"))
                    }

                    WidgetData(
                        platformName = platformName,
                        modelName = displayLabel,
                        // 第2层：Core 1 固定核心指标
                        primaryMetric = WidgetData.DisplayMetric("余额", "${WidgetData.formatNumber(totalBalance)} ${currencySymbol}"),
                        // 第3层：Core 2 动态轮播位①（当前无时间窗口数据，保持空）
                        usageMetrics = emptyList(),
                        // 第4层：百分比视觉层（当前无可靠分子分母，保持空）
                        percentage = null,
                        percentageLabel = null,
                        // 第5层：B级辅助轮播位②
                        auxiliaryMetrics = auxList,
                        // 状态
                        statusText = if (isAvailable) "正常" else "余额不足",
                        isSuccess = true,
                        // 兼容旧字段
                        displayLabel = currencySymbol,
                        total = totalBalance,
                        used = null,
                        remaining = totalBalance,
                        usagePercent = null,
                        isAvailable = true
                    )
                } else {
                    WidgetData.error(platformName, "余额解析失败")
                }
            } else {
                // balance_infos 不存在或为空
                WidgetData(
                    platformName = platformName,
                    modelName = "余额为空",
                    displayLabel = null,
                    total = null,
                    used = null,
                    remaining = null,
                    usagePercent = null,
                    isAvailable = true
                )
            }
        } catch (e: Exception) {
            WidgetData.error(platformName, "余额解析失败")
        }
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