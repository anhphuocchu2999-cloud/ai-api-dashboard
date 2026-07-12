package com.java.myapplication.adapter

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
    val modelName: String? = null,      // 第1层：模型名称

    // 第2层：Core 1 固定核心指标
    val primaryMetric: DisplayMetric? = null,

    // 第3层：Core 2 动态轮播位①（近期消耗数据列表）
    val usageMetrics: List<DisplayMetric> = emptyList(),

    // 第4层：百分比视觉层
    val percentage: Int? = null,        // 百分比数值 0-100
    val percentageLabel: String? = null, // 百分比含义标签

    // 第5层：B级辅助轮播位②（辅助数据列表）
    val auxiliaryMetrics: List<DisplayMetric> = emptyList(),

    // 状态
    val statusText: String? = null,     // 固定状态文字（底部角标）
    val isSuccess: Boolean = true,      // 是否成功获取数据

    // 兼容旧字段（逐步迁移）
    val displayLabel: String? = null,   // 【旧】显示标签
    val total: Double? = null,          // 【旧】总量
    val used: Double? = null,         // 【旧】已用量
    val remaining: Double? = null,      // 【旧】剩余量
    val callCount: Long? = null,        // 【旧】调用次数
    val usagePercent: Int? = null,      // 【旧】使用率
    val isAvailable: Boolean = true,    // 【旧】是否可用
    val errorMessage: String? = null,   // 【旧】错误信息

    // --- Kimi 本地快照统计字段 ---
    val cumulativeUsedCalls: Long? = null,  // 累计已用次数（仅 Kimi）

    // --- Aihuangniu 本地快照统计字段 ---
    val cumulativeUsageRequests: Long? = null,     // 累计请求次数
    val cumulativeUsageTokens: Long? = null,         // 累计 Token 数
    val cumulativeUsageActualCost: String? = null    // 累计消费金额（字符串避免浮点误差）
) {

    /**
     * 单个展示指标
     */
    data class DisplayMetric(
        val label: String,   // 标签，例如 "剩余"、"近10分钟消费"
        val value: String    // 值，例如 "5000 次"、"¥0.15"
    )

    companion object {
        /**
         * 创建一个表示错误的 WidgetData
         */
        fun error(platformName: String, message: String): WidgetData {
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