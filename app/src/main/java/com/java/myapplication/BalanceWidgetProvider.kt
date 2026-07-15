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
            updateAppWidget(context, AppWidgetManager.getInstance(context), id)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.java.myapplication.ACTION_REFRESH"
        private const val LAYOUT_PREFS = "widget_layout_state"
        private const val CAROUSEL_PREFS = "widget_carousel"
        private const val COMPACT_HEIGHT_DP = 160
        private const val SAME_HOST_INTERVAL_MS = 1_000L

        private val generationCounter = AtomicLong(0L)
        private val latestGeneration = ConcurrentHashMap<Int, Long>()

        private data class CardIds(
            val title: Int,
            val primary: Int,
            val usage: Int,
            val percent: Int,
            val progress: Int,
            val auxiliary: Int
        )

        private fun cardIds(prefix: String): CardIds = when (prefix) {
            "mimo" -> CardIds(R.id.mimo_title, R.id.mimo_tokens, R.id.mimo_balance,
                R.id.mimo_percent, R.id.mimo_progress, R.id.mimo_auxiliary)
            "ds" -> CardIds(R.id.ds_title, R.id.ds_tokens, R.id.ds_balance,
                R.id.ds_percent, R.id.ds_progress, R.id.ds_auxiliary)
            "oai" -> CardIds(R.id.oai_title, R.id.oai_tokens, R.id.oai_balance,
                R.id.oai_percent, R.id.oai_progress, R.id.oai_auxiliary)
            else -> CardIds(R.id.kimi_title, R.id.kimi_tokens, R.id.kimi_balance,
                R.id.kimi_percent, R.id.kimi_progress, R.id.kimi_auxiliary)
        }

        private fun prefix(slot: String): String = when (slot) {
            "MiMo" -> "mimo"
            "DeepSeek" -> "ds"
            "OpenAI" -> "oai"
            else -> "kimi"
        }

        private fun saveCompact(context: Context, id: Int, options: Bundle): Boolean {
            val prefs = context.getSharedPreferences(LAYOUT_PREFS, Context.MODE_PRIVATE)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            if (minHeight <= 0) return prefs.getBoolean("compact_$id", false)
            val compact = minHeight < COMPACT_HEIGHT_DP
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
            val generation = generationCounter.incrementAndGet()
            latestGeneration[id] = generation
            val isCompact = compact(context, manager, id)
            val views = RemoteViews(context.packageName, R.layout.widget_balance)
            applyLayout(views, isCompact)

            val prefs = context.getSharedPreferences("api_config", Context.MODE_PRIVATE)
            val loaded = ConfigRepository.loadAllConfigs(prefs)
            val slotIds = listOf("Kimi", "MiMo", "DeepSeek", "OpenAI")
            val configs = slotIds.map { slot ->
                loaded.find { it.id == slot } ?: ApiAccountConfig(
                    id = slot, name = "", apiBase = "", apiKey = "", model = "", enabled = true
                )
            }
            val visibleSlots = if (isCompact) slotIds.take(2) else slotIds
            val visibleConfigs = if (isCompact) configs.take(2) else configs

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
                    val previous = hostTimes[host] ?: 0L
                    val wait = if (host.isNotBlank() && previous > 0L) {
                        (SAME_HOST_INTERVAL_MS - (System.currentTimeMillis() - previous)).coerceAtLeast(0L)
                    } else 0L
                    if (wait > 0L) try { Thread.sleep(wait) } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                    results += fetchData(context, prefs, slot, config)
                    if (host.isNotBlank()) hostTimes[host] = System.currentTimeMillis()
                }

                Handler(Looper.getMainLooper()).post {
                    if (latestGeneration[id] != generation) return@post
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
            val ids = cardIds(prefix)
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
            if (!config.enabled || config.apiBase.isBlank() || config.apiKey.isBlank()) {
                return WidgetData.empty(slot)
            }
            val adapter = AdapterFactory.getAdapter(slot, config.apiBase)
                ?: return WidgetData.error(slot, "当前服务暂未提供账户数据")
            val auth = BackgroundAuthRepository.load(prefs, slot)
            val request = AdapterRequest(
                apiBase = config.apiBase,
                modelApiKey = config.apiKey,
                modelName = config.model,
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
                data.cumulativeUsageRequests != null || data.cumulativeUsageTokens != null ||
                    data.cumulativeUsageActualCost != null -> RecentUsageTracker.CumulativeUsage(
                    requests = data.cumulativeUsageRequests,
                    tokens = data.cumulativeUsageTokens,
                    cost = data.cumulativeUsageActualCost?.toBigDecimalOrNull(),
                    currency = "CNY"
                )
                else -> return data
            }
            val normalized = data.copy(
                cumulativeUsedCalls = null,
                cumulativeUsageRequests = null,
                cumulativeUsageTokens = null,
                cumulativeUsageActualCost = null
            )
            return RecentUsageTracker.apply(
                context = context.applicationContext,
                identity = RecentUsageTracker.identity(
                    provider = slot,
                    apiBase = config.apiBase,
                    apiKey = config.apiKey,
                    modelName = config.model
                ),
                data = normalized,
                cumulative = cumulative
            )
        }

        private fun render(context: Context, views: RemoteViews, data: WidgetData, prefix: String) {
            val ids = cardIds(prefix)
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

            val remote = if (data.isFallback) "云端缓存" else "云端"
            views.setTextViewText(ids.primary, data.primaryMetric?.let { metric(remote, it) } ?: "待刷新·暂无数据")

            val carousel = context.getSharedPreferences(CAROUSEL_PREFS, Context.MODE_PRIVATE)
            val usageIndex = carousel.getInt("${prefix}_carousel_index", 0)
            val usageText = when {
                data.isFallback -> data.usageMetrics.firstOrNull()?.let { metric("缓存", it) }
                    ?: "缓存·上次成功数据"
                data.usageMetrics.isNotEmpty() -> metric(
                    "本地",
                    data.usageMetrics[usageIndex % data.usageMetrics.size]
                )
                else -> "本地·近期消耗统计中…"
            }
            views.setTextViewText(ids.usage, usageText)
            views.setViewVisibility(ids.usage, View.VISIBLE)
            carousel.edit().putInt("${prefix}_carousel_index", (usageIndex + 1) % 1000).apply()

            if (data.percentage != null && data.percentageLabel != null) {
                views.setViewVisibility(ids.progress, View.VISIBLE)
                views.setViewVisibility(ids.percent, View.VISIBLE)
                views.setProgressBar(ids.progress, 100, data.percentage.coerceIn(0, 100), false)
                views.setTextViewText(ids.percent, "本地·${data.percentageLabel} ${data.percentage}%")
            } else {
                views.setViewVisibility(ids.progress, View.GONE)
                views.setViewVisibility(ids.percent, View.GONE)
            }

            val auxIndex = carousel.getInt("${prefix}_aux_carousel_index", 0)
            if (data.auxiliaryMetrics.isNotEmpty()) {
                views.setTextViewText(
                    ids.auxiliary,
                    metric(remote, data.auxiliaryMetrics[auxIndex % data.auxiliaryMetrics.size])
                )
                views.setViewVisibility(ids.auxiliary, View.VISIBLE)
                carousel.edit().putInt("${prefix}_aux_carousel_index", (auxIndex + 1) % 1000).apply()
            } else {
                views.setTextViewText(ids.auxiliary, "")
                views.setViewVisibility(ids.auxiliary, View.GONE)
            }
        }

        private fun metric(source: String, item: WidgetData.DisplayMetric): String {
            val value = item.value.trim()
            return if (value.isBlank()) "$source·${item.label}" else "$source·${item.label} $value"
        }

        private fun setTitle(views: RemoteViews, prefix: String, config: ApiAccountConfig) {
            val title = when {
                config.model.isNotBlank() -> config.model
                config.name.isNotBlank() -> config.name
                else -> "暂无模型"
            }
            views.setTextViewText(cardIds(prefix).title, title)
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
