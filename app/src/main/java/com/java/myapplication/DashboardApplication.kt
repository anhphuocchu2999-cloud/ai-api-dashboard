package com.java.myapplication

import android.app.Application
import android.content.Context
import com.java.myapplication.config.ModelInstanceRepository

class DashboardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
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
}
