package com.java.myapplication

import android.app.Application
import android.content.Context
import android.os.Build
import android.webkit.WebView
import com.java.myapplication.config.ModelInstanceRepository
import java.io.File

class DashboardApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 实验室使用独立进程和独立 WebView 数据目录，避免网页 Cookie/存储污染主 App。
        if (currentProcessName().endsWith(":dashboard_discovery")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WebView.setDataDirectorySuffix("dashboard_discovery")
            }
            return
        }

        instance = this

        // 确保模型实例迁移完成
        val prefs = getSharedPreferences("api_config", MODE_PRIVATE)
        val migrated = ModelInstanceRepository.ensureMigrated(prefs)
        if (!migrated) {
            android.util.Log.w("DashboardApplication", "ModelInstance migration returned false")
        }
    }

    companion object {
        @Volatile
        private var instance: DashboardApplication? = null

        fun appContextOrNull(): Context? = instance?.applicationContext
    }

    private fun currentProcessName(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return Application.getProcessName()
        return runCatching {
            File("/proc/self/cmdline").readText().substringBefore('\u0000').trim()
        }.getOrDefault(packageName)
    }
}
