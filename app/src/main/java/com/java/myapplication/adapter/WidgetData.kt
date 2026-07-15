package com.java.myapplication.adapter

import android.content.Context
import com.java.myapplication.DashboardApplication
import com.java.myapplication.config.InstanceKeyResolver
import org.json.JSONArray
import org.json.JSONObject

/**
 * 统一 Widget 数据模型。
 *
 * 每张卡片固定承载：
 * 1. 模型名称；
 * 2. 云端真实核心指标；
 * 3. 近期消耗；
 * 4. 百分比；
 * 5. 云端真实辅助指标。
 *
 * DisplayMetric 会在桌面上明确标记“云端”或“本地”。
 */
data class WidgetData(
    val platformName: String,
    val modelName: String? = null,
    val primaryMetric: DisplayMetric? = null,
    var usageMetrics: List<DisplayMetric> = emptyList(),
    val percentage: Int? = null,
    var percentageLabel: String? = null,
    val auxiliaryMetrics: List<DisplayMetric> = emptyList(),
    val statusText: String? = null,
    val isSuccess: Boolean = true,

    // 兼容旧字段
    val displayLabel: String? = null,
    val total: Double? = null,
    val used: Double? = null,
    val remaining: Double? = null,
    val callCount: Long? = null,
    val usagePercent: Int? = null,
    val isAvailable: Boolean = true,
    val errorMessage: String? = null,

    // Kimi 累计统计输入
    val cumulativeUsedCalls: Long? = null,

    // 爱黄牛历史兼容累计统计输入
    val cumulativeUsageRequests: Long? = null,
    val cumulativeUsageTokens: Long? = null,
    val cumulativeUsageActualCost: String? = null,

    // 当前是否展示最近一次成功缓存
    val isFallback: Boolean = false
) {

    init {
        // 百分比均由本机基于云端真实数字计算，避免误认为平台直接返回。
        percentageLabel = percentageLabel?.let { decorateLocalLabel(it) }

        // 不再由渲染层随机生成“正在排队”等假动态内容。
        // 有真实核心数据但暂时没有可计算的近期消耗时，只展示明确状态。
        if (
            isSuccess &&
            isAvailable &&
            !isFallback &&
            primaryMetric != null &&
            usageMetrics.isEmpty()
        ) {
            usageMetrics = listOf(
                DisplayMetric(
                    label = "近期消耗",
                    value = "暂无可计算数据",
                    source = MetricSource.STATUS
                )
            )
        }

        if (shouldPersistAsSuccessfulData()) {
            persistLastSuccessfulData()
        }
    }

    enum class MetricSource {
        AUTO,
        CLOUD,
        LOCAL,
        STATUS
    }

    /** 单个桌面展示指标。 */
    data class DisplayMetric(
        var label: String,
        val value: String,
        val source: MetricSource = MetricSource.AUTO
    ) {
        init {
            label = decorateMetricLabel(label, source)
        }

        companion object {
            private const val CLOUD_PREFIX = "云端·"
            private const val LOCAL_PREFIX = "本地·"

            private fun decorateMetricLabel(raw: String, source: MetricSource): String {
                val clean = raw.trim()
                if (clean.isBlank()) return clean
                if (clean.startsWith(CLOUD_PREFIX) || clean.startsWith(LOCAL_PREFIX)) return clean

                return when (resolveSource(clean, source)) {
                    MetricSource.CLOUD -> "$CLOUD_PREFIX$clean"
                    MetricSource.LOCAL -> "$LOCAL_PREFIX$clean"
                    MetricSource.STATUS,
                    MetricSource.AUTO -> clean
                }
            }

            private fun resolveSource(label: String, source: MetricSource): MetricSource {
                if (source != MetricSource.AUTO) return source

                return if (
                    label.startsWith("近") ||
                    label.startsWith("过去") ||
                    label.contains("统计中") ||
                    label.contains("统计基准") ||
                    label.contains("余额变化") ||
                    label.contains("余额净减少") ||
                    label.contains("余额增加") ||
                    label.contains("余额无变化")
                ) {
                    MetricSource.LOCAL
                } else {
                    MetricSource.CLOUD
                }
            }
        }
    }

    companion object {
        private const val CACHE_PREFS_NAME = "widget_last_success"
        private const val FALLBACK_MESSAGE = "网络有点抖，先看上次数据～"

        private val TRANSIENT_ERROR_KEYWORDS = listOf(
            "域名解析失败",
            "网络错误",
            "网络异常",
            "连接失败",
            "连接超时",
            "请求过于频繁",
            "服务器错误",
            "服务器异常",
            "获取失败"
        )

        fun error(platformName: String, message: String): WidgetData {
            if (isTransientError(message)) {
                val cached = loadLastSuccessfulData(platformName)
                if (cached != null) {
                    val markedAuxiliary = if (cached.auxiliaryMetrics.isEmpty()) {
                        listOf(DisplayMetric("", "😂", MetricSource.STATUS))
                    } else {
                        cached.auxiliaryMetrics.map { metric ->
                            metric.copy(value = "${metric.value} 😂")
                        }
                    }

                    return cached.copy(
                        usageMetrics = listOf(
                            DisplayMetric(FALLBACK_MESSAGE, "", MetricSource.STATUS)
                        ),
                        auxiliaryMetrics = markedAuxiliary,
                        statusText = FALLBACK_MESSAGE,
                        isSuccess = true,
                        isAvailable = true,
                        errorMessage = message,
                        cumulativeUsedCalls = null,
                        cumulativeUsageRequests = null,
                        cumulativeUsageTokens = null,
                        cumulativeUsageActualCost = null,
                        isFallback = true
                    )
                }
            }

            return WidgetData(
                platformName = platformName,
                isSuccess = false,
                statusText = message,
                isAvailable = false,
                errorMessage = message
            )
        }

        fun empty(platformName: String): WidgetData {
            return WidgetData(
                platformName = platformName,
                isSuccess = false,
                statusText = "未配置",
                isAvailable = false
            )
        }

        fun formatNumber(value: Double): String {
            return when {
                value >= 1_000_000 -> "%.2fM".format(value / 1_000_000)
                value >= 10_000 -> "%.0fK".format(value / 1_000)
                value == value.toLong().toDouble() -> "%,d".format(value.toLong())
                else -> "%.2f".format(value)
            }
        }

        private fun decorateLocalLabel(raw: String): String {
            val clean = raw.trim()
            if (clean.isBlank()) return clean
            if (clean.startsWith("本地·") || clean.startsWith("云端·")) return clean
            return "本地·$clean"
        }

        private fun isTransientError(message: String): Boolean {
            return TRANSIENT_ERROR_KEYWORDS.any { keyword -> message.contains(keyword) }
        }

        private fun loadLastSuccessfulData(platformName: String): WidgetData? {
            val context = DashboardApplication.appContextOrNull() ?: return null
            val prefs = context.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
            val canonicalKey = InstanceKeyResolver.canonicalInstanceId(platformName)

            var raw = prefs.getString(canonicalKey, null)?.takeIf { it.isNotBlank() }
            if (raw == null) {
                for (legacyAlias in InstanceKeyResolver.legacyAliases(platformName)) {
                    val legacyRaw = prefs.getString(legacyAlias, null)?.takeIf { it.isNotBlank() }
                        ?: continue
                    raw = legacyRaw
                    prefs.edit().putString(canonicalKey, legacyRaw).commit()
                    break
                }
            }

            raw ?: return null

            return try {
                val obj = JSONObject(raw)
                WidgetData(
                    platformName = platformName,
                    modelName = obj.optString("modelName", "").takeIf { it.isNotBlank() },
                    primaryMetric = obj.optJSONObject("primaryMetric")?.let(::jsonToMetric),
                    usageMetrics = jsonToMetrics(obj.optJSONArray("usageMetrics")),
                    percentage = if (obj.has("percentage")) obj.optInt("percentage") else null,
                    percentageLabel = obj.optString("percentageLabel", "").takeIf { it.isNotBlank() },
                    auxiliaryMetrics = jsonToMetrics(obj.optJSONArray("auxiliaryMetrics")),
                    statusText = obj.optString("statusText", "").takeIf { it.isNotBlank() },
                    isSuccess = true,
                    displayLabel = obj.optString("displayLabel", "").takeIf { it.isNotBlank() },
                    total = if (obj.has("total")) obj.optDouble("total") else null,
                    used = if (obj.has("used")) obj.optDouble("used") else null,
                    remaining = if (obj.has("remaining")) obj.optDouble("remaining") else null,
                    callCount = if (obj.has("callCount")) obj.optLong("callCount") else null,
                    usagePercent = if (obj.has("usagePercent")) obj.optInt("usagePercent") else null,
                    isAvailable = true,
                    errorMessage = null,
                    isFallback = true
                )
            } catch (e: Exception) {
                android.util.Log.w("WidgetData", "读取最近成功数据失败: $canonicalKey", e)
                null
            }
        }

        private fun metricToJson(metric: DisplayMetric): JSONObject {
            return JSONObject()
                .put("label", metric.label)
                .put("value", metric.value)
                .put("source", metric.source.name)
        }

        private fun metricsToJson(metrics: List<DisplayMetric>): JSONArray {
            val array = JSONArray()
            metrics.forEach { metric -> array.put(metricToJson(metric)) }
            return array
        }

        private fun jsonToMetric(obj: JSONObject): DisplayMetric {
            val source = try {
                MetricSource.valueOf(obj.optString("source", MetricSource.AUTO.name))
            } catch (_: Exception) {
                MetricSource.AUTO
            }
            return DisplayMetric(
                label = obj.optString("label", ""),
                value = obj.optString("value", ""),
                source = source
            )
        }

        private fun jsonToMetrics(array: JSONArray?): List<DisplayMetric> {
            if (array == null) return emptyList()
            val result = mutableListOf<DisplayMetric>()
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let { result.add(jsonToMetric(it)) }
            }
            return result
        }
    }

    private fun shouldPersistAsSuccessfulData(): Boolean {
        if (!isSuccess || !isAvailable || isFallback) return false

        return primaryMetric != null ||
            usageMetrics.any { it.source != MetricSource.STATUS } ||
            percentage != null ||
            auxiliaryMetrics.isNotEmpty() ||
            !modelName.isNullOrBlank() ||
            total != null ||
            used != null ||
            remaining != null ||
            callCount != null ||
            usagePercent != null
    }

    private fun persistLastSuccessfulData() {
        val context = DashboardApplication.appContextOrNull() ?: return
        val canonicalKey = InstanceKeyResolver.canonicalInstanceId(platformName)

        try {
            val obj = JSONObject()
                .put("platformName", platformName)
                .put("isSuccess", true)
                .put("isAvailable", true)

            modelName?.let { obj.put("modelName", it) }
            primaryMetric?.let { obj.put("primaryMetric", metricToJson(it)) }
            obj.put("usageMetrics", metricsToJson(usageMetrics))
            percentage?.let { obj.put("percentage", it) }
            percentageLabel?.let { obj.put("percentageLabel", it) }
            obj.put("auxiliaryMetrics", metricsToJson(auxiliaryMetrics))
            statusText?.let { obj.put("statusText", it) }
            displayLabel?.let { obj.put("displayLabel", it) }
            total?.let { obj.put("total", it) }
            used?.let { obj.put("used", it) }
            remaining?.let { obj.put("remaining", it) }
            callCount?.let { obj.put("callCount", it) }
            usagePercent?.let { obj.put("usagePercent", it) }

            context.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(canonicalKey, obj.toString())
                .apply()
        } catch (e: Exception) {
            android.util.Log.w("WidgetData", "保存最近成功数据失败: $canonicalKey", e)
        }
    }

    fun hasModelName(): Boolean = !modelName.isNullOrBlank()
    fun hasTotal(): Boolean = total != null && total >= 0
    fun hasUsed(): Boolean = used != null && used >= 0
    fun hasRemaining(): Boolean = remaining != null && remaining >= 0
    fun hasCallCount(): Boolean = callCount != null && callCount >= 0
    fun hasUsagePercent(): Boolean = usagePercent != null

    fun formatTotal(): String = total?.let { formatNumber(it) } ?: ""
    fun formatUsed(): String = used?.let { formatNumber(it) } ?: ""
    fun formatRemaining(): String = remaining?.let { formatNumber(it) } ?: ""
    fun formatCallCount(): String = callCount?.toString() ?: ""
    fun formatPercent(): String = usagePercent?.let { "$it%" } ?: ""
    fun getProgressValue(): Int = usagePercent?.coerceIn(0, 100) ?: 0

    fun isEmpty(): Boolean {
        return primaryMetric == null &&
            usageMetrics.isEmpty() &&
            percentage == null &&
            auxiliaryMetrics.isEmpty() &&
            !hasModelName() &&
            !hasTotal() &&
            !hasUsed() &&
            !hasRemaining() &&
            !hasCallCount() &&
            !hasUsagePercent()
    }

    private fun cleanString(input: String?): String {
        if (input == null) return ""
        return input.trim()
            .removePrefix("\"")
            .removeSuffix("\"")
            .replace("\\\"", "\"")
            .trim()
    }

    fun getPrimaryDisplay(): String? {
        return when {
            hasRemaining() -> {
                val label = cleanString(displayLabel)
                "剩余 ${formatRemaining()}${if (label.isNotBlank()) " $label" else ""}"
            }
            hasTotal() -> {
                val label = cleanString(displayLabel)
                "总额 ${formatTotal()}${if (label.isNotBlank()) " $label" else ""}"
            }
            else -> null
        }
    }

    fun getSecondaryDisplay(): String? {
        val parts = mutableListOf<String>()

        if (hasUsed()) {
            val label = cleanString(displayLabel)
            parts.add("已用: ${formatUsed()}${if (label.isNotBlank()) " $label" else ""}")
        }

        if (hasTotal() && !hasRemaining() && !hasUsed()) {
            val label = cleanString(displayLabel)
            parts.add("总额: ${formatTotal()}${if (label.isNotBlank()) " $label" else ""}")
        }

        if (hasCallCount() && !hasUsed()) {
            parts.add("调用: ${formatCallCount()} 次")
        }

        return if (parts.isEmpty()) null else parts.take(2).joinToString(" | ")
    }

    fun getModelDisplay(): String? = if (hasModelName()) modelName else null
    fun getPercentDisplay(): String? = if (hasUsagePercent()) formatPercent() else null

    fun getErrorDisplay(): String? {
        return if (!isAvailable) cleanString(errorMessage ?: "未配置") else null
    }

    private fun formatNumber(value: Double): String {
        return when {
            value >= 1_000_000 -> "%.2fM".format(value / 1_000_000)
            value >= 10_000 -> "%.0fK".format(value / 1_000)
            value == value.toLong().toDouble() -> "%,d".format(value.toLong())
            else -> "%.2f".format(value)
        }
    }
}
