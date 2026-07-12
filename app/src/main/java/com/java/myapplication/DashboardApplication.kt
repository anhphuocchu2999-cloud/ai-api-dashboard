package com.java.myapplication

import android.app.Application
import android.content.Context

class DashboardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        @Volatile
        private var instance: DashboardApplication? = null

        fun appContextOrNull(): Context? = instance?.applicationContext
    }
}
