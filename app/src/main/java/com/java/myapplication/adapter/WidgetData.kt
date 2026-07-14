package com.java.myapplication.adapter

import android.content.Context
import com.java.myapplication.DashboardApplication
import com.java.myapplication.config.InstanceKeyResolver
import org.json.JSONArray
import org.json.JSONObject

/**
 * 统一 Widget 数据模型 — 五层卡片结构
 *
 * 所有平台适配器最终都转换为这个结构
 *
 * 五层结构：
 * 第1层：modelName — 模型名称（固定）
 * 第2层：primaryMetric — Core 1 固定核心指标（固定，不参与轮播）
 * 第3层：usageMetrics — Core 2 动态轮播位①（近期消耗数据列表）
 * 第4层：percentage + percentageLabel — 百分比视觉层（固定）
 * 第5层：auxiliaryMetrics — B级辅助轮播位②（辅助数据列表）
 *
 * 状态：statusText（固定底部角标，不参与轮播）
 */
data class WidgetData(
    val platformName: String,         // 平台名称，用于内部路由
    val modelName: String? = null,    // 第1层：模型名称

    // 第2层：Core 1 固定核心指标
    val primaryMetric: DisplayMetric? = null,

    // 第3层：Core 2 动态轮播位①（近期消耗数据列表）
    val usageMetrics: List<DisplayMetric> = emptyList(),

    // 第4层：百分比视觉层
    val percentage: Int? = null,         // 百分比数值 0-100
    val percentageLabel: String? = null, // 百分比含义标签

    // 第5层：B级辅助轮播位②（辅助数据列表）
    val auxiliaryMetrics: List<DisplayMetric> = emptyList(),

    // 状态
    val statusText: String? = null,      // 固定状态文字（底部角标）
    val isSuccess: Boolean = true,       // 是否成功获取数据

    // 兼容旧字段（逐步迁移）
    val displayLabel: String? = null,    // 【旧】显示标签
    val total: Double? = null,           // 【旧】总量
    val used: Double? = null,            // 【旧】已用量
    val remaining: Double? = null,       // 【旧】剩余量
    val callCount: Long? = null,         // 【旧】调用次数
    val usagePercent: Int? = null,       // 【旧】使用率
    val isAvailable: Boolean = true,     // 【旧】是否可用
    val errorMessage: String? = null,    // 【旧】错误信息

    // --- Kimi 本地快照统计字段 ---
    val cumulativeUsedCalls: Long? = null, // 累计已用次数（仅 Kimi）

    // --- Aihuangniu 本地快照统计字段 ---
    val cumulativeUsageRequests: Long? = null,    // 累计请求次数
    val cumulativeUsageTokens: Long? = null,      // 累计 Token 数
    val cumulativeUsageActualCost: String? = null, // 累计消费金额（字符串避免浮点误差）

    // 仅用于 UI 状态：当前是否正在展示最近一次成功数据
    val isFallback: Boolean = false
) {

    init {
        if (shouldPersistAsSuccessfulData()) {
            persistLastSuccessfulData()
        }
    }

    /**
     * 单个展示指标
     */
    data class DisplayMetric(
        val label: String, // 标签，例如 "剩余"、"近10分钟消费"
        val value: String  // 值，例如 "5000 次"、"¥0.15"
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

        /**
         * 创建一个表示错误的 WidgetData。
         * 对临时网络类错误：优先返回该卡片最近一次成功数据。
         * 对认证失败、配置错误、解析错误等：继续展示真实错误。
         */
        fun error(platformName: String, message: String): WidgetData {
            if (isTransientError(message)) {
                val cached = loadLastSuccessfulData(platformName)
                if (cached != null) {
                    val markedAuxiliary = if (cached.auxiliaryMetrics.isEmpty()) {
                        listOf(DisplayMetric("", "😂"))
                    } else {
                        cached.auxiliaryMetrics.map { metric ->
                            metric.copy(value = "${metric.value} 😂")
                        }
                    }

                    return cached.copy(
                        usageMetrics = listOf(DisplayMetric(FALLBACK_MESSAGE, "")),
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

        /**
         * 创建一个空数据的 WidgetData（用于未配置的平台）
         */
        fun empty(platformName: String): WidgetData {
            return WidgetData(
                platformName = platformName,
                isSuccess = false,
                statusText = "未配置",
                isAvailable = false
            )
        }

        /**
         * 公共数字格式化方法
         * - 10000以下：显示完整数字
         * - 10000以上：使用 K/M 缩写
         */
        fun formatNumber(value: Double): String {
            return when {
                value >= 1000000 -> "%.2fM".format(value / 1000000)
                value >= 10000 -> "%.0fK".format(value / 1000)
                value == value.toLong().toDouble() -> "%,d".format(value.toLong())
                else -> "%.2f".format(value)
            }
        }

        private fun isTransientError(message: String): Boolean {
            return TRANSIENT_ERROR_KEYWORDS.any { keyword -> message.contains(keyword) }
        }

        /**
         * Stage 8A-3：先读稳定 instanceId 键；若不存在，再兼容读取历史平台键，
         * 并把原始缓存复制到新键。旧键保留用于回滚。
         */
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
                    // 复制而不删除；失败时仍可继续使用旧缓存，下次再重试。
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
                    primaryMetric = obj.optJSONObject("primaryMetric")?.let { jsonToMetric(it) },
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
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { result.add(jsonToMetric(it)) }
            }
            return result
        }
    }

    private fun shouldPersistAsSuccessfulData(): Boolean {
        if (!isSuccess || !isAvailable || isFallback) return false

        return primaryMetric != null ||
            usageMetrics.isNotEmpty() ||
            percentage != null ||
            auxiliaryMetrics.isNotEmpty() ||
            !modelName.isNullOrBlank() ||
            total != null ||
            used != null ||
            remaining != null ||
            callCount != null ||
            usagePercent != null
    }

    /**
     * Stage 8A-3：成功数据统一写入稳定 instanceId 键。
     */
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

    /**
     * 是否有模型名称可显示
     */
    fun hasModelName(): Boolean = !modelName.isNullOrBlank()

    /**
     * 是否有总量可显示
     */
    fun hasTotal(): Boolean = total != null && total >= 0

    /**
     * 是否有已用量可显示
     */
    fun hasUsed(): Boolean = used != null && used >= 0

    /**
     * 是否有剩余量可显示
     */
    fun hasRemaining(): Boolean = remaining != null && remaining >= 0

    /**
     * 是否有调用次数可显示
     */
    fun hasCallCount(): Boolean = callCount != null && callCount >= 0

    /**
     * 是否有使用率可显示（用于进度条）
     */
    fun hasUsagePercent(): Boolean = usagePercent != null

    /**
     * 获取格式化后的总量字符串
     */
    fun formatTotal(): String = total?.let { formatNumber(it) } ?: ""

    /**
     * 获取格式化后的已用量字符串
     */
    fun formatUsed(): String = used?.let { formatNumber(it) } ?: ""

    /**
     * 获取格式化后的剩余量字符串
     */
    fun formatRemaining(): String = remaining?.let { formatNumber(it) } ?: ""

    /**
     * 获取格式化后的调用次数字符串
     */
    fun formatCallCount(): String = callCount?.toString() ?: ""

    /**
     * 判断当前数据是否为空（未配置状态）
     */
    fun isEmpty(): Boolean {
        return !hasModelName() && !hasTotal() && !hasUsed() && !hasRemaining() && !hasCallCount() && !hasUsagePercent()
    }

    /**
     * 获取使用率百分比字符串
     */
    fun formatPercent(): String = usagePercent?.let { "$it%" } ?: ""

    /**
     * 获取进度条进度值
     */
    fun getProgressValue(): Int = usagePercent?.coerceIn(0, 100) ?: 0

    /**
     * 清理字符串中的多余引号和空白
     */
    private fun cleanString(input: String?): String {
        if (input == null) return ""
        return input.trim()
            .removePrefix("\"")
            .removeSuffix("\"")
            .replace("\\\"", "\"")
            .trim()
    }

    /**
     * 获取第一层显示内容（最高优先级：剩余）
     * 返回 null 表示没有数据
     */
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

    /**
     * 获取第二层显示内容（辅助信息：已用/总额/调用次数）
     * 最多返回2项
     *
     * 去重规则：
     * - 如果已用和调用次数表达的是同一个意思（例如都是次数），只显示一个
     */
    fun getSecondaryDisplay(): String? {
        val parts = mutableListOf<String>()

        // 已用（优先显示）
        if (hasUsed()) {
            val label = cleanString(displayLabel)
            parts.add("已用: ${formatUsed()}${if (label.isNotBlank()) " $label" else ""}")
        }

        // 总额（如果剩余不存在，且已用没有显示）
        if (hasTotal() && !hasRemaining() && !hasUsed()) {
            val label = cleanString(displayLabel)
            parts.add("总额: ${formatTotal()}${if (label.isNotBlank()) " $label" else ""}")
        }

        // 调用次数（如果已用不存在，或者已用和调用次数不是同一个意思）
        // 去重：如果已用和调用次数都代表"次数"，只显示已用
        if (hasCallCount() && !hasUsed()) {
            parts.add("调用: ${formatCallCount()} 次")
        }

        return if (parts.isEmpty()) null else parts.take(2).joinToString(" | ")
    }

    /**
     * 获取第三层显示内容（模型名称）
     */
    fun getModelDisplay(): String? {
        return if (hasModelName()) modelName else null
    }

    /**
     * 获取第四层显示内容（使用率百分比）
     */
    fun getPercentDisplay(): String? {
        return if (hasUsagePercent()) formatPercent() else null
    }

    /**
     * 获取错误信息显示
     */
    fun getErrorDisplay(): String? {
        return if (!isAvailable) {
            val msg = errorMessage ?: "未配置"
            cleanString(msg)
        } else null
    }

    /**
     * 数字格式化规则：
     * - 10000以下：显示完整数字（例如：1957 次）
     * - 10000以上：使用 K/M 缩写（例如：12K、1.2M）
     */
    private fun formatNumber(value: Double): String {
        return when {
            value >= 1000000 -> "%.2fM".format(value / 1000000)
            value >= 10000 -> "%.0fK".format(value / 1000)
            value == value.toLong().toDouble() -> "%,d".format(value.toLong())
            else -> "%.2f".format(value)
        }
    }
}
