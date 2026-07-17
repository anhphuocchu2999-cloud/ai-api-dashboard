package com.java.myapplication.adapter

import android.content.Context
import com.java.myapplication.DashboardApplication
import com.java.myapplication.config.InstanceKeyResolver
import org.json.JSONArray
import org.json.JSONObject

/**
 * 统一 Widget 数据模型。
 *
 * 每张卡片固定承载：模型名称、核心指标、近期消耗、百分比和辅助指标。
 * 数据来源标签由 BalanceWidgetProvider 统一渲染，避免 Adapter 重复拼接。
 *
 * cacheKey 是当前模型实例／槽位的稳定身份，只用于本地最近成功数据隔离。
 */
data class WidgetData(
    val platformName: String,
    val modelName: String? = null,
    val primaryMetric: DisplayMetric? = null,
    var usageMetrics: List<DisplayMetric> = emptyList(),
    val percentage: Int? = null,
    val percentageLabel: String? = null,
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
    val isFallback: Boolean = false,

    // 当前模型实例／槽位的缓存身份；为空时不写入最近成功缓存。
    val cacheKey: String? = null,

    // 仅用于缓存回退展示；实时结果不设置。
    val cachedAtMillis: Long? = null
) {

    init {
        // 禁止渲染层用随机俏皮文案冒充动态数据。
        // 有真实核心数据但暂时无法计算近期消耗时，只显示明确状态。
        if (
            isSuccess &&
            isAvailable &&
            !isFallback &&
            primaryMetric != null &&
            usageMetrics.isEmpty()
        ) {
            usageMetrics = listOf(
                DisplayMetric("近期消耗", "暂无可计算数据")
            )
        }

        if (shouldPersistAsSuccessfulData()) {
            persistLastSuccessfulData()
        }
    }

    data class DisplayMetric(
        val label: String,
        val value: String
    )

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

        fun clearLastSuccessfulDataForInstance(instanceKey: String) {
            val context = DashboardApplication.appContextOrNull() ?: return
            val canonical = InstanceKeyResolver.canonicalInstanceId(instanceKey)
                .lowercase()
                .replace(Regex("[^a-z0-9_-]"), "_")
            if (canonical.isBlank()) return
            val prefs = context.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
            val prefix = "model-cache-$canonical-"
            val editor = prefs.edit()
            prefs.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
            editor.apply()
        }

        fun error(
            platformName: String,
            message: String,
            cacheKey: String? = null
        ): WidgetData {
            val base = WidgetData(
                platformName = platformName,
                isSuccess = false,
                statusText = message,
                isAvailable = false,
                errorMessage = message,
                cacheKey = cacheKey?.takeIf { it.isNotBlank() }
            )
            return if (cacheKey.isNullOrBlank()) base else base.bindCacheKey(cacheKey)
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

        private fun isTransientError(message: String): Boolean {
            return TRANSIENT_ERROR_KEYWORDS.any { keyword -> message.contains(keyword) }
        }

        private fun loadLastSuccessfulData(
            instanceKey: String,
            fallbackPlatformName: String
        ): WidgetData? {
            val context = DashboardApplication.appContextOrNull() ?: return null
            val prefs = context.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
            val canonicalKey = InstanceKeyResolver.canonicalInstanceId(instanceKey)
            if (canonicalKey.isBlank()) return null

            var raw = prefs.getString(canonicalKey, null)?.takeIf { it.isNotBlank() }
            if (raw == null) {
                for (legacyAlias in InstanceKeyResolver.legacyAliases(instanceKey)) {
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
                    platformName = obj.optString("platformName", fallbackPlatformName)
                        .takeIf { it.isNotBlank() }
                        ?: fallbackPlatformName,
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
                    isFallback = true,
                    cacheKey = canonicalKey,
                    cachedAtMillis = obj.optLong("savedAt", 0L).takeIf { it > 0L }
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
        }

        private fun metricsToJson(metrics: List<DisplayMetric>): JSONArray {
            val array = JSONArray()
            metrics.forEach { metric -> array.put(metricToJson(metric)) }
            return array
        }

        private fun jsonToMetric(obj: JSONObject): DisplayMetric {
            return DisplayMetric(
                label = obj.optString("label", ""),
                value = obj.optString("value", "")
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

        /**
         * 使用稳定的数据能力槽位比较完整度，禁止把会变化的中文展示标签当成字段身份。
         */
        private fun cachedFieldKeys(obj: JSONObject): Set<String> = buildSet {
            obj.optJSONObject("primaryMetric")?.let { metric ->
                if (metric.optString("value", "").isNotBlank()) add("primary")
            }
            val usage = obj.optJSONArray("usageMetrics")
            var usageSlot = 0
            for (index in 0 until (usage?.length() ?: 0)) {
                val metric = usage?.optJSONObject(index) ?: continue
                val value = metric.optString("value", "").trim()
                if (value.isNotBlank() && value != "暂无可计算数据") {
                    add("usage-slot:${usageSlot++}")
                }
            }
            if (obj.has("percentage")) {
                add("percentage")
            }
            val auxiliary = obj.optJSONArray("auxiliaryMetrics")
            for (index in 0 until (auxiliary?.length() ?: 0)) {
                val metric = auxiliary?.optJSONObject(index) ?: continue
                val label = metric.optString("label", "").trim()
                val value = metric.optString("value", "").trim()
                if (label.isNotBlank() && value.isNotBlank()) add("auxiliary:$label")
            }
            listOf("total", "used", "remaining", "callCount", "usagePercent").forEach { field ->
                if (obj.has(field)) add("legacy:$field")
            }
        }

    }

    /**
     * 将 Adapter 返回值绑定到当前模型实例。
     *
     * 成功数据只写入该实例缓存；临时错误只读取该实例缓存，禁止按平台名跨槽位兜底。
     */
    fun bindCacheKey(instanceKey: String): WidgetData {
        val canonicalKey = InstanceKeyResolver.canonicalInstanceId(instanceKey)
        if (canonicalKey.isBlank()) return this

        if (!isSuccess || !isAvailable) {
            val message = statusText ?: errorMessage.orEmpty()
            if (isTransientError(message)) {
                val cached = loadLastSuccessfulData(canonicalKey, platformName)
                if (cached != null) {
                    return cached.copy(
                        statusText = FALLBACK_MESSAGE,
                        isSuccess = true,
                        isAvailable = true,
                        errorMessage = message,
                        cumulativeUsedCalls = null,
                        cumulativeUsageRequests = null,
                        cumulativeUsageTokens = null,
                        cumulativeUsageActualCost = null,
                        isFallback = true,
                        cacheKey = canonicalKey
                    )
                }
            }
        }

        return if (cacheKey == canonicalKey) this else copy(cacheKey = canonicalKey)
    }

    private fun shouldPersistAsSuccessfulData(): Boolean {
        if (cacheKey.isNullOrBlank() || !isSuccess || !isAvailable || isFallback) return false

        return primaryMetric != null ||
            usageMetrics.any { it.value != "暂无可计算数据" } ||
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
        val canonicalKey = cacheKey
            ?.let(InstanceKeyResolver::canonicalInstanceId)
            ?.takeIf { it.isNotBlank() }
            ?: return

        try {
            val obj = JSONObject()
                .put("platformName", platformName)
                .put("cacheKey", canonicalKey)
                .put("isSuccess", true)
                .put("isAvailable", true)
                .put("savedAt", System.currentTimeMillis())

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

            val prefs = context.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
            val existingKeys = prefs.getString(canonicalKey, null)
                ?.takeIf { it.isNotBlank() }
                ?.let { raw -> runCatching { cachedFieldKeys(JSONObject(raw)) }.getOrNull() }
                .orEmpty()
            val candidateKeys = cachedFieldKeys(obj)
            if (!candidateKeys.containsAll(existingKeys)) return

            prefs.edit()
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
