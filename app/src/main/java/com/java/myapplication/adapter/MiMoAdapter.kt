package com.java.myapplication.adapter

import java.net.HttpURLConnection
import java.net.URL

/**
 * MiMo 平台适配器
 * 支持余额查询：GET https://platform.xiaomimimo.com/api/v1/balance
 * 认证方式：Cookie
 */
class MiMoAdapter : PlatformAdapter {

    override val platformName: String = "MiMo"

    override fun detect(apiBase: String, apiKey: String): Boolean {
        return try {
            val normalizedBase = apiBase.trim().trimEnd('/')
            val url = "$normalizedBase/api/v1/balance"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Cookie", apiKey)
            conn.setRequestProperty("Accept", "application/json")
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
        val targetUrl = "https://platform.xiaomimimo.com/api/v1/balance"
        val cleanCookie = apiKey.trim()

        return try {
            val conn = URL(targetUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            if (cleanCookie.isNotBlank()) {
                conn.setRequestProperty("Cookie", cleanCookie)
            }
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                parseBalanceResponse(responseBody, modelName)
            } else {
                conn.disconnect()
                val errorMessage = when (responseCode) {
                    401 -> "认证失败"
                    403 -> "访问被拒绝"
                    404 -> "接口不存在"
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
            android.util.Log.e("MiMoAdapter", "fetchData error: ${e.javaClass.simpleName}: ${e.message}", e)
            WidgetData.error(platformName, "未知错误 (${e.javaClass.simpleName})")
        }
    }

    /**
     * 解析余额接口返回的 JSON
     */
    private fun parseBalanceResponse(response: String, modelName: String?): WidgetData {
        return try {
            // 安全解析数字字段（可能是字符串或数字）
            val balance = extractJsonValue(response, "balance")?.toDoubleOrNull() ?: 0.0
            val frozenBalance = extractJsonValue(response, "frozenBalance")?.toDoubleOrNull() ?: 0.0
            val overdraftLimit = extractJsonValue(response, "overdraftLimit")?.toDoubleOrNull() ?: 0.0
            val remainingOverdraftLimit = extractJsonValue(response, "remainingOverdraftLimit")?.toDoubleOrNull() ?: 0.0
            val giftBalance = extractJsonValue(response, "giftBalance")?.toDoubleOrNull() ?: 0.0
            val cashBalance = extractJsonValue(response, "cashBalance")?.toDoubleOrNull() ?: 0.0

            // 计算赠送占比
            val percentage = if (balance > 0) {
                ((giftBalance / balance) * 100).toInt().coerceIn(0, 100)
            } else null

            // 构建 Core 1
            val primaryMetric = WidgetData.DisplayMetric(
                "余额",
                "¥${WidgetData.formatNumber(balance)}"
            )

            // 构建辅助数据列表
            val auxiliaryList = mutableListOf<WidgetData.DisplayMetric>()

            // 始终加入赠送余额
            auxiliaryList.add(WidgetData.DisplayMetric("赠送余额", "¥${WidgetData.formatNumber(giftBalance)}"))

            // 以下字段仅在大于 0 时加入
            if (cashBalance > 0) {
                auxiliaryList.add(WidgetData.DisplayMetric("现金余额", "¥${WidgetData.formatNumber(cashBalance)}"))
            }
            if (frozenBalance > 0) {
                auxiliaryList.add(WidgetData.DisplayMetric("冻结余额", "¥${WidgetData.formatNumber(frozenBalance)}"))
            }
            if (remainingOverdraftLimit > 0) {
                auxiliaryList.add(WidgetData.DisplayMetric("可用透支", "¥${WidgetData.formatNumber(remainingOverdraftLimit)}"))
            }

            WidgetData(
                platformName = platformName,
                modelName = modelName,
                // 第2层：Core 1 固定核心指标
                primaryMetric = primaryMetric,
                // 第3层：Core 2 动态轮播位①
                usageMetrics = emptyList(),
                // 第4层：百分比视觉层
                percentage = percentage,
                percentageLabel = "赠送占比",
                // 第5层：B级辅助轮播位②
                auxiliaryMetrics = auxiliaryList,
                // 状态
                statusText = "正常",
                isSuccess = true,
                // 兼容旧字段
                total = balance,
                used = null,
                remaining = balance,
                isAvailable = true
            )
        } catch (e: Exception) {
            WidgetData.error(platformName, "数据解析失败")
        }
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
        if (i >= json.length) return null
        val endIdx = when {
            json[valueStart] == '"' -> {
                // 字符串值：找到匹配的结束引号
                json.indexOf('"', i)
            }
            else -> {
                // 数字值：找到 , } ]
                var end = i
                while (end < json.length && json[end] !in setOf(',', '}', ']')) end++
                end
            }
        }
        return if (endIdx > i) json.substring(i, endIdx).trim().removeSurrounding("\"") else null
    }
}