package com.java.myapplication.stats

import android.content.Context
import com.java.myapplication.adapter.WidgetData
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

/**
 * 用服务端累计值在本机计算近期消耗。
 *
 * 只保存时间戳和累计数字，不保存 API Key、Cookie、Token 或接口原文。
 * 固定窗口：1 小时、6 小时、12 小时、24 小时；没有足够历史时使用真实已记录时长。
 */
object RecentUsageTracker {

    data class CumulativeUsage(
        val requests: Long? = null,
        val tokens: Long? = null,
        val cost: BigDecimal? = null,
        val currency: String? = null
    ) {
        fun hasAnyValue(): Boolean = requests != null || tokens != null || cost != null
    }

    private data class Snapshot(
        val time: Long,
        val requests: Long?,
        val tokens: Long?,
        val cost: BigDecimal?
    )

    private data class Window(
        val label: String,
        val durationMs: Long,
        val toleranceMs: Long
    )

    private const val PREFS_NAME = "widget_recent_usage_v1"
    private const val RETENTION_MS = 30L * 60L * 60L * 1000L
    private const val MIN_SAMPLE_INTERVAL_MS = 5L * 60L * 1000L
    private const val MAX_SNAPSHOTS = 96

    private val windows = listOf(
        Window("近1小时", 1L * 60L * 60L * 1000L, 20L * 60L * 1000L),
        Window("近6小时", 6L * 60L * 60L * 1000L, 90L * 60L * 1000L),
        Window("近12小时", 12L * 60L * 60L * 1000L, 2L * 60L * 60L * 1000L),
        Window("近24小时", 24L * 60L * 60L * 1000L, 2L * 60L * 60L * 1000L)
    )

    fun identity(
        provider: String,
        apiBase: String,
        apiKey: String,
        modelName: String?
    ): String {
        val apiKeyFingerprint = sha256(apiKey.trim()).take(16)
        return sha256(
            listOf(
                provider.trim().lowercase(),
                apiBase.trim().trimEnd('/').lowercase(),
                modelName.orEmpty().trim().lowercase(),
                apiKeyFingerprint
            ).joinToString("|")
        ).take(32)
    }

    fun apply(
        context: Context?,
        identity: String,
        data: WidgetData,
        cumulative: CumulativeUsage
    ): WidgetData {
        if (context == null || identity.isBlank() || !cumulative.hasAnyValue()) return data

        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "usage_$identity"
        val history = loadSnapshots(prefs.getString(key, null))
            .filter { now - it.time in 0..RETENTION_MS }
            .sortedBy { it.time }
            .toMutableList()

        val current = Snapshot(
            time = now,
            requests = cumulative.requests,
            tokens = cumulative.tokens,
            cost = cumulative.cost
        )

        val last = history.lastOrNull()
        if (last != null && hasCounterReset(last, current)) {
            history.clear()
        }

        val metrics = buildMetrics(history, current, cumulative.currency)
        appendOrReplace(history, current)
        saveSnapshots(prefs, key, history)

        return data.copy(
            usageMetrics = if (metrics.isEmpty()) {
                listOf(WidgetData.DisplayMetric("近期消耗统计中…", ""))
            } else {
                metrics
            }
        )
    }

    private fun buildMetrics(
        history: List<Snapshot>,
        current: Snapshot,
        currency: String?
    ): List<WidgetData.DisplayMetric> {
        if (history.isEmpty()) return emptyList()

        val fixedWindowMetrics = windows.mapNotNull { window ->
            val baseline = history
                .asSequence()
                .filter { snapshot ->
                    val age = current.time - snapshot.time
                    age >= window.durationMs && age <= window.durationMs + window.toleranceMs
                }
                .minByOrNull { snapshot ->
                    abs((current.time - snapshot.time) - window.durationMs)
                }
                ?: return@mapNotNull null

            buildMetric(window.label, baseline, current, currency)
        }

        if (fixedWindowMetrics.isNotEmpty()) return fixedWindowMetrics

        val oldest = history.firstOrNull() ?: return emptyList()
        val elapsed = current.time - oldest.time
        if (elapsed < MIN_SAMPLE_INTERVAL_MS) return emptyList()

        return listOfNotNull(
            buildMetric(formatElapsedLabel(elapsed), oldest, current, currency)
        )
    }

    private fun buildMetric(
        label: String,
        baseline: Snapshot,
        current: Snapshot,
        currency: String?
    ): WidgetData.DisplayMetric? {
        val requestDelta = delta(current.requests, baseline.requests)
        val tokenDelta = delta(current.tokens, baseline.tokens)
        val costDelta = delta(current.cost, baseline.cost)

        if (requestDelta == null && tokenDelta == null && costDelta == null) return null

        val parts = mutableListOf<String>()
        requestDelta?.let { parts.add("${formatCount(it)}次") }
        tokenDelta?.let { parts.add("${formatCount(it)} Token") }
        costDelta?.let { parts.add(formatMoney(it, currency)) }

        val hasConsumption = (requestDelta ?: 0L) > 0L ||
            (tokenDelta ?: 0L) > 0L ||
            (costDelta ?: BigDecimal.ZERO) > BigDecimal.ZERO

        return WidgetData.DisplayMetric(
            label = label,
            value = if (hasConsumption) parts.joinToString(" · ") else "暂无消耗～"
        )
    }

    private fun hasCounterReset(previous: Snapshot, current: Snapshot): Boolean {
        return decreased(current.requests, previous.requests) ||
            decreased(current.tokens, previous.tokens) ||
            decreased(current.cost, previous.cost)
    }

    private fun decreased(current: Long?, previous: Long?): Boolean {
        return current != null && previous != null && current < previous
    }

    private fun decreased(current: BigDecimal?, previous: BigDecimal?): Boolean {
        return current != null && previous != null && current < previous
    }

    private fun delta(current: Long?, previous: Long?): Long? {
        if (current == null || previous == null) return null
        return (current - previous).coerceAtLeast(0L)
    }

    private fun delta(current: BigDecimal?, previous: BigDecimal?): BigDecimal? {
        if (current == null || previous == null) return null
        return current.subtract(previous).coerceAtLeast(BigDecimal.ZERO)
    }

    private fun appendOrReplace(history: MutableList<Snapshot>, current: Snapshot) {
        val last = history.lastOrNull()
        if (last != null && current.time - last.time < MIN_SAMPLE_INTERVAL_MS) {
            history[history.lastIndex] = current
        } else {
            history.add(current)
        }

        while (history.size > MAX_SNAPSHOTS) {
            history.removeAt(0)
        }
    }

    private fun loadSnapshots(raw: String?): List<Snapshot> {
        if (raw.isNullOrBlank()) return emptyList()

        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val time = item.optLong("time", -1L)
                    if (time <= 0L) continue
                    add(
                        Snapshot(
                            time = time,
                            requests = item.optNullableLong("requests"),
                            tokens = item.optNullableLong("tokens"),
                            cost = item.optNullableDecimal("cost")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveSnapshots(
        prefs: android.content.SharedPreferences,
        key: String,
        snapshots: List<Snapshot>
    ) {
        val array = JSONArray()
        snapshots.forEach { snapshot ->
            val item = JSONObject().put("time", snapshot.time)
            snapshot.requests?.let { item.put("requests", it) }
            snapshot.tokens?.let { item.put("tokens", it) }
            snapshot.cost?.let { item.put("cost", it.toPlainString()) }
            array.put(item)
        }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private fun JSONObject.optNullableLong(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return opt(key)?.toString()?.toBigDecimalOrNull()?.toLong()
    }

    private fun JSONObject.optNullableDecimal(key: String): BigDecimal? {
        if (!has(key) || isNull(key)) return null
        return opt(key)?.toString()?.toBigDecimalOrNull()
    }

    private fun formatElapsedLabel(durationMs: Long): String {
        val minutes = (durationMs / 60_000L).coerceAtLeast(1L)
        val hours = minutes / 60L
        val remainingMinutes = minutes % 60L
        return when {
            hours == 0L -> "近${minutes}分钟"
            remainingMinutes < 5L -> "近${hours}小时"
            else -> "近${hours}小时${remainingMinutes}分钟"
        }
    }

    private fun formatCount(value: Long): String {
        val absolute = kotlin.math.abs(value.toDouble())
        return when {
            absolute >= 1_000_000_000 -> compact(value, 1_000_000_000.0, "B")
            absolute >= 1_000_000 -> compact(value, 1_000_000.0, "M")
            absolute >= 1_000 -> compact(value, 1_000.0, "K")
            else -> value.toString()
        }
    }

    private fun compact(value: Long, divisor: Double, suffix: String): String {
        val number = BigDecimal.valueOf(value / divisor)
            .setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        return "${number.toPlainString()}$suffix"
    }

    private fun formatMoney(value: BigDecimal, currency: String?): String {
        val symbol = when (currency.orEmpty().uppercase()) {
            "CNY", "RMB", "¥" -> "¥"
            "USD", "$" -> "$"
            else -> currency.orEmpty().takeIf { it.isNotBlank() }?.plus(" ") ?: "¥"
        }
        val scale = when {
            value.abs() >= BigDecimal.ONE -> 2
            value.abs() >= BigDecimal("0.001") -> 4
            else -> 6
        }
        return "$symbol${value.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()}"
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
