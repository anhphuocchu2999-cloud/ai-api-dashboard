#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected pattern exactly once, found {count}\nPATTERN:\n{old}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def ensure_absent(path: Path, marker: str) -> None:
    text = path.read_text(encoding="utf-8")
    if marker in text:
        raise RuntimeError(f"{path}: patch appears to have already been applied: {marker}")


def main() -> int:
    provider = ROOT / "app/src/main/java/com/java/myapplication/BalanceWidgetProvider.kt"
    layout = ROOT / "app/src/main/res/layout/widget_balance.xml"
    widget_info = ROOT / "app/src/main/res/xml/balance_widget_info.xml"
    main_activity = ROOT / "app/src/main/java/com/java/myapplication/MainActivity.kt"

    for path in (provider, layout, widget_info, main_activity):
        if not path.is_file():
            raise FileNotFoundError(path)

    ensure_absent(provider, "COMPACT_HEIGHT_BREAKPOINT_DP")
    ensure_absent(layout, '@+id/row_secondary')

    replace_once(
        provider,
        "import android.content.Intent\nimport android.os.Handler",
        "import android.content.Intent\nimport android.os.Bundle\nimport android.os.Handler",
    )

    replace_once(
        provider,
        '''    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
''',
        '''    override fun onUpdate(
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
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }
''',
    )

    replace_once(
        provider,
        '''        const val ACTION_REFRESH = "com.java.myapplication.ACTION_REFRESH"

        // 连续点击计数（全局）
''',
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
            views.setViewVisibility(
                R.id.row_secondary,
                if (compactMode) android.view.View.GONE else android.view.View.VISIBLE
            )
        }

        // 连续点击计数（全局）
''',
    )

    replace_once(
        provider,
        '''                val message = toastMessages[count - 1]
                val views = RemoteViews(context.packageName, R.layout.widget_balance)
                views.setTextViewText(R.id.click_count, message)
''',
        '''                val message = toastMessages[count - 1]
                val views = RemoteViews(context.packageName, R.layout.widget_balance)
                applyResponsiveLayout(views, isCompactMode(appWidgetManager, appWidgetId))
                views.setTextViewText(R.id.click_count, message)
''',
    )

    replace_once(
        provider,
        '''            val views = RemoteViews(context.packageName, R.layout.widget_balance)
            val random = Random()
''',
        '''            val views = RemoteViews(context.packageName, R.layout.widget_balance)
            val compactMode = isCompactMode(appWidgetManager, appWidgetId)
            applyResponsiveLayout(views, compactMode)
            val random = Random()
''',
    )

    replace_once(
        provider,
        '''            val configs = slotIds.map { slotId ->
                apiConfigs.find { it.id == slotId }
                    ?: ApiAccountConfig(id = slotId, name = "", apiBase = "", apiKey = "", model = "", enabled = true)
            }
            val platforms = slotIds
''',
        '''            val allConfigs = slotIds.map { slotId ->
                apiConfigs.find { it.id == slotId }
                    ?: ApiAccountConfig(id = slotId, name = "", apiBase = "", apiKey = "", model = "", enabled = true)
            }
            val platforms = if (compactMode) slotIds.take(2) else slotIds
            val configs = if (compactMode) allConfigs.take(2) else allConfigs
''',
    )

    replace_once(
        layout,
        '''    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:orientation="horizontal">
''',
        '''    <LinearLayout
        android:id="@+id/row_primary"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:orientation="horizontal">
''',
    )

    replace_once(
        layout,
        '''    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:layout_marginTop="8dp"
        android:orientation="horizontal">
''',
        '''    <LinearLayout
        android:id="@+id/row_secondary"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:layout_marginTop="8dp"
        android:orientation="horizontal">
''',
    )

    replace_once(
        widget_info,
        '''    android:minHeight="180dp"
    android:updatePeriodMillis="21600000"
''',
        '''    android:minHeight="180dp"
    android:minResizeHeight="110dp"
    android:updatePeriodMillis="21600000"
''',
    )

    replace_once(
        main_activity,
        'text = "Build: 2026-07-12-001 | Stage: 6-2",',
        'text = "Build: 2026-07-12-002 | Stage: 6-3A",',
    )

    print("Stage 6-3A patch applied successfully.")
    print("Changed files:")
    print("- app/src/main/java/com/java/myapplication/BalanceWidgetProvider.kt")
    print("- app/src/main/res/layout/widget_balance.xml")
    print("- app/src/main/res/xml/balance_widget_info.xml")
    print("- app/src/main/java/com/java/myapplication/MainActivity.kt")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
