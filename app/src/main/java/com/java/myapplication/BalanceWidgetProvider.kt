package com.java.myapplication

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import com.java.myapplication.adapter.AdapterFactory
import com.java.myapplication.adapter.AdapterRequest
import com.java.myapplication.adapter.WidgetData
import com.java.myapplication.adapter.auth.BackgroundAuthConfig
import com.java.myapplication.adapter.auth.BackgroundAuthType
import com.java.myapplication.config.ApiAccountConfig
import com.java.myapplication.config.ConfigRepository
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class BalanceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        saveCompactModeFromOptions(context, appWidgetId, newOptions)
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                handleRefreshClick(context, appWidgetManager, appWidgetId)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.java.myapplication.ACTION_REFRESH"
        private const val COMPACT_HEIGHT_BREAKPOINT_DP = 160
        private const val LAYOUT_PREFS_NAME = "widget_layout_state"
        private val updateCounter = AtomicLong(0L)
        private val latestUpdateByWidget = ConcurrentHashMap<Int, Long>()

        private fun layoutKey(appWidgetId: Int) = "compact_$appWidgetId"

        private fun saveCompactModeFromOptions(
            context: Context,
            appWidgetId: Int,
            options: Bundle
        ): Boolean {
            val prefs = context.getSharedPreferences(LAYOUT_PREFS_NAME, Context.MODE_PRIVATE)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            if (minHeight <= 0) {
                return prefs.getBoolean(layoutKey(appWidgetId), false)
            }

            val compactMode = minHeight < COMPACT_HEIGHT_BREAKPOINT_DP
            prefs.edit().putBoolean(layoutKey(appWidgetId), compactMode).apply()
            return compactMode
        }

        private fun currentCompactMode(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ): Boolean {
            val prefs = context.getSharedPreferences(LAYOUT_PREFS_NAME, Context.MODE_PRIVATE)
            val key = layoutKey(appWidgetId)
            if (prefs.contains(key)) {
                return prefs.getBoolean(key, false)
            }
            return saveCompactModeFromOptions(
                context,
                appWidgetId,
                appWidgetManager.getAppWidgetOptions(appWidgetId)
            )
        }

        private fun beginUpdate(appWidgetId: Int): Long {
            val generation = updateCounter.incrementAndGet()
            latestUpdateByWidget[appWidgetId] = generation
            return generation
        }

        private fun isLatestUpdate(appWidgetId: Int, generation: Long): Boolean {
            return latestUpdateByWidget[appWidgetId] == generation
        }

        private fun applyResponsiveLayout(views: RemoteViews, compactMode: Boolean) {
            views.setViewVisibility(
                R.id.row_secondary,
                if (compactMode) android.view.View.GONE else android.view.View.VISIBLE
            )
        }

        // 连续点击计数（全局）
        private var clickCount = 0
        private var lastClickTime = 0L

        private val toastMessages = listOf(
            "嘿嘿，再点 7 下才肯刷新～",
            "还差 6 下，手速不错哦",
            "还差 5 下，服务器正在装睡 💤",
            "还差 4 下，已经成功一半啦",
            "还差 3 下，继续继续～",
            "还差 2 下，马上叫醒它",
            "最后 1 下！准备发车 🚗"
        )

        private fun handleRefreshClick(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val now = System.currentTimeMillis()
            val prefs = context.getSharedPreferences("api_config", Context.MODE_PRIVATE)
            val keyCount = "refresh_click_count"
            val keyTime = "refresh_click_time"

            val lastTime = prefs.getLong(keyTime, 0L)
            var count = prefs.getInt(keyCount, 0)

            // 超过3秒重置计数
            if (now - lastTime > 3000L) {
                count = 0
            }

            count++
            prefs.edit().putInt(keyCount, count).putLong(keyTime, now).apply()

            if (count < 8) {
                // 前7次显示俏皮文案，不请求服务器
                val message = toastMessages[count - 1]
                val views = RemoteViews(context.packageName, R.layout.widget_balance)
                applyResponsiveLayout(
                    views,
                    currentCompactMode(context, appWidgetManager, appWidgetId)
                )
                views.setTextViewText(R.id.click_count, message)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            // 第8次：清零计数并执行真实刷新
            prefs.edit().putInt(keyCount, 0).apply()
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }

        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val generation = beginUpdate(appWidgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_balance)
            val compactMode = currentCompactMode(context, appWidgetManager, appWidgetId)
            applyResponsiveLayout(views, compactMode)
            val random = Random()

            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val updateTime = timeFormat.format(Date())

            // 读取配置（每次刷新都重新读取，不使用缓存）
            val prefs = context.getSharedPreferences("api_config", Context.MODE_PRIVATE)

            // 通过 ConfigRepository 加载所有配置（自动兼容新旧格式）
            val apiConfigs = ConfigRepository.loadAllConfigs(prefs)
            val enabledConfigs = apiConfigs.filter { it.enabled }

            // 固定四个槽位，按顺序对应 Kimi/MiMo/DeepSeek/OpenAI
            val slotIds = listOf("Kimi", "MiMo", "DeepSeek", "OpenAI")
            val allConfigs = slotIds.map { slotId ->
                apiConfigs.find { it.id == slotId }
                    ?: ApiAccountConfig(id = slotId, name = "", apiBase = "", apiKey = "", model = "", enabled = true)
            }
            val platforms = if (compactMode) slotIds.take(2) else slotIds
            val configs = if (compactMode) allConfigs.take(2) else allConfigs

            // 显示加载中状态
            views.setTextViewText(R.id.kimi_tokens, "加载中...")
            views.setTextViewText(R.id.kimi_balance, "")
            views.setTextViewText(R.id.kimi_percent, "")
            views.setProgressBar(R.id.kimi_progress, 100, 0, false)
            appWidgetManager.updateAppWidget(appWidgetId, views)

            // 后台请求所有平台数据（顺序请求，同一 host 间隔 1000ms）
            Thread {
                val results = mutableListOf<WidgetData>()
                val lastHostTimes = mutableMapOf<String, Long>()
                
                platforms.forEachIndexed { index, platformName ->
                    val config = configs[index]
                    
                    // 计算同一 host 的请求间隔
                    val host = config?.apiBase?.let { 
                        try {
                            java.net.URL(it.trim().trimEnd('/')).host
                        } catch (_: Exception) { "" }
                    } ?: ""
                    
                    val lastTime = lastHostTimes[host] ?: 0L
                    val now = System.currentTimeMillis()
                    val delay = if (host.isNotBlank() && lastTime > 0) {
                        val needed = 1000L - (now - lastTime)
                        if (needed > 0) needed else 0L
                    } else 0L
                    
                    if (delay > 0) {
                        try { Thread.sleep(delay) } catch (_: InterruptedException) {}
                    }
                    
                    val result = fetchWidgetDataForPlatform(platformName, config, prefs, context)
                    results.add(result)
                    
                    if (host.isNotBlank()) {
                        lastHostTimes[host] = System.currentTimeMillis()
                    }
                }
                
                // 如果所有结果都是错误，尝试使用缓存
                if (results.all { !it.isAvailable || it.isEmpty() }) {
                    platforms.forEachIndexed { index, platformName ->
                        val cached = cachedData[platformName]
                        val lastTime = lastSuccessTime[platformName] ?: 0L
                        val cacheAge = System.currentTimeMillis() - lastTime
                        if (cached != null && cached.isAvailable && cacheAge < CACHE_VALID_MS) {
                            results[index] = cached.copy(
                                modelName = cached.modelName?.let { "$it (稍后刷新)" } ?: "稍后刷新"
                            )
                        }
                    }
                }

                Handler(Looper.getMainLooper()).post {
                    if (!isLatestUpdate(appWidgetId, generation)) {
                        return@post
                    }

                    applyResponsiveLayout(
                        views,
                        currentCompactMode(context, appWidgetManager, appWidgetId)
                    )

                    // 渲染当前模式的平台
                    results.forEachIndexed { index, result ->
                        val prefix = when (platforms[index]) {
                            "Kimi" -> "kimi"
                            "MiMo" -> "mimo"
                            "DeepSeek" -> "ds"
                            "OpenAI" -> "oai"
                            else -> "kimi"
                        }
                        // 更新卡片标题（从配置中读取真实模型名或用户自定义名）
                        updateCardTitle(views, prefix, configs[index])
                        renderWidgetData(context, views, result, prefix)
                    }

                    views.setTextViewText(R.id.update_time, "Updated: $updateTime")
                    setupWidgetClick(context, views, appWidgetId)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }.start()
        }

        private fun formatNumber(value: Double): String {
            return when {
                value >= 1000000 -> "%.2fM".format(value / 1000000)
                value >= 1000 -> "%.2fK".format(value / 1000)
                value == value.toLong().toDouble() -> "%,d".format(value.toLong())
                else -> "%.2f".format(value)
            }
        }

        /**
         * 五层卡片数据展示引擎
         * 
         * 第1层：模型名称（固定，由 updateCardTitle 处理）
         * 第2层：Core 1 固定核心指标（primaryMetric → tokensId）
         * 第3层：Core 2 动态轮播位①（usageMetrics → balanceId）
         * 第4层：百分比视觉层（percentage + percentageLabel → progressId + percentId）
         * 第5层：B级辅助轮播位②（auxiliaryMetrics → auxiliaryId）
         * 
         * 状态：statusText（固定底部角标）
         */
        private fun renderWidgetData(context: Context, views: RemoteViews, data: WidgetData, prefix: String) {
            val tokensId = when (prefix) {
                "kimi" -> R.id.kimi_tokens
                "mimo" -> R.id.mimo_tokens
                "ds" -> R.id.ds_tokens
                "oai" -> R.id.oai_tokens
                else -> R.id.kimi_tokens
            }
            val balanceId = when (prefix) {
                "kimi" -> R.id.kimi_balance
                "mimo" -> R.id.mimo_balance
                "ds" -> R.id.ds_balance
                "oai" -> R.id.oai_balance
                else -> R.id.kimi_balance
            }
            val percentId = when (prefix) {
                "kimi" -> R.id.kimi_percent
                "mimo" -> R.id.mimo_percent
                "ds" -> R.id.ds_percent
                "oai" -> R.id.oai_percent
                else -> R.id.kimi_percent
            }
            val progressId = when (prefix) {
                "kimi" -> R.id.kimi_progress
                "mimo" -> R.id.mimo_progress
                "ds" -> R.id.ds_progress
                "oai" -> R.id.oai_progress
                else -> R.id.kimi_progress
            }
            val auxiliaryId = when (prefix) {
                "kimi" -> R.id.kimi_auxiliary
                "mimo" -> R.id.mimo_auxiliary
                "ds" -> R.id.ds_auxiliary
                "oai" -> R.id.oai_auxiliary
                else -> R.id.kimi_auxiliary
            }

            android.util.Log.d("BalanceWidgetProvider", "prefix=$prefix, usageMetrics.size=${data.usageMetrics.size}, usageMetrics.first=${data.usageMetrics.firstOrNull()?.label ?: "null"}")

            // 错误/空状态：显示具体错误或"未配置"，隐藏所有动态区域
            if (!data.isSuccess || (!data.isAvailable && data.errorMessage != null)) {
                val errorMsg = data.statusText ?: data.errorMessage ?: "未配置"
                views.setTextViewText(tokensId, errorMsg)
                views.setTextViewText(balanceId, "")
                views.setTextViewText(percentId, "")
                views.setTextViewText(auxiliaryId, "")
                views.setViewVisibility(progressId, android.view.View.GONE)
                views.setViewVisibility(percentId, android.view.View.GONE)
                views.setViewVisibility(auxiliaryId, android.view.View.GONE)
                return
            }

            // 兼容旧逻辑：如果新字段为空，回退到旧字段
            val hasNewFields = data.primaryMetric != null || data.usageMetrics.isNotEmpty() ||
                               data.percentage != null || data.auxiliaryMetrics.isNotEmpty()
            
            if (!hasNewFields) {
                // 回退到旧渲染逻辑
                renderLegacyWidgetData(views, data, prefix)
                return
            }

            // ===== 第2层：Core 1 固定核心指标 =====
            val core1Text = data.primaryMetric?.let { "${it.label} ${it.value}" } ?: ""
            views.setTextViewText(tokensId, core1Text)
            // ===== 第3层：Core 2 动态轮播位①（usageMetrics → balanceId）=====
            // 轮播索引从 SharedPreferences 读取，各平台独立
            val carouselPrefs = context.getSharedPreferences("widget_carousel", Context.MODE_PRIVATE)
            val carouselIndex = carouselPrefs.getInt("${prefix}_carousel_index", 0)
            
            // 等待态文案（MiMo / DeepSeek 无真实数据时显示）
            val waitingMessages = listOf(
                "近期用量统计中…",
                "小账本正在悄悄记账～",
                "数据正在排队入场～",
                "第一份趋势正在路上 🚚"
            )
            
            // 确定 Core 2 显示内容
            val core2Text: String
            val hasRealData = data.usageMetrics.isNotEmpty()
            if (hasRealData) {
                // 真实数据：按索引轮播
                val idx = carouselIndex % data.usageMetrics.size
                val metric = data.usageMetrics[idx]
                core2Text = "${metric.label} ${metric.value}"
            } else {
                // 无真实数据：等待态轮播（MiMo / DeepSeek）
                val idx = carouselIndex % waitingMessages.size
                core2Text = waitingMessages[idx]
            }
            
            views.setTextViewText(balanceId, core2Text)
            views.setViewVisibility(balanceId, android.view.View.VISIBLE)
            
            // 更新轮播索引（下次刷新时切换）
            carouselPrefs.edit().putInt("${prefix}_carousel_index", (carouselIndex + 1) % 1000).apply()

            // ===== 第4层：百分比视觉层（percentage → progressId + percentId）=====
            val hasPercentage = data.percentage != null && data.percentageLabel != null
            if (hasPercentage) {
                views.setViewVisibility(progressId, android.view.View.VISIBLE)
                views.setViewVisibility(percentId, android.view.View.VISIBLE)
                views.setProgressBar(progressId, 100, data.percentage!!.coerceIn(0, 100), false)
                views.setTextViewText(percentId, "${data.percentageLabel} ${data.percentage}%")
            } else {
                views.setViewVisibility(progressId, android.view.View.GONE)
                views.setViewVisibility(percentId, android.view.View.GONE)
                views.setTextViewText(percentId, "")
            }

            // ===== 第5层：B级辅助轮播位②（auxiliaryMetrics → auxiliaryId）=====
            // 独立轮播索引，与 Core 2 完全隔离
            val auxCarouselIndex = carouselPrefs.getInt("${prefix}_aux_carousel_index", 0)
            
            if (data.auxiliaryMetrics.isNotEmpty()) {
                // 有真实辅助数据：按独立索引轮播
                val auxIdx = auxCarouselIndex % data.auxiliaryMetrics.size
                val auxMetric = data.auxiliaryMetrics[auxIdx]
                views.setTextViewText(auxiliaryId, "${auxMetric.label} ${auxMetric.value}")
                views.setViewVisibility(auxiliaryId, android.view.View.VISIBLE)
                // 更新独立轮播索引
                carouselPrefs.edit().putInt("${prefix}_aux_carousel_index", (auxCarouselIndex + 1) % 1000).apply()
            } else {
                // 无辅助数据：隐藏槽位
                views.setTextViewText(auxiliaryId, "")
                views.setViewVisibility(auxiliaryId, android.view.View.GONE)
            }
        }

        /**
         * 兼容旧数据模型的渲染逻辑
         */
        private fun renderLegacyWidgetData(views: RemoteViews, data: WidgetData, prefix: String) {
            val tokensId = when (prefix) {
                "kimi" -> R.id.kimi_tokens
                "mimo" -> R.id.mimo_tokens
                "ds" -> R.id.ds_tokens
                "oai" -> R.id.oai_tokens
                else -> R.id.kimi_tokens
            }
            val balanceId = when (prefix) {
                "kimi" -> R.id.kimi_balance
                "mimo" -> R.id.mimo_balance
                "ds" -> R.id.ds_balance
                "oai" -> R.id.oai_balance
                else -> R.id.kimi_balance
            }
            val percentId = when (prefix) {
                "kimi" -> R.id.kimi_percent
                "mimo" -> R.id.mimo_percent
                "ds" -> R.id.ds_percent
                "oai" -> R.id.oai_percent
                else -> R.id.kimi_percent
            }
            val progressId = when (prefix) {
                "kimi" -> R.id.kimi_progress
                "mimo" -> R.id.mimo_progress
                "ds" -> R.id.ds_progress
                "oai" -> R.id.oai_progress
                else -> R.id.kimi_progress
            }

            val primary = data.getPrimaryDisplay()
            val secondary = data.getSecondaryDisplay()
            val modelName = data.getModelDisplay()
            val percent = data.getPercentDisplay()

            val line1 = primary ?: ""
            val line2 = secondary ?: ""
            val line3 = modelName ?: ""
            val line4 = percent ?: ""

            views.setTextViewText(tokensId, line1)
            views.setTextViewText(balanceId, if (line2.isNotBlank()) line2 else line3)
            
            if (data.hasUsagePercent()) {
                views.setViewVisibility(progressId, android.view.View.VISIBLE)
                views.setViewVisibility(percentId, android.view.View.VISIBLE)
                views.setProgressBar(progressId, 100, data.getProgressValue(), false)
                views.setTextViewText(percentId, line4)
            } else {
                views.setViewVisibility(progressId, android.view.View.GONE)
                views.setViewVisibility(percentId, android.view.View.GONE)
            }
        }

        /**
         * 获取单个平台的数据
         * 统一处理：未配置、未启用、无 Adapter、fetchData 成功/失败
         */
        // 缓存上次成功数据，避免短暂错误覆盖有效数据
        private val cachedData = mutableMapOf<String, WidgetData>()
        private val lastSuccessTime = mutableMapOf<String, Long>()
        private const val CACHE_VALID_MS = 60000L // 缓存有效期 60 秒

        private fun loadBackgroundAuth(
            prefs: android.content.SharedPreferences,
            platformName: String
        ): BackgroundAuthConfig {
            val authJson = prefs.getString("${platformName}_auth", null)
            if (authJson.isNullOrBlank()) {
                return BackgroundAuthConfig()
            }

            return try {
                val obj = org.json.JSONObject(authJson)
                val enabled = obj.optBoolean("enabled", false)
                if (!enabled) {
                    BackgroundAuthConfig()
                } else {
                    val authType = try {
                        BackgroundAuthType.valueOf(
                            obj.optString("authType", BackgroundAuthType.NONE.name)
                                .uppercase(Locale.ROOT)
                        )
                    } catch (_: Exception) {
                        BackgroundAuthType.NONE
                    }

                    BackgroundAuthConfig(
                        authType = authType,
                        authValue = obj.optString("authValue", ""),
                        enabled = authType != BackgroundAuthType.NONE,
                        updatedAt = obj.optLong("updatedAt", 0L)
                    )
                }
            } catch (_: Exception) {
                BackgroundAuthConfig()
            }
        }

        private fun fetchWidgetDataForPlatform(
            platformName: String,
            config: ApiAccountConfig,
            prefs: android.content.SharedPreferences,
            context: Context
        ): WidgetData {
            // 未启用
            if (!config.enabled) {
                return WidgetData.empty(platformName)
            }

            // 缺少 apiBase 或 apiKey
            if (config.apiBase.isBlank() || config.apiKey.isBlank()) {
                return WidgetData.empty(platformName)
            }

            // 所有 Adapter 统一通过 AdapterFactory 路由。
            val adapter = AdapterFactory.getAdapter(platformName, config.apiBase)
            if (adapter == null) {
                // 模型连接正常但余额接口需要登录授权或当前无对应数据 Adapter。
                return WidgetData(
                    platformName = platformName,
                    modelName = "余额需登录授权",
                    displayLabel = null,
                    total = null,
                    used = null,
                    remaining = null,
                    usagePercent = null,
                    isAvailable = true
                )
            }

            val backgroundAuth = loadBackgroundAuth(prefs, platformName)
            val request = AdapterRequest(
                apiBase = config.apiBase,
                modelApiKey = config.apiKey,
                modelName = config.model,
                backgroundAuthType = backgroundAuth.authType,
                backgroundCredential = backgroundAuth.authValue
            )

            // 只记录认证类型，不记录凭据值、长度或原始内容。
            android.util.Log.d(
                "BalanceWidgetProvider",
                "平台=$platformName, 后台授权=${request.backgroundAuthType.name}, 模型Key=${if (request.modelApiKey.isBlank()) "未配置" else "已配置"}"
            )

            val result = try {
                adapter.fetchData(request)
            } catch (e: Exception) {
                WidgetData.error(platformName, "获取失败")
            }

            // Kimi 本地快照统计处理
            val processedResult = if (platformName == "Kimi" && result.cumulativeUsedCalls != null) {
                computeKimiUsageMetrics(context, result, config.model)
            } else if (platformName == "OpenAI" && result.cumulativeUsageRequests != null) {
                computeAihuangniuUsageMetrics(context, result, config.model)
            } else {
                result
            }

            // 如果请求成功，更新缓存
            if (processedResult.isAvailable && !processedResult.isEmpty()) {
                cachedData[platformName] = processedResult
                lastSuccessTime[platformName] = System.currentTimeMillis()
                return processedResult
            }

            // 如果请求失败但缓存有效，返回缓存数据
            val cached = cachedData[platformName]
            val lastTime = lastSuccessTime[platformName] ?: 0L
            val cacheAge = System.currentTimeMillis() - lastTime
            
            if (cached != null && cached.isAvailable && cacheAge < CACHE_VALID_MS) {
                // 返回缓存数据，但标记为"稍后刷新"
                return cached.copy(
                    modelName = cached.modelName?.let { "$it (稍后刷新)" } ?: "稍后刷新"
                )
            }

            // 没有有效缓存，返回错误
            return processedResult
        }

        /**
         * 更新卡片标题
         * 显示规则：
         * - model 有值：显示 model（真实模型名）
         * - model 为空：显示 "未配置"
         * 禁止显示固定平台名（Kimi/MiMo/DeepSeek/OpenAI）
         */
        private fun updateCardTitle(views: RemoteViews, prefix: String, config: ApiAccountConfig) {
            val titleId = when (prefix) {
                "kimi" -> R.id.kimi_title
                "mimo" -> R.id.mimo_title
                "ds" -> R.id.ds_title
                "oai" -> R.id.oai_title
                else -> R.id.kimi_title
            }

            val title = when {
                // model 有值：显示真实模型名
                config.model.isNotBlank() -> config.model
                // model 为空：显示暂无模型
                else -> "暂无模型"
            }

            views.setTextViewText(titleId, title)
        }

        private fun setupWidgetClick(context: Context, views: RemoteViews, appWidgetId: Int) {
            val refreshIntent = Intent(context, BalanceWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // 点击 Widget 任意位置触发刷新
            views.setOnClickPendingIntent(R.id.widget_root, refreshPendingIntent)
        }

        private fun parseWidgetJson(json: String): WidgetConfig {
            val result = mutableMapOf<String, String>()
            val clean = json.trim().removePrefix("{").removeSuffix("}")
            var i = 0
            while (i < clean.length) {
                val keyStart = clean.indexOf('"', i)
                if (keyStart == -1) break
                val keyEnd = clean.indexOf('"', keyStart + 1)
                if (keyEnd == -1) break
                val key = clean.substring(keyStart + 1, keyEnd)
                val colon = clean.indexOf(':', keyEnd + 1)
                if (colon == -1) break
                var valueStart = colon + 1
                while (valueStart < clean.length && clean[valueStart] == ' ') valueStart++
                val value: String
                if (clean[valueStart] == '"') {
                    val valueEnd = clean.indexOf('"', valueStart + 1)
                    value = clean.substring(valueStart + 1, valueEnd)
                    i = valueEnd + 1
                } else {
                    val comma = clean.indexOf(',', valueStart)
                    val end = if (comma == -1) clean.length else comma
                    value = clean.substring(valueStart, end).trim()
                    i = if (comma == -1) clean.length else comma + 1
                }
                result[key] = value
            }
            return WidgetConfig(
                name = result["name"] ?: "",
                apiBase = result["apiBase"] ?: "",
                apiKey = result["apiKey"] ?: "",
                model = result["model"] ?: "",
                enabled = (result["enabled"] ?: "true") == "true"
            )
        }

        // ==================== 以下代码已迁移到 adapter/NewApiAdapter.kt ====================
        // 保留 parseWidgetJson 和 WidgetConfig 供配置解析使用
        // 探测逻辑和 Kimi 专用接口请求已抽离到 NewApiAdapter
        // ================================================================================

        /**
         * 接口能力探测：尝试多个接口，根据返回字段自动判断数据类型
         */
        private fun probeAndFetch(apiBase: String, apiKey: String): ProbeResult {
            val normalizedBase = apiBase.trim().trimEnd('/')
            val probeResults = mutableMapOf<String, ProbeResponse>()

            // 探测的接口列表
            val endpoints = listOf(
                "/v1/dashboard/billing/subscription",
                "/v1/dashboard/billing/usage",
                "/v1/user/info",
                "/v1/user/balance",
                "/v1/models"
            )

            for (endpoint in endpoints) {
                val result = tryFetch("$normalizedBase$endpoint", apiKey)
                probeResults[endpoint] = result
            }

            // 分析返回字段，判断数据类型
            return analyzeProbeResults(probeResults)
        }

        private fun tryFetch(url: String, apiKey: String): ProbeResponse {
            return try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val responseCode = conn.responseCode
                val response = if (responseCode == 200) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    val errorText = try { conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "" } catch (_: Exception) { "" }
                    conn.disconnect()
                    return ProbeResponse.Error(responseCode, errorText)
                }
                conn.disconnect()
                ProbeResponse.Success(responseCode, response)
            } catch (e: Exception) {
                ProbeResponse.Exception(e.javaClass.simpleName, e.message ?: "")
            }
        }

        private fun analyzeProbeResults(results: Map<String, ProbeResponse>): ProbeResult {
            // 收集所有成功返回的字段
            val allFields = mutableSetOf<String>()
            val allResponses = mutableMapOf<String, String>()

            for ((endpoint, result) in results) {
                when (result) {
                    is ProbeResponse.Success -> {
                        val fields = extractAllKeys(result.body)
                        allFields.addAll(fields)
                        allResponses[endpoint] = result.body
                    }
                    else -> {}
                }
            }

            // 判断数据类型
            // 1. 额度/次数类型字段
            val quotaFields = setOf("total", "used", "remaining", "available", "quota", "limit", "call_count", "usage_count", "soft_limit", "hard_limit")
            // 2. 余额类型字段
            val balanceFields = setOf("balance", "amount", "credit", "usd", "cny", "money")
            // 3. Token 类型字段
            val tokenFields = setOf("tokens", "prompt_tokens", "completion_tokens", "total_tokens")

            val hasQuota = allFields.any { it in quotaFields }
            val hasBalance = allFields.any { it in balanceFields }
            val hasTokens = allFields.any { it in tokenFields }

            // 优先判断逻辑
            return when {
                hasQuota -> {
                    // 尝试从 subscription 和 usage 接口提取额度数据
                    extractQuotaData(allResponses)
                }
                hasBalance -> {
                    extractBalanceData(allResponses)
                }
                hasTokens -> {
                    extractTokenData(allResponses)
                }
                else -> {
                    // 无法识别
                    ProbeResult.Success(DataType.Unknown(allFields.toList(), allResponses))
                }
            }
        }

        private fun extractQuotaData(responses: Map<String, String>): ProbeResult {
            var total = 0.0
            var used = 0.0
            var remaining = 0.0
            var found = false

            // 从 subscription 接口提取 soft_limit
            val subResponse = responses["/v1/dashboard/billing/subscription"] ?: ""
            if (subResponse.isNotEmpty()) {
                val softLimit = extractJsonValue(subResponse, "soft_limit")?.toDoubleOrNull() ?: 0.0
                if (softLimit > 0) {
                    total = softLimit
                    found = true
                }
            }

            // 从 usage 接口提取 total_usage
            val usageResponse = responses["/v1/dashboard/billing/usage"] ?: ""
            if (usageResponse.isNotEmpty()) {
                val totalUsage = extractJsonValue(usageResponse, "total_usage")?.toDoubleOrNull() ?: 0.0
                if (totalUsage > 0) {
                    used = totalUsage / 100.0  // 美分转美元
                    found = true
                }
            }

            return if (found) {
                remaining = (total - used).coerceAtLeast(0.0)
                ProbeResult.Success(DataType.Quota(total, used, remaining))
            } else {
                ProbeResult.Success(DataType.Unknown(listOf(), responses))
            }
        }

        private fun extractBalanceData(responses: Map<String, String>): ProbeResult {
            var amount = 0.0
            var currency = "$"
            var found = false

            for ((_, response) in responses) {
                if (response.isEmpty()) continue
                val balance = extractJsonValue(response, "balance")?.toDoubleOrNull()
                if (balance != null && balance > 0) {
                    amount = balance
                    found = true
                    break
                }
            }

            return if (found) {
                ProbeResult.Success(DataType.Balance(amount, currency))
            } else {
                ProbeResult.Success(DataType.Unknown(listOf(), responses))
            }
        }

        private fun extractTokenData(responses: Map<String, String>): ProbeResult {
            var total = 0.0
            var used = 0.0
            var found = false

            for ((_, response) in responses) {
                if (response.isEmpty()) continue
                val tokens = extractJsonValue(response, "total_tokens")?.toDoubleOrNull()
                if (tokens != null && tokens > 0) {
                    total = tokens
                    found = true
                    break
                }
            }

            return if (found) {
                ProbeResult.Success(DataType.Tokens(total, used))
            } else {
                ProbeResult.Success(DataType.Unknown(listOf(), responses))
            }
        }

        fun extractJsonValue(json: String, key: String): String? {
            val keyStr = "\"$key\":"
            val idx = json.indexOf(keyStr)
            if (idx == -1) return null
            val valueStart = idx + keyStr.length
            var i = valueStart
            while (i < json.length && (json[i] == ' ' || json[i] == '"')) i++
            val endIdx = when {
                json[i] == '"' -> {
                    val start = i + 1
                    json.indexOf('"', start)
                }
                else -> {
                    var end = i
                    while (end < json.length && json[end] !in setOf(',', '}', ']')) end++
                    end
                }
            }
            return if (endIdx > i) json.substring(i, endIdx).trim() else null
        }

        fun extractAllKeys(json: String): Set<String> {
            val keys = mutableSetOf<String>()
            var i = 0
            while (i < json.length) {
                val quoteIdx = json.indexOf('"', i)
                if (quoteIdx == -1) break
                val endQuote = json.indexOf('"', quoteIdx + 1)
                if (endQuote == -1) break
                val key = json.substring(quoteIdx + 1, endQuote)
                // 检查后面是否跟着冒号
                val afterQuote = endQuote + 1
                if (afterQuote < json.length && json[afterQuote] == ':') {
                    keys.add(key)
                }
                i = endQuote + 1
            }
            return keys
        }

        // ==================== 以下 Kimi 专用代码已迁移到 adapter/NewApiAdapter.kt ====================
        // BalanceWidgetProvider 不再直接处理 /api/usage/token 接口
        // 请使用 NewApiAdapter.fetchData(apiBase, apiKey) 获取 WidgetData
        // ================================================================================

        /**
         * Kimi 本地快照统计计算
         *
         * 使用单独 SharedPreferences：widget_local_stats
         * key：
         *   kimi_last_model      - 上次模型名称
         *   kimi_last_total_used - 上次累计已用次数
         *   kimi_last_success_time - 上次成功刷新时间（毫秒）
         *
         * 规则：
         * 1. 第一次刷新（无历史快照）：建立基准，Core 2 显示"近期用量统计中…"
         * 2. 两次成功刷新之间：计算新增调用次数，根据真实时间差生成文案
         * 3. 累计值变小或模型变化：重新建立基准，显示"统计基准已更新～"
         * 4. 请求失败：不更新基准
         */
        private fun computeKimiUsageMetrics(
            context: Context,
            result: WidgetData,
            currentModel: String?
        ): WidgetData {
            val statsPrefs = context.getSharedPreferences("widget_local_stats", Context.MODE_PRIVATE)
            val lastModel = statsPrefs.getString("kimi_v2_last_model", null)
            val lastTotalUsed = statsPrefs.getLong("kimi_v2_last_used_calls", -1L)
            val lastSuccessTime = statsPrefs.getLong("kimi_v2_last_success_time", -1L)
            val currentTotalUsed = result.cumulativeUsedCalls ?: return result
            val currentTime = System.currentTimeMillis()
            val currentModelSafe = currentModel ?: ""

            // 模型变化：重新建立基准
            if (lastModel != null && lastModel != currentModelSafe) {
                statsPrefs.edit()
                    .putString("kimi_v2_last_model", currentModelSafe)
                    .putLong("kimi_v2_last_used_calls", currentTotalUsed)
                    .putLong("kimi_v2_last_success_time", currentTime)
                    .apply()
                return result.copy(
                    usageMetrics = listOf(WidgetData.DisplayMetric("统计基准已更新～", ""))
                )
            }

            // 累计值变小：重新建立基准
            if (lastTotalUsed >= 0 && currentTotalUsed < lastTotalUsed) {
                statsPrefs.edit()
                    .putString("kimi_v2_last_model", currentModelSafe)
                    .putLong("kimi_v2_last_used_calls", currentTotalUsed)
                    .putLong("kimi_v2_last_success_time", currentTime)
                    .apply()
                return result.copy(
                    usageMetrics = listOf(WidgetData.DisplayMetric("统计基准已更新～", ""))
                )
            }

            // 第一次刷新（无历史快照）：建立基准
            if (lastTotalUsed < 0 || lastSuccessTime < 0) {
                statsPrefs.edit()
                    .putString("kimi_v2_last_model", currentModelSafe)
                    .putLong("kimi_v2_last_used_calls", currentTotalUsed)
                    .putLong("kimi_v2_last_success_time", currentTime)
                    .apply()
                return result.copy(
                    usageMetrics = listOf(WidgetData.DisplayMetric("近期用量统计中…", ""))
                )
            }

            // 两次成功刷新之间：计算新增调用次数
            val newCalls = currentTotalUsed - lastTotalUsed
            val timeDiffMs = currentTime - lastSuccessTime
            val timeLabel = formatTimeDiff(timeDiffMs)

            val usageText = if (newCalls > 0) {
                "${timeLabel}调用 ${newCalls}次"
            } else {
                "${timeLabel}暂无新调用～"
            }

            // 更新基准
            statsPrefs.edit()
                .putString("kimi_v2_last_model", currentModelSafe)
                .putLong("kimi_v2_last_used_calls", currentTotalUsed)
                .putLong("kimi_v2_last_success_time", currentTime)
                .apply()

            return result.copy(
                usageMetrics = listOf(WidgetData.DisplayMetric(usageText, ""))
            )
        }

        /**
         * 格式化时间差为中文文案
         *
         * 少于1分钟："不到1分钟"
         * 1～59分钟："过去35分钟"
         * 1小时以上："过去2小时35分钟"
         */
        private fun formatTimeDiff(diffMs: Long): String {
            val minutes = diffMs / 60000
            val hours = minutes / 60
            val remainingMinutes = minutes % 60

            return when {
                minutes < 1 -> "不到1分钟"
                hours == 0L -> "过去${minutes}分钟"
                remainingMinutes == 0L -> "过去${hours}小时"
                else -> "过去${hours}小时${remainingMinutes}分钟"
            }
        }

        /**
         * Aihuangniu 本地快照统计计算
         *
         * 使用独立 SharedPreferences：widget_local_stats
         * key：
         *   aihuangniu_v1_last_model      - 上次模型名称
         *   aihuangniu_v1_last_requests   - 上次累计请求次数
         *   aihuangniu_v1_last_tokens     - 上次累计 Token 数
         *   aihuangniu_v1_last_actual_cost - 上次累计消费
         *   aihuangniu_v1_last_success_time - 上次成功刷新时间（毫秒）
         */
        private fun computeAihuangniuUsageMetrics(
            context: Context,
            result: WidgetData,
            currentModel: String?
        ): WidgetData {
            val statsPrefs = context.getSharedPreferences("widget_local_stats", Context.MODE_PRIVATE)
            val lastModel = statsPrefs.getString("aihuangniu_v1_last_model", null)
            val lastRequests = statsPrefs.getLong("aihuangniu_v1_last_requests", -1L)
            val lastTokens = statsPrefs.getLong("aihuangniu_v1_last_tokens", -1L)
            val lastActualCost = statsPrefs.getString("aihuangniu_v1_last_actual_cost", null)
            val lastSuccessTime = statsPrefs.getLong("aihuangniu_v1_last_success_time", -1L)

            val currentRequests = result.cumulativeUsageRequests ?: return result
            val currentTokens = result.cumulativeUsageTokens ?: return result
            val currentActualCost = result.cumulativeUsageActualCost ?: return result
            val currentTime = System.currentTimeMillis()
            val currentModelSafe = currentModel ?: ""

            // 模型变化：重新建立基准
            if (lastModel != null && lastModel != currentModelSafe) {
                statsPrefs.edit()
                    .putString("aihuangniu_v1_last_model", currentModelSafe)
                    .putLong("aihuangniu_v1_last_requests", currentRequests)
                    .putLong("aihuangniu_v1_last_tokens", currentTokens)
                    .putString("aihuangniu_v1_last_actual_cost", currentActualCost)
                    .putLong("aihuangniu_v1_last_success_time", currentTime)
                    .apply()
                return result.copy(
                    usageMetrics = listOf(WidgetData.DisplayMetric("统计基准已更新～", ""))
                )
            }

            // 累计值变小：重新建立基准
            if (lastRequests >= 0 && currentRequests < lastRequests) {
                statsPrefs.edit()
                    .putString("aihuangniu_v1_last_model", currentModelSafe)
                    .putLong("aihuangniu_v1_last_requests", currentRequests)
                    .putLong("aihuangniu_v1_last_tokens", currentTokens)
                    .putString("aihuangniu_v1_last_actual_cost", currentActualCost)
                    .putLong("aihuangniu_v1_last_success_time", currentTime)
                    .apply()
                return result.copy(
                    usageMetrics = listOf(WidgetData.DisplayMetric("统计基准已更新～", ""))
                )
            }

            // 第一次刷新（无历史快照）：建立基准
            if (lastRequests < 0 || lastSuccessTime < 0) {
                statsPrefs.edit()
                    .putString("aihuangniu_v1_last_model", currentModelSafe)
                    .putLong("aihuangniu_v1_last_requests", currentRequests)
                    .putLong("aihuangniu_v1_last_tokens", currentTokens)
                    .putString("aihuangniu_v1_last_actual_cost", currentActualCost)
                    .putLong("aihuangniu_v1_last_success_time", currentTime)
                    .apply()
                return result.copy(
                    usageMetrics = listOf(WidgetData.DisplayMetric("近期用量统计中…", ""))
                )
            }

            // 两次成功刷新之间：计算新增数据
            val newRequests = currentRequests - lastRequests
            val newTokens = currentTokens - lastTokens
            val lastCost = lastActualCost?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
            val currentCost = currentActualCost.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
            val newCost = currentCost.subtract(lastCost)
            val timeDiffMs = currentTime - lastSuccessTime
            val timeLabel = formatTimeDiff(timeDiffMs)

            val metrics = mutableListOf<WidgetData.DisplayMetric>()

            // 1. 调用次数
            if (newRequests > 0) {
                metrics.add(WidgetData.DisplayMetric("${timeLabel}调用", "${newRequests}次"))
            } else {
                metrics.add(WidgetData.DisplayMetric("${timeLabel}暂无新调用～", ""))
            }

            // 2. Token 消耗
            metrics.add(WidgetData.DisplayMetric("${timeLabel}消耗", "${newTokens} tokens"))

            // 3. 消费金额
            val costStr = if (newCost > java.math.BigDecimal.ZERO) {
                "¥${newCost.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()}"
            } else {
                "¥0.00"
            }
            metrics.add(WidgetData.DisplayMetric("${timeLabel}消费", costStr))

            // 更新基准
            statsPrefs.edit()
                .putString("aihuangniu_v1_last_model", currentModelSafe)
                .putLong("aihuangniu_v1_last_requests", currentRequests)
                .putLong("aihuangniu_v1_last_tokens", currentTokens)
                .putString("aihuangniu_v1_last_actual_cost", currentActualCost)
                .putLong("aihuangniu_v1_last_success_time", currentTime)
                .apply()

            return result.copy(usageMetrics = metrics)
        }
    }
}

data class WidgetConfig(
    val name: String,
    val apiBase: String,
    val apiKey: String,
    val model: String,
    val enabled: Boolean
)

data class KimiTokenData(
    val totalGranted: Double,      // 总配额 (per_call_quota 的倍数)
    val totalUsed: Double,         // 已使用配额
    val totalAvailable: Double,    // 可用剩余
    val callCount: Long,           // 调用次数
    val perCallQuota: Double,      // 每次调用配额
    val perCallDisplayLabel: String, // 显示单位，例如"次"
    val allowedModels: String      // 允许的模型
)

sealed class KimiTokenResult {
    data class Success(val data: KimiTokenData) : KimiTokenResult()
    data class Error(val message: String, val detail: String = "") : KimiTokenResult()
}

sealed class ProbeResult {
    data class Success(val dataType: DataType) : ProbeResult()
    data class Error(val message: String) : ProbeResult()
}

sealed class DataType {
    data class Quota(val total: Double, val used: Double, val remaining: Double) : DataType()
    data class Balance(val amount: Double, val currency: String) : DataType()
    data class Tokens(val total: Double, val used: Double) : DataType()
    data class Unknown(val fields: List<String>, val responses: Map<String, String>) : DataType()
}

sealed class ProbeResponse {
    data class Success(val code: Int, val body: String) : ProbeResponse()
    data class Error(val code: Int, val body: String) : ProbeResponse()
    data class Exception(val type: String, val message: String) : ProbeResponse()
}