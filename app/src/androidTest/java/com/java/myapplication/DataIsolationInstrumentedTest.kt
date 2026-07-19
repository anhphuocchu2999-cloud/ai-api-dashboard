package com.java.myapplication

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.java.myapplication.adapter.WidgetData
import com.java.myapplication.adapter.auth.BackgroundAuthConfig
import com.java.myapplication.adapter.auth.BackgroundAuthRepository
import com.java.myapplication.adapter.auth.BackgroundAuthType
import com.java.myapplication.config.ApiAccountConfig
import com.java.myapplication.config.ConfigRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataIsolationInstrumentedTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val authPrefs
        get() = context.getSharedPreferences("test_auth_isolation", Context.MODE_PRIVATE)

    private val configPrefs
        get() = context.getSharedPreferences("test_config_persistence", Context.MODE_PRIVATE)

    @Before
    fun clearTestState() {
        authPrefs.edit().clear().commit()
        configPrefs.edit().clear().commit()
        context.getSharedPreferences("widget_last_success", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun cleanUp() = clearTestState()

    @Test
    fun platformMigrationReadsOnlyExactSharedCredential() {
        val slotCredential = BackgroundAuthConfig(
            authType = BackgroundAuthType.COOKIE,
            authValue = "slot-a-cookie-for-test",
            enabled = true,
            updatedAt = 1L
        )
        assertTrue(BackgroundAuthRepository.save(authPrefs, "MiMo", slotCredential))

        assertNull(
            "平台公共迁移不得通过兼容别名读取某个槽位的凭据",
            BackgroundAuthRepository.loadExact(authPrefs, "platform-auth-mimo")
        )
        assertEquals(slotCredential.authValue, BackgroundAuthRepository.load(authPrefs, "MiMo").authValue)

        val legacyShared = slotCredential.copy(
            authValue = "legacy-shared-cookie-for-test",
            updatedAt = 2L
        )
        assertTrue(BackgroundAuthRepository.save(authPrefs, "platform-auth-mimo", legacyShared))
        assertEquals(
            legacyShared.authValue,
            BackgroundAuthRepository.loadExact(authPrefs, "platform-auth-mimo")?.authValue
        )
        assertEquals(
            "槽位自己的凭据不得被平台公共键覆盖",
            slotCredential.authValue,
            BackgroundAuthRepository.load(authPrefs, "MiMo").authValue
        )
    }

    @Test
    fun apiKeysPersistEncryptedAndRoundTripPerSlot() {
        val configs = listOf(
            ApiAccountConfig("Kimi", "槽位一", "https://api.example.com", "key-one-test", "model-a", true),
            ApiAccountConfig("MiMo", "槽位二", "https://api.other.example", "key-two-test", "model-b", true)
        )
        assertTrue(ConfigRepository.saveConfigs(configPrefs, configs))

        val rawStorage = configPrefs.all.values.joinToString("|")
        assertFalse(rawStorage.contains("key-one-test"))
        assertFalse(rawStorage.contains("key-two-test"))

        val loaded = ConfigRepository.loadAllConfigs(configPrefs)
        assertEquals("key-one-test", loaded.first { it.id == "Kimi" }.apiKey)
        assertEquals("key-two-test", loaded.first { it.id == "MiMo" }.apiKey)
    }

    @Test
    fun partialOrModelOnlyResultCannotReplaceCompleteInstanceCache() {
        val cacheKey = "model-cache-test-slot-a-123456"
        WidgetData(
            platformName = "test",
            modelName = "model-a",
            primaryMetric = WidgetData.DisplayMetric("余额", "100"),
            auxiliaryMetrics = listOf(WidgetData.DisplayMetric("累计用量", "25")),
            cacheKey = cacheKey
        )
        WidgetData(
            platformName = "test",
            modelName = "model-a",
            primaryMetric = WidgetData.DisplayMetric("余额", "90"),
            cacheKey = cacheKey
        )
        WidgetData(
            platformName = "test",
            modelName = "model-a",
            cacheKey = cacheKey
        )

        val fallback = WidgetData.error("test", "网络错误", cacheKey)
        assertTrue(fallback.isFallback)
        assertEquals("100", fallback.primaryMetric?.value)
        assertEquals("25", fallback.auxiliaryMetrics.firstOrNull()?.value)
        assertNotNull(fallback.cachedAtMillis)

        val otherInstance = WidgetData.error("test", "网络错误", "model-cache-test-slot-b-654321")
        assertFalse("不同实例不得串读缓存", otherInstance.isFallback)
        assertFalse(otherInstance.isSuccess)
    }
}
