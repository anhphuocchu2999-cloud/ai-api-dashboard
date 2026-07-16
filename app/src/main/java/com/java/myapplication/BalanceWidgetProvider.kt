package com.java.myapplication

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import com.java.myapplication.adapter.AdapterFactory
import com.java.myapplication.adapter.AdapterRequest
import com.java.myapplication.adapter.WidgetData
import com.java.myapplication.adapter.auth.BackgroundAuthRepository
import com.java.myapplication.config.ApiAccountConfig
import com.java.myapplication.config.ConfigRepository
import com.java.myapplication.stats.RecentUsageTracker
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class BalanceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateAppWidget(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        options: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, manager, id, options)
        saveCompact(context, id, options)
        updateAppWidget(context, manager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_REFRESH) return
        val id = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            handleRefreshTap(context, AppWidgetManager.getInstance(context), id)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.java.myapplication.ACTION_REFRESH"
        private const val LAYOUT_PREFS = "widget_layout_state"
        private const val CAROUSEL_PREFS = "widget_carousel"
        private const val REFRESH_TAP_PREFS = "widget_refresh_taps"
        private const val COMPACT_HEIGHT_DP = 160
        private const val SAME_HOST_INTERVAL_MS = 1_000L
        private const val REFRESH_TAP_WINDOW_MS = 3_000L
        private const val REFRESH_TAP_TARGET = 8
        private val generations = ConcurrentHashMap<Int, Long>()
        private val counter = AtomicLong(0L)

        private val refreshTapMessages = listOf(
            "嘿嘿，再点 7 下才肯刷新～",
            "还差 6 下，手速不错哦",
            "还差 5 下，服务器正在装睡 💤",
            "还差 4 下，已经成功一半啦",
            "还差 3 下，继续继续～",
            "还差 2 下，马上叫醒它",
            "最后 1 下！准备发车 🚗"
        )

        private data class CardIds(
            val title: Int,
            val primary: Int,
            val usage: Int,
            val percent: Int,
            val progress: Int,
            val auxiliary: Int
        )

        private fun ids(prefix: String) = when (prefix) {
            "mimo" -> CardIds(R.id.mimo_title, R.id.mimo_tokens, R.id.mimo_balance,
                R.id.mimo_percent, R.id.mimo_progress, R.id.mimo_auxiliary)
            "ds" -> CardIds(R.id.ds_title, R.id.ds_tokens, R.id.ds_balance,
                R.id.ds_percent, R.id.ds_progress, R.id.ds_auxiliary)
            "oai" -> CardIds(R.id.oai_title, R.id.oai_tokens, R.id.oai_balance,
                R.id.oai_percent, R.id.oai_progress, R.id.oai_auxiliary)
            else -> CardIds(R.id.kimi_title, R.id.kimi_tokens, R.id.kimi_balance,
                R.id.kimi_percent, R.id.kimi_progress, R.id.kimi_auxiliary)
        }

        private fun prefix(slot: String) = when (slot) {
            "MiMo" -> "mimo"
            "DeepSeek" -> "ds"
            "OpenAI" -> "oai"
            else -> "kimi"
        }

        private fun tapCountKey(id: Int) = "count_$id"

        private fun tapTimeKey(id: Int) = "time_$id"

        private fun handleRefreshTap(context: Context, manager: AppWidgetManager, id: Int) {
            val now = System.currentTimeMillis()
            val prefs = context.getSharedPreferences(REFRESH_TAP_PREFS, Context.MODE_PRIVATE)
            val lastTapTime = prefs.getLong(tapTimeKey(id), 0L)
            val previousCount = if (now - lastTapTime <= REFRESH_TAP_WINDOW_MS) {
                prefs.getInt(tapCountKey(id), 0)
            } else {
                0
            }
            val tapCount = previousCount + 1

            if (tapCount < REFRESH_TAP_TARGET) {
                prefs.edit()
                    .putInt(tapCountKey(id), tapCount)
                    .putLong(tapTimeKey(id), now)
                    .apply()
                val views = RemoteViews(context.packageName, R.layout.widget_balance)
                views.setTextViewText(R.id.click_count, refreshTapMessages[tapCount - 1])
                manager.partiallyUpdateAppWidget(id, views)
                return
            }

            clearRefreshTapState(context, id)
            updateAppWidget(context, manager, id)
        }

        private fun clearRefreshTapState(context: Context, id: Int) {
            context.getSharedPreferences(REFRESH_TAP_PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(tapCountKey(id))
                .remove(tapTimeKey(id))
                .apply()
        }

        private fun saveCompact(context: Context, id: Int, options: Bundle): Boolean {
            val prefs = context.getSharedPreferences(LAYOUT_PREFS, Context.MODE_PRIVATE)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            if (height <= 0) return prefs.getBoolean("compact_$id", false)
            val compact = height < COMPACT_HEIGHT_DP
            prefs.edit().putBoolean("compact_$id", compact).apply()
            return compact
        }

        private fun compact(context: Context, manager: AppWidgetManager, id: Int): Boolean {
            val prefs = context.getSharedPreferences(LAYOUT_PREFS, Context.MODE_PRIVATE)
            val key = "compact_$id"
            return if (prefs.contains(key)) prefs.getBoolean(key, false)
            else saveCompact(context, id, manager.getAppWidgetOptions(id))
        }

        private fun applyLayout(views: RemoteViews, compact: Boolean) {
            views.setViewVisibility(R.id.row_secondary, if (compact) View.GONE else View.VISIBLE)
        }

        internal fun updateAppWidget(context: Context, manager: AppWidgetManager, id: Int) {
            clearRefreshTapState(context, id)
            val generation = counter.incrementAndGet()
            generations[id] = generation
            val compact = compact(context, manager, id)
            val views = RemoteViews(context.packageName, R.layout.widget_balance)
            applyLayout(views, compact)

            val prefs = context.getSharedPreferences("api_config", Context.MODE_PRIVATE)
            val loaded = ConfigRepository.loadAllConfigs(prefs)
            val slots = listOf("Kimi", "MiMo", "DeepSeek", "OpenAI")
            val configs = slots.map { slot ->
                loaded.find { it.id == slot } ?: ApiAccountConfig(
                    id = slot, name = "", apiBase = "", apiKey = "", model = "", enabled = true
                )
            }
            val visibleSlots = if (compact) slots.take(2) else slots
            val visibleConfigs = if (compact) configs.take(2) else configs

            visibleSlots.forEach { showLoading(views, prefix(it)) }
            views.setTextViewText(R.id.update_time, "正在同步…")
            views.setTextViewText(R.id.click_count, "")
            bindRefresh(context, views, id)
            manager.updateAppWidget(id, views)

            Thread {
                val results = mutableListOf<WidgetData>()
                val hostTimes = mutableMapOf<String, Long>()
                visibleSlots.forEachIndexed { index, slot ->
                    val config = visibleConfigs[index]
                    val host = try { URL(config.apiBase.trim().trimEnd('/')).host } catch (_: Exception) { "" }
                    val last = hostTimes[host] ?: 0L
                    val wait = if (host.isNotBlank() && last > 0L) {
                        (SAME_HOST_INTERVAL_MS - (System.currentTimeMillis() - last)).coerceAtLeast(0L)
                    } else 0L
                    if (wait > 0L) try { Thread.sleep(wait) } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                    results += fetchData(context, prefs, slot, config)
                    if (host.isNotBlank()) hostTimes[host] = System.currentTimeMillis()
                }

                Handler(Looper.getMainLooper()).post {
                    if (generations[id] != generation) return@post
                    applyLayout(views, compact(context, manager, id))
                    results.forEachIndexed { index, data ->
                        val p = prefix(visibleSlots[index])
                        setTitle(views, p, visibleConfigs[index])
                        render(context, views, data, p)
                    }
                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    views.setTextViewText(R.id.update_time, "更新 $time")
                    views.setTextViewText(R.id.click_count, "")
                    bindRefresh(context, views, id)
                    manager.updateAppWidget(id, views)
                }
            }.start()
        }

        private fun showLoading(views: RemoteViews, prefix: String) {
            val ids = ids(prefix)
            views.setTextViewText(ids.primary, "正在同步…")
            views.setTextViewText(ids.usage, "")
            views.setTextViewText(ids.percent, "")
            views.setTextViewText(ids.auxiliary, "")
            views.setViewVisibility(ids.progress, View.GONE)
            views.setViewVisibility(ids.percent, View.GONE)
            views.setViewVisibility(ids.auxiliary, View.GONE)
        }

        private fun fetchData(
            context: Context,
            prefs: android.content.SharedPreferences,
            slot: String,
            config: ApiAccountConfig
        ): WidgetData {
            if (
                !config.enabled ||
                config.apiBase.isBlank() ||
                config.apiKey.isBlank() ||
                config.model.isBlank()
            ) {
                return WidgetData.empty(slot)
            }
            val adapter = AdapterFactory.getAdapter(slot, config.apiBase)
                ?: return WidgetData.error(slot, "当前服务暂未提供账户数据")
            val auth = BackgroundAuthRepository.load(prefs, slot)
            val request = AdapterRequest(
                apiBase = config.apiBase,
                modelApiKey = config.apiKey,
                modelName = config.model,
                instanceId = slot,
                backgroundAuthType = auth.authType,
                backgroundCredential = auth.authValue
            )
            val raw = try { adapter.fetchData(request) } catch (_: Exception) {
                WidgetData.error(slot, "获取失败")
            }
            return applyRecentUsage(context, slot, config, raw)
        }

        private fun applyRecentUsage(
            context: Context,
            slot: String,
            config: ApiAccountConfig,
            data: WidgetData
        ): WidgetData {
            val cumulative = when {
                data.cumulativeUsedCalls != null -> RecentUsageTracker.CumulativeUsage(
                    requests = data.cumulativeUsedCalls
                )
                data.cumulativeUsageRequests != null ||
                    data.cumulativeUsageTokens != null ||
                    data.cumulativeUsageActualCost != null -> RecentUsageTracker.CumulativeUsage(
                        requests = data.cumulativeUsageRequests,
                        tokens = data.cumulativeUsageTokens,
                        cost = data.cumulativeUsageActualCost?.toBigDecimalOrNull(),
                        currency = "CNY"
                    )
                else -> return data
            }
            return RecentUsageTracker.apply(
                context = context.applicationContext,
                identity = RecentUsageTracker.identity(
                    provider = slot,
                    apiBase = config.apiBase,
                    apiKey = config.apiKey,
                    modelName = config.model
                ),
                data = data.copy(
                    cumulativeUsedCalls = null,
                    cumulativeUsageRequests = null,
                    cumulativeUsageTokens = null,
                    cumulativeUsageActualCost = null
                ),
                cumulative = cumulative
            )
        }

        private fun render(context: Context, views: RemoteViews, data: WidgetData, prefix: String) {
            val ids = ids(prefix)
            if (!data.isSuccess || !data.isAvailable) {
                views.setTextViewText(ids.primary, data.statusText ?: data.errorMessage ?: "未配置")
                views.setTextViewText(ids.usage, "")
                views.setTextViewText(ids.percent, "")
                views.setTextViewText(ids.auxiliary, "")
                views.setViewVisibility(ids.progress, View.GONE)
                views.setViewVisibility(ids.percent, View.GONE)
                views.setViewVisibility(ids.auxiliary, View.GONE)
                return
            }

            views.setTextViewText(
                ids.primary,
                data.primaryMetric?.let { if (data.isFallback) cached(it) else metric(it) }
                    ?: "暂无可展示数据"
            )

            val carousel = context.getSharedPreferences(CAROUSEL_PREFS, Context.MODE_PRIVATE)
            val usageIndex = carousel.getInt("${prefix}_carousel_index", 0)
            val usage = data.usageMetrics.takeIf { it.isNotEmpty() }
                ?.get(usageIndex % data.usageMetrics.size)
            views.setTextViewText(
                ids.usage,
                usage?.let { if (data.isFallback) cached(it) else metric(it) }
                    ?: "近期消耗 暂无可计算数据"
            )
            views.setViewVisibility(ids.usage, View.VISIBLE)
            if (usage != null) {
                carousel.edit().putInt("${prefix}_carousel_index", (usageIndex + 1) % 1000).apply()
            }

            if (data.percentage != null && data.percentageLabel != null) {
                views.setViewVisibility(ids.progress, View.VISIBLE)
                views.setViewVisibility(ids.percent, View.VISIBLE)
                views.setProgressBar(ids.progress, 100, data.percentage.coerceIn(0, 100), false)
                val label = if (data.isFallback) "缓存·${strip(data.percentageLabel)}"
                else data.percentageLabel
                views.setTextViewText(ids.percent, "$label ${data.percentage}%")
            } else {
                views.setViewVisibility(ids.progress, View.GONE)
                views.setViewVisibility(ids.percent, View.GONE)
                views.setTextViewText(ids.percent, "")
            }

            val auxIndex = carousel.getInt("${prefix}_aux_carousel_index", 0)
            if (data.auxiliaryMetrics.isNotEmpty()) {
                val aux = data.auxiliaryMetrics[auxIndex % data.auxiliaryMetrics.size]
                views.setTextViewText(ids.auxiliary, if (data.isFallback) cached(aux) else metric(aux))
                views.setViewVisibility(ids.auxiliary, View.VISIBLE)
                carousel.edit().putInt("${prefix}_aux_carousel_index", (auxIndex + 1) % 1000).apply()
            } else {
                views.setTextViewText(ids.auxiliary, "")
                views.setViewVisibility(ids.auxiliary, View.GONE)
            }
        }

        private fun metric(item: WidgetData.DisplayMetric): String {
            val label = item.label.trim()
            val value = item.value.trim()
            return when {
                label.isBlank() -> value
                value.isBlank() -> label
                else -> "$label $value"
            }
        }

        private fun cached(item: WidgetData.DisplayMetric): String {
            val label = strip(item.label)
            val value = item.value.trim()
            return when {
                label.isBlank() -> "缓存·$value"
                value.isBlank() -> "缓存·$label"
                else -> "缓存·$label $value"
            }
        }

        private fun strip(label: String): String = label.trim()
            .removePrefix("云端·")
            .removePrefix("本地·")
            .removePrefix("缓存·")

        private fun setTitle(views: RemoteViews, prefix: String, config: ApiAccountConfig) {
            val configured = config.enabled &&
                config.apiBase.isNotBlank() &&
                config.apiKey.isNotBlank() &&
                config.model.isNotBlank()
            val title = if (configured) config.model else "未配置"
            views.setTextViewText(ids(prefix).title, title)
        }

        private fun bindRefresh(context: Context, views: RemoteViews, id: Int) {
            val intent = Intent(context, BalanceWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            }
            val pending = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
        }
    }
}
