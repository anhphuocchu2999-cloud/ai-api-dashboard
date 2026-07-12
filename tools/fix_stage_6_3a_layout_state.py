from pathlib import Path

path = Path("app/src/main/java/com/java/myapplication/BalanceWidgetProvider.kt")
text = path.read_text(encoding="utf-8")

replacements = []

replacements.append((
'''import java.util.Locale
import java.util.Random
''',
'''import java.util.Locale
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
'''
))

replacements.append((
'''    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }
''',
'''    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        saveCompactModeFromOptions(context, appWidgetId, newOptions)
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }
'''
))

replacements.append((
'''        const val ACTION_REFRESH = "com.java.myapplication.ACTION_REFRESH"
        private const val COMPACT_HEIGHT_BREAKPOINT_DP = 160

        private fun isCompactMode(
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ): Boolean {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            return minHeight in 1 until COMPACT_HEIGHT_BREAKPOINT_DP
        }

        private fun applyResponsiveLayout(views: RemoteViews, compactMode: Boolean) {
''',
'''        const val ACTION_REFRESH = "com.java.myapplication.ACTION_REFRESH"
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
'''
))

replacements.append((
'''                applyResponsiveLayout(views, isCompactMode(appWidgetManager, appWidgetId))
''',
'''                applyResponsiveLayout(
                    views,
                    currentCompactMode(context, appWidgetManager, appWidgetId)
                )
'''
))

replacements.append((
'''        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_balance)
            val compactMode = isCompactMode(appWidgetManager, appWidgetId)
            applyResponsiveLayout(views, compactMode)
''',
'''        ) {
            val generation = beginUpdate(appWidgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_balance)
            val compactMode = currentCompactMode(context, appWidgetManager, appWidgetId)
            applyResponsiveLayout(views, compactMode)
'''
))

replacements.append((
'''                Handler(Looper.getMainLooper()).post {
                    // 渲染四个平台
''',
'''                Handler(Looper.getMainLooper()).post {
                    if (!isLatestUpdate(appWidgetId, generation)) {
                        return@post
                    }

                    applyResponsiveLayout(
                        views,
                        currentCompactMode(context, appWidgetManager, appWidgetId)
                    )

                    // 渲染当前模式的平台
'''
))

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly 1 match, found {count}:\n{old[:160]}")
    text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")
print("Stage 6-3A layout state/race fix applied successfully.")
