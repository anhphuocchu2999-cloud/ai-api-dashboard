package com.java.myapplication.adapter

import com.java.myapplication.adapter.auth.BackgroundAuthFactory
import com.java.myapplication.adapter.auth.BackgroundAuthType
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * 爱黄牛中转站适配器
 * 支持余额查询：GET /api/v1/user/profile
 * 支持用量查询：GET /api/v1/usage
 * 需要后台 Bearer Token 授权
 */
class AihuangniuAdapter(
    private val backgroundBearerToken: String? = null
) : PlatformAdapter {

    override val platformName: String = "Aihuangniu"

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

    override fun fetchData(apiBase: String, apiKey: String, modelName: String?): WidgetData {
        val normalizedBase = apiBase.trim().trimEnd('/')

        if (apiKey.isBlank()) {
            return WidgetData.error(platformName, "Key未配置")
        }

        // 1. 获取用户资料（余额等）——使用后台 Bearer Token
        val profileData = fetchProfileData(normalizedBase, backgroundBearerToken ?: apiKey)

        // 2. 获取 /v1/usage 累计统计——使用模型 API Key
        val effectiveModelName = modelName?.takeIf { it.isNotBlank() } ?: profileData.modelName
        val usageCumulative = fetchUsageCumulative(normalizedBase, apiKey, effectiveModelName)

        android.util.Log.d("AihuangniuAdapter", "累计统计: requests=${usageCumulative?.first}, tokens=${usageCumulative?.second}, cost=${usageCumulative?.third}")

        // 3. 合并结果
        return if (profileData.isSuccess) {
            WidgetData(
                platformName = platformName,
                modelName = effectiveModelName,
                primaryMetric = profileData.primaryMetric,
                usageMetrics = emptyList(), // Core 2 由 Provider 本地快照生成
                percentage = profileData.percentage,
                percentageLabel = profileData.percentageLabel,
                auxiliaryMetrics = profileData.auxiliaryMetrics,
                statusText = profileData.statusText,
                isSuccess = true,
                displayLabel = profileData.displayLabel,
                total = profileData.total,
                used = profileData.used,
                remaining = profileData.remaining,
                usagePercent = profileData.usagePercent,
                isAvailable = true,
                cumulativeUsageRequests = usageCumulative?.first,
                cumulativeUsageTokens = usageCumulative?.second,
                cumulativeUsageActualCost = usageCumulative?.third
            )
        } else {
            profileData
        }
    }

    /**
     * 获取用户资料（余额等）
     */
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
     * 获取 /v1/usage 累计统计（使用模型 API Key）
     * 返回: Triple(requests, total_tokens, actual_cost_string)
     */
    private fun fetchUsageCumulative(apiBase: String, modelApiKey: String, modelName: String?): Triple<Long, Long, String>? {
        if (modelName.isNullOrBlank()) {
            android.util.Log.d("AihuangniuAdapter", "modelName为空，跳过/v1/usage查询")
            return null
        }

        val url = "$apiBase/v1/usage"
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            // 使用模型 API Key（sk-xxx）而非后台 Bearer Token
            conn.setRequestProperty("Authorization", "Bearer ${modelApiKey.trim()}")

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                android.util.Log.d("AihuangniuAdapter", "/v1/usage 返回 $responseCode")
                conn.disconnect()
                return null
            }

            val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            parseUsageCumulative(responseBody, modelName)
        } catch (e: Exception) {
            android.util.Log.d("AihuangniuAdapter", "/v1/usage 请求异常: ${e.javaClass.simpleName}")
            null
        }
    }

    /**
     * 解析 /v1/usage 累计统计
     */
    private fun parseUsageCumulative(response: String, modelName: String): Triple<Long, Long, String>? {
        return try {
            val root = JSONObject(response)
            val modelStats = root.optJSONArray("model_stats")
            if (modelStats == null || modelStats.length() == 0) {
                return null
            }

            for (i in 0 until modelStats.length()) {
                val item = modelStats.getJSONObject(i)
                val model = item.optString("model", "")
                if (model == modelName) {
                    val requests = item.optLong("requests", -1).takeIf { it >= 0 } ?: return null
                    val totalTokens = item.optLong("total_tokens", -1).takeIf { it >= 0 } ?: return null
                    val actualCost = item.optDouble("actual_cost", -1.0).takeIf { it >= 0 } ?: return null

                    // 使用 BigDecimal 避免浮点误差
                    val costStr = java.math.BigDecimal(actualCost.toString())
                        .setScale(6, java.math.RoundingMode.HALF_UP)
                        .stripTrailingZeros()
                        .toPlainString()

                    return Triple(requests, totalTokens, costStr)
                }
            }
            null
        } catch (e: Exception) {
            android.util.Log.d("AihuangniuAdapter", "解析 /v1/usage 异常: ${e.javaClass.simpleName}")
            null
        }
    }

    /**
     * 获取单页用量数据
     */
    private fun fetchUsagePage(apiBase: String, apiKey: String, page: Int, pageSize: Int): List<UsageRecord> {
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

    /**
     * 解析用量响应
     */
    private fun parseUsageResponse(response: String): List<UsageRecord> {
        return try {
            val root = JSONObject(response)
            val data = root.optJSONObject("data")
            val items = data?.optJSONArray("items")

            if (items == null || items.length() == 0) {
                return emptyList()
            }

            val records = mutableListOf<UsageRecord>()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val model = item.optString("model", "")
                val createdAt = item.optString("created_at", "")
                val inputTokens = item.optInt("input_tokens", 0).coerceAtLeast(0)
                val outputTokens = item.optInt("output_tokens", 0).coerceAtLeast(0)
                val actualCost = item.optDouble("actual_cost", 0.0).coerceAtLeast(0.0)

                if (model.isNotBlank() && createdAt.isNotBlank()) {
                    val timestamp = parseTimestamp(createdAt)
                    if (timestamp > 0) {
                        records.add(UsageRecord(
                            model = model,
                            timestamp = timestamp,
                            inputTokens = inputTokens,
                            outputTokens = outputTokens,
                            actualCost = actualCost
                        ))
                    }
                }
            }
            records
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 解析时间戳（处理 ISO 8601 格式，截断小数秒）
     */
    private fun parseTimestamp(createdAt: String): Long {
        return try {
            // 截断超过3位的小数秒
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
            val date = sdf.parse(normalized)
            date?.time ?: 0L
        } catch (_: Exception) {
            // 尝试不带小数秒的格式
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdf.parse(createdAt)
                date?.time ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
    }

    /**
     * 聚合用量数据为时间窗口指标
     */
    private fun aggregateUsageMetrics(records: List<UsageRecord>): List<WidgetData.DisplayMetric> {
        val now = System.currentTimeMillis()
        val metrics = mutableListOf<WidgetData.DisplayMetric>()

        // 时间窗口（毫秒）
        val windows = listOf(
            Pair("近1分钟", 60_000L),
            Pair("近10分钟", 600_000L),
            Pair("近30分钟", 1_800_000L),
            Pair("近12小时", 43_200_000L),
            Pair("近24小时", 86_400_000L)
        )

        for ((label, windowMs) in windows) {
            val cutoff = now - windowMs
            val windowRecords = records.filter { rec -> rec.timestamp >= cutoff }

            if (windowRecords.isEmpty()) {
                continue
            }

            val callCount = windowRecords.size
            val totalTokens = windowRecords.sumOf { rec -> (rec.inputTokens + rec.outputTokens).toLong() }
            val totalCost = windowRecords.sumOf { rec -> rec.actualCost }

            // 调用次数
            metrics.add(WidgetData.DisplayMetric("${label}调用", "${callCount}次"))

            // Token 消耗
            if (totalTokens > 0) {
                metrics.add(WidgetData.DisplayMetric("${label}Token", formatTokenCount(totalTokens)))
            }

            // 金额消耗
            if (totalCost > 0) {
                metrics.add(WidgetData.DisplayMetric("${label}消费", formatCost(totalCost)))
            }
        }

        return metrics
    }

    /**
     * 格式化 Token 数量
     */
    private fun formatTokenCount(count: Long): String {
        return when {
            count >= 100_000_000 -> "${count / 100_000_000}亿${(count % 100_000_000) / 10_000_000}千万"
            count >= 10_000 -> "${count / 10_000}万${(count % 10_000) / 1000}千"
            else -> "%,d".format(count)
        }
    }

    /**
     * 格式化金额
     */
    private fun formatCost(cost: Double): String {
        return when {
            cost >= 1.0 -> "¥%.2f".format(cost)
            cost >= 0.001 -> "¥%.4f".format(cost).trimEnd('0').trimEnd('.')
            else -> "¥%.6f".format(cost).trimEnd('0').trimEnd('.')
        }
    }

    /**
     * 解析用户资料响应
     */
    private fun parseProfileResponse(response: String): WidgetData {
        return try {
            val root = JSONObject(response)
            val data = root.optJSONObject("data")
            val balance = data?.optDouble("balance", -1.0) ?: -1.0

            if (balance < 0) {
                return WidgetData.error(platformName, "返回格式错误")
            }

            // 解析可选辅助字段
            val totalRecharged = data?.optDouble("total_recharged", -1.0)?.takeIf { it >= 0 }
            val concurrency = data?.optInt("concurrency", -1)?.takeIf { it >= 0 }
            val status = data?.optString("status", null)
            val lastActiveAt = data?.optString("last_active_at", null)

            // 计算百分比：余额 ÷ 累计充值 × 100%
            val percentage: Double? = if (totalRecharged != null && totalRecharged > 0) {
                (balance / totalRecharged * 100)
            } else null

            // 构建 auxiliaryMetrics
            val auxList = mutableListOf<WidgetData.DisplayMetric>()
            if (totalRecharged != null) {
                auxList.add(WidgetData.DisplayMetric("累计充值", "${WidgetData.formatNumber(totalRecharged)} ¥"))
            }
            if (concurrency != null) {
                auxList.add(WidgetData.DisplayMetric("并发", "${concurrency}"))
            }
            if (!status.isNullOrBlank()) {
                auxList.add(WidgetData.DisplayMetric("状态", status))
            }
            if (!lastActiveAt.isNullOrBlank()) {
                auxList.add(WidgetData.DisplayMetric("活跃", lastActiveAt.substring(0, minOf(10, lastActiveAt.length))))
            }

            WidgetData(
                platformName = platformName,
                modelName = null,
                primaryMetric = WidgetData.DisplayMetric("余额", "${WidgetData.formatNumber(balance)} ¥"),
                usageMetrics = emptyList(),
                percentage = percentage?.let { (it + 0.5).toInt() },
                percentageLabel = percentage?.let { "余额占充值" },
                auxiliaryMetrics = auxList,
                statusText = "正常",
                isSuccess = true,
                displayLabel = "¥",
                total = balance,
                used = null,
                remaining = balance,
                usagePercent = null,
                isAvailable = true
            )
        } catch (e: Exception) {
            WidgetData.error(platformName, "数据解析失败")
        }
    }

    /**
     * 用量记录数据类
     */
    private data class UsageRecord(
        val model: String,
        val timestamp: Long,
        val inputTokens: Int,
        val outputTokens: Int,
        val actualCost: Double
    )
}
