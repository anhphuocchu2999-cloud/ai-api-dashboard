package com.java.myapplication

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import com.java.myapplication.adapter.AdapterFactory
import com.java.myapplication.adapter.AdapterRequest
import com.java.myapplication.adapter.HostRequestCoordinator
import com.java.myapplication.adapter.WidgetData
import com.java.myapplication.adapter.auth.BackgroundAuthConfig
import com.java.myapplication.adapter.auth.BackgroundAuthRepository
import com.java.myapplication.config.ApiAccountConfig
import com.java.myapplication.config.ConfigRepository
import com.java.myapplication.config.ServiceHostMatcher
import com.java.myapplication.stats.RecentUsageTracker
import com.java.myapplication.webauth.WebAuthProfileRegistry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
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
        val expectedToken = context.getSharedPreferences(REFRESH_TAP_PREFS, Context.MODE_PRIVATE)
            .getString(refreshTokenKey(id), null)
        if (
            id != AppWidgetManager.INVALID_APPWIDGET_ID &&
            expectedToken != null &&
            intent.getStringExtra(EXTRA_REFRESH_TOKEN) == expectedToken
        ) {
            handleRefreshTap(context, AppWidgetManager.getInstance(context), id)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val taps = context.getSharedPreferences(REFRESH_TAP_PREFS, Context.MODE_PRIVATE).edit()
        val layout = context.getSharedPreferences(LAYOUT_PREFS, Context.MODE_PRIVATE).edit()
        val carousel = context.getSharedPreferences(CAROUSEL_PREFS, Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { id ->
            generations.remove(id)
            taps.remove(tapCountKey(id)).remove(tapTimeKey(id)).remove(refreshTokenKey(id))
            layout.remove("compact_$id")
            renderPrefixes.forEach { prefix ->
                carousel.remove("${id}_${prefix}_carousel_index")
                    .remove("${id}_${prefix}_aux_carousel_index")
            }
        }
        taps.apply(); layout.apply(); carousel.apply()
    }

    companion object {
        const val ACTION_REFRESH = "com.java.myapplication.ACTION_REFRESH"
        private const val LAYOUT_PREFS = "widget_layout_state"
        private const val CAROUSEL_PREFS = "widget_carousel"
        private const val REFRESH_TAP_PREFS = "widget_refresh_taps"
        private const val EXTRA_REFRESH_TOKEN = "refresh_token"
        private const val COMPACT_HEIGHT_DP = 160
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
            val root: Int,
            val logo: Int,
            val title: Int,
            val primary: Int,
            val usage: Int,
            val percent: Int,
            val progress: Int,
            val auxiliary: Int,
            val syncing: Int
        )

        private fun ids(prefix: String) = when (prefix) {
            "mimo" -> CardIds(R.id.mimo_card, R.id.mimo_logo,
                R.id.mimo_title, R.id.mimo_tokens, R.id.mimo_balance,
                R.id.mimo_percent, R.id.mimo_progress, R.id.mimo_auxiliary,
                R.id.mimo_syncing)
            "ds" -> CardIds(R.id.ds_card, R.id.ds_logo,
                R.id.ds_title, R.id.ds_tokens, R.id.ds_balance,
                R.id.ds_percent, R.id.ds_progress, R.id.ds_auxiliary,
                R.id.ds_syncing)
            "oai" -> CardIds(R.id.oai_card, R.id.oai_logo,
                R.id.oai_title, R.id.oai_tokens, R.id.oai_balance,
                R.id.oai_percent, R.id.oai_progress, R.id.oai_auxiliary,
                R.id.oai_syncing)
            else -> CardIds(R.id.kimi_card, R.id.kimi_logo,
                R.id.kimi_title, R.id.kimi_tokens, R.id.kimi_balance,
                R.id.kimi_percent, R.id.kimi_progress, R.id.kimi_auxiliary,
                R.id.kimi_syncing)
        }

        private val renderPrefixes = listOf("kimi", "mimo", "ds", "oai")

        private fun tapCountKey(id: Int) = "count_$id"

        private fun tapTimeKey(id: Int) = "time_$id"

        private fun refreshTokenKey(id: Int) = "token_$id"

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

        private fun applyLayout(views: RemoteViews, compact: Boolean, visibleCount: Int) {
            views.setViewVisibility(R.id.row_primary, if (visibleCount == 0) View.GONE else View.VISIBLE)
            views.setViewVisibility(
                R.id.row_secondary,
                if (compact || visibleCount <= 2) View.GONE else View.VISIBLE
            )
        }

        internal fun updateAppWidget(context: Context, manager: AppWidgetManager, id: Int) {
            clearRefreshTapState(context, id)
            val generation = counter.incrementAndGet()
            generations[id] = generation
            val compact = compact(context, manager, id)
            val views = RemoteViews(context.packageName, R.layout.widget_balance)

            val prefs = context.getSharedPreferences("api_config", Context.MODE_PRIVATE)
            val loaded = ConfigRepository.loadAllConfigs(prefs)
            val slots = listOf("Kimi", "MiMo", "DeepSeek", "OpenAI")
            val configs = slots.map { slot ->
                loaded.find { it.id == slot } ?: ApiAccountConfig(
                    id = slot, name = "", apiBase = "", apiKey = "", model = "", enabled = true
                )
            }
            val enabledEntries = slots.zip(configs).filter { (_, config) -> config.enabled }
            val visibleEntries = enabledEntries.take(if (compact) 2 else 4)
            val targets = renderPrefixes.take(if (compact) 2 else 4)
            applyLayout(views, compact, visibleEntries.size)
            targets.forEachIndexed { index, target ->
                val entry = visibleEntries.getOrNull(index)
                views.setViewVisibility(ids(target).root, if (entry == null) View.GONE else View.VISIBLE)
                if (entry != null) {
                    setTitle(views, target, entry.second)
                    applyBrand(views, target, entry.first, entry.second)
                }
                setSyncing(views, target, false)
            }

            // 刷新开始时只局部显示卡片角标，不重绘整张 Widget。
            // 这样桌面会继续保留上一次渲染的数据，直到新结果准备完毕。
            val syncingViews = RemoteViews(context.packageName, R.layout.widget_balance)
            renderPrefixes.forEach { target -> setSyncing(syncingViews, target, false) }
            visibleEntries.forEachIndexed { index, (_, config) ->
                if (isConfigured(config)) setSyncing(syncingViews, targets[index], true)
            }
            syncingViews.setTextViewText(R.id.click_count, "")
            bindRefresh(context, syncingViews, id)
            manager.partiallyUpdateAppWidget(id, syncingViews)

            Thread {
                val results = mutableListOf<WidgetData>()
                try {
                    visibleEntries.forEach { (slot, config) ->
                        if (generations[id] != generation) return@Thread
                        results += fetchData(context, prefs, slot, config)
                    }
                } catch (_: Throwable) {
                    while (results.size < visibleEntries.size) {
                        results += WidgetData.error(visibleEntries[results.size].first, "读取失败，请稍后重试")
                    }
                }

                Handler(Looper.getMainLooper()).post {
                    if (generations[id] != generation) return@post
                    applyLayout(views, compact(context, manager, id), visibleEntries.size)
                    results.forEachIndexed { index, data ->
                        val p = targets[index]
                        setTitle(views, p, visibleEntries[index].second)
                        applyBrand(views, p, visibleEntries[index].first, visibleEntries[index].second)
                        render(context, views, data, p, id)
                        setSyncing(
                            views,
                            p,
                            data.isFallback || !data.isSuccess || !data.isAvailable
                        )
                    }
                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    views.setTextViewText(R.id.update_time, "更新 $time")
                    views.setTextViewText(R.id.click_count, "")
                    bindRefresh(context, views, id)
                    manager.updateAppWidget(id, views)
                }
            }.start()
        }

        private fun setSyncing(views: RemoteViews, prefix: String, syncing: Boolean) {
            val syncingId = ids(prefix).syncing
            views.setTextViewText(syncingId, if (syncing) "😂 数据在路上～" else "")
            views.setViewVisibility(syncingId, if (syncing) View.VISIBLE else View.GONE)
        }

        private fun isConfigured(config: ApiAccountConfig): Boolean =
            config.enabled &&
                config.apiBase.isNotBlank() &&
                config.apiKey.isNotBlank() &&
                config.model.isNotBlank()

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
            val adapter = AdapterFactory.getAdapter(slot, config.apiBase, slot)
                ?: return WidgetData.error(slot, "当前服务暂未提供账户数据")
            val auth = loadBoundAuthorization(prefs, slot, config.apiBase)
            val request = AdapterRequest(
                apiBase = config.apiBase,
                modelApiKey = config.apiKey,
                modelName = config.model,
                instanceId = slot,
                backgroundAuthType = auth.authType,
                backgroundCredential = auth.authValue
            )
            val raw = try { HostRequestCoordinator.withHost(config.apiBase) { adapter.fetchData(request) } } catch (_: Exception) {
                WidgetData.error(slot, "获取失败")
            }
            return applyRecentUsage(context, slot, config, raw)
        }

        private fun loadBoundAuthorization(
            prefs: android.content.SharedPreferences,
            slot: String,
            apiBase: String
        ): BackgroundAuthConfig {
            val profile = WebAuthProfileRegistry.findFor(slot, apiBase)
                ?: return BackgroundAuthConfig()
            val auth = BackgroundAuthRepository.load(prefs, slot)
            return auth.takeIf {
                it.enabled &&
                    it.authType == profile.authType &&
                    it.authValue.isNotBlank()
            } ?: BackgroundAuthConfig()
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

        private fun render(context: Context, views: RemoteViews, data: WidgetData, prefix: String, appWidgetId: Int) {
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
                data.primaryMetric?.let(::metric)
                    ?: "暂无可展示数据"
            )

            val carousel = context.getSharedPreferences(CAROUSEL_PREFS, Context.MODE_PRIVATE)
            val usageKey = "${appWidgetId}_${prefix}_carousel_index"
            val usageIndex = carousel.getInt(usageKey, 0)
            val usage = data.usageMetrics.takeIf { it.isNotEmpty() }
                ?.get(usageIndex % data.usageMetrics.size)
            views.setTextViewText(
                ids.usage,
                usage?.let(::metric)
                    ?: "近期消耗 暂无可计算数据"
            )
            views.setViewVisibility(ids.usage, View.VISIBLE)
            if (usage != null) {
                carousel.edit().putInt(usageKey, (usageIndex + 1) % 1000).apply()
            }

            if (data.percentage != null && data.percentageLabel != null) {
                views.setViewVisibility(ids.progress, View.VISIBLE)
                views.setViewVisibility(ids.percent, View.VISIBLE)
                views.setProgressBar(ids.progress, 100, data.percentage.coerceIn(0, 100), false)
                views.setTextViewText(
                    ids.percent,
                    "${strip(data.percentageLabel)} ${data.percentage}%"
                )
            } else {
                views.setViewVisibility(ids.progress, View.GONE)
                views.setViewVisibility(ids.percent, View.GONE)
                views.setTextViewText(ids.percent, "")
            }

            val auxKey = "${appWidgetId}_${prefix}_aux_carousel_index"
            val auxIndex = carousel.getInt(auxKey, 0)
            if (data.auxiliaryMetrics.isNotEmpty()) {
                val aux = data.auxiliaryMetrics[auxIndex % data.auxiliaryMetrics.size]
                views.setTextViewText(ids.auxiliary, metric(aux))
                views.setViewVisibility(ids.auxiliary, View.VISIBLE)
                carousel.edit().putInt(auxKey, (auxIndex + 1) % 1000).apply()
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

        private fun strip(label: String): String = label.trim()
            .removePrefix("云端·")
            .removePrefix("本地·")
            .removePrefix("缓存·")
            .removePrefix("网页·")

        private fun setTitle(views: RemoteViews, prefix: String, config: ApiAccountConfig) {
            val title = if (isConfigured(config)) config.model else "未配置"
            views.setTextViewText(ids(prefix).title, title)
        }

        private fun applyBrand(
            views: RemoteViews,
            prefix: String,
            slot: String,
            config: ApiAccountConfig
        ) {
            val cardIds = ids(prefix)
            val profileId = WebAuthProfileRegistry.findFor(slot, config.apiBase)?.profileId
            val logo = when {
                profileId == "mimo" || ServiceHostMatcher.matches(config.apiBase, "xiaomimimo.com") -> R.drawable.logo_mimo
                profileId == "deepseek" || ServiceHostMatcher.matches(config.apiBase, "deepseek.com") -> R.drawable.logo_deepseek
                ServiceHostMatcher.matches(config.apiBase, "coolyeah.net") -> R.drawable.logo_kimi
                ServiceHostMatcher.matches(config.apiBase, "openai.com") -> R.drawable.logo_openai
                else -> null
            }
            views.setViewVisibility(cardIds.logo, if (logo == null) View.GONE else View.VISIBLE)
            logo?.let { views.setImageViewResource(cardIds.logo, it) }

            val accent = when (logo) {
                R.drawable.logo_kimi -> Color.rgb(52, 199, 89)
                R.drawable.logo_mimo -> Color.rgb(0, 122, 255)
                R.drawable.logo_deepseek -> Color.rgb(255, 149, 0)
                R.drawable.logo_openai -> Color.rgb(175, 82, 222)
                else -> Color.rgb(142, 142, 147)
            }
            views.setTextColor(cardIds.primary, accent)
            views.setTextColor(cardIds.percent, accent)
        }

        private fun bindRefresh(context: Context, views: RemoteViews, id: Int) {
            val prefs = context.getSharedPreferences(REFRESH_TAP_PREFS, Context.MODE_PRIVATE)
            val token = prefs.getString(refreshTokenKey(id), null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(refreshTokenKey(id), it).commit()
            }
            val intent = Intent(context, BalanceWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(EXTRA_REFRESH_TOKEN, token)
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

