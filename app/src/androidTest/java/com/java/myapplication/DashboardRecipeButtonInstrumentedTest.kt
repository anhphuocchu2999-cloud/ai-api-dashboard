package com.java.myapplication

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.java.myapplication.discovery.DashboardDiscoveryActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardRecipeButtonInstrumentedTest {
    @Test
    fun staleRecipeResultClickAlwaysShowsVisibleRecoveryMessage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val intent = Intent(context, DashboardDiscoveryActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(DashboardDiscoveryActivity.EXTRA_UI_TEST_STALE_RECIPE_STATE, true)

        context.startActivity(intent)

        val button = requireNotNull(
            device.wait(
                Until.findObject(By.res(context.packageName, "discovery_save_recipe")),
                10_000
            )
        ) { "直接测试按钮没有显示" }
        button.click()

        requireNotNull(
            device.wait(
                Until.findObject(By.text("重新直接测试并加密保存")),
                5_000
            )
        ) { "点击后按钮没有显示可重试状态" }
        val status = requireNotNull(
            device.findObject(By.res(context.packageName, "discovery_recipe_status"))
        ) { "点击后没有状态提示" }
        assertTrue(
            "点击后没有解释识别结果已失效",
            status.text.orEmpty().contains("已经失效")
        )
    }
}
