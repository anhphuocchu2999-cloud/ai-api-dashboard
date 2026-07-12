package com.java.myapplication

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import android.app.Activity
import com.java.myapplication.adapter.auth.BackgroundAuthConfig
import com.java.myapplication.adapter.auth.BackgroundAuthRepository
import com.java.myapplication.adapter.auth.BackgroundAuthType

/**
 * MiMo 网页登录授权 Activity
 *
 * 流程：
 * 1. 打开 platform.xiaomimimo.com 登录页
 * 2. 用户完成登录
 * 3. 自动检测必要 Cookie（api-platform_serviceToken + userId）
 * 4. 保存 Cookie 到 SharedPreferences
 * 5. 触发 Widget 刷新
 */
class MiMoWebLoginActivity : Activity() {

    companion object {
        const val LOGIN_URL = "https://platform.xiaomimimo.com/#/console/balance"
        const val COOKIE_DOMAIN = "platform.xiaomimimo.com"
        const val PREFS_NAME = "api_config"
        // 必要 Cookie
        const val REQUIRED_COOKIE_SERVICE_TOKEN = "api-platform_serviceToken"
        const val REQUIRED_COOKIE_USER_ID = "userId"
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var loginDetected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mimo_web_login)

        title = "MiMo 网页登录授权"

        webView = findViewById(R.id.web_view)
        progressBar = findViewById(R.id.progress_bar)

        // 启用 Cookie
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        // WebView 设置
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = "Mozilla/5.0 (Linux; Android 14; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
        }

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE

                // 页面加载完成后检查 Cookie
                checkAndSaveCookies()
            }
        }

        webView.loadUrl(LOGIN_URL)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!loginDetected) {
            // 未获得必要 Cookie，不覆盖现有配置
            Toast.makeText(
                this,
                "未检测到登录状态，已取消授权",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 检查并保存 Cookie
     */
    private fun checkAndSaveCookies() {
        if (loginDetected) return

        val cookieManager = CookieManager.getInstance()
        val cookieString = cookieManager.getCookie(COOKIE_DOMAIN) ?: ""

        if (cookieString.isBlank()) return

        // 解析 Cookie
        val cookies = parseCookieString(cookieString)

        // 检查必要 Cookie
        val hasServiceToken = cookies.containsKey(REQUIRED_COOKIE_SERVICE_TOKEN)
        val hasUserId = cookies.containsKey(REQUIRED_COOKIE_USER_ID)

        if (hasServiceToken && hasUserId) {
            loginDetected = true

            // 构建完整 Cookie 字符串
            val allCookies = buildString {
                cookies.forEach { (name, value) ->
                    if (isNotEmpty()) append("; ")
                    append("$name=$value")
                }
            }

            saveCookieAndRefresh(allCookies)
        }
    }

    /**
     * 保存 Cookie 并触发 Widget 刷新
     */
    private fun saveCookieAndRefresh(cookieString: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val authConfig = BackgroundAuthConfig(
            authType = BackgroundAuthType.COOKIE,
            authValue = cookieString,
            enabled = true,
            updatedAt = System.currentTimeMillis()
        )

        BackgroundAuthRepository.save(
            prefs = prefs,
            instanceKey = "MiMo",
            config = authConfig
        )

        Toast.makeText(
            this,
            "MiMo 网页授权成功",
            Toast.LENGTH_SHORT
        ).show()

        // 触发 Widget 刷新
        refreshWidget(this)

        finish()
    }

    /**
     * 触发 Widget 刷新
     */
    private fun refreshWidget(context: Context) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, BalanceWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                val intent = android.content.Intent(context, BalanceWidgetProvider::class.java)
                intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                context.sendBroadcast(intent)
            }
        } catch (_: Exception) { }
    }

    /**
     * 解析 Cookie 字符串为 Map
     */
    private fun parseCookieString(cookieString: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val pairs = cookieString.split(";")
        for (pair in pairs) {
            val trimmed = pair.trim()
            val eqIndex = trimmed.indexOf('=')
            if (eqIndex > 0) {
                val name = trimmed.substring(0, eqIndex).trim()
                val value = trimmed.substring(eqIndex + 1).trim()
                if (name.isNotEmpty()) {
                    result[name] = value
                }
            }
        }
        return result
    }
}