package com.java.myapplication

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import com.java.myapplication.adapter.auth.BackgroundAuthConfig
import com.java.myapplication.adapter.auth.BackgroundAuthRepository
import com.java.myapplication.adapter.auth.BackgroundAuthType
import com.java.myapplication.webauth.WebAuthProfile
import com.java.myapplication.webauth.WebAuthProfileRegistry

/**
 * 通用网页登录授权 Activity。
 *
 * Stage 7A-3A 只迁移已经验证过的 MiMo COOKIE 流程。
 * Stage 7A-3C 扩展支持爱黄牛 localStorage auth_token 自动提取。
 */
class WebAuthActivity : Activity() {

    companion object {
        private const val EXTRA_PROFILE_ID = "web_auth_profile_id"
        private const val PREFS_NAME = "api_config"

        fun createIntent(context: Context, profileId: String): Intent {
            return Intent(context, WebAuthActivity::class.java)
                .putExtra(EXTRA_PROFILE_ID, profileId)
        }
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var profile: WebAuthProfile
    private var loginDetected = false
    private var showCancelToast = false

    /** 用于 localStorage 轮询的 Handler */
    private val pollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pollRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID).orEmpty()
        val resolvedProfile = WebAuthProfileRegistry.findByProfileId(profileId)
        if (resolvedProfile == null) {
            Toast.makeText(this, "暂不支持这个网页登录入口", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (resolvedProfile.authType != BackgroundAuthType.COOKIE &&
            resolvedProfile.authType != BackgroundAuthType.BEARER_TOKEN
        ) {
            Toast.makeText(this, "这个授权方式还没有完成验证", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        profile = resolvedProfile
        showCancelToast = true

        setContentView(R.layout.activity_web_auth)
        title = "${profile.displayName} 网页登录授权"

        webView = findViewById(R.id.web_view)
        progressBar = findViewById(R.id.progress_bar)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

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

            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: android.graphics.Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE

                // 根据认证类型选择检测策略
                when (profile.authType) {
                    BackgroundAuthType.COOKIE -> checkAndSaveCookies()
                    BackgroundAuthType.BEARER_TOKEN -> startLocalStoragePolling()
                    else -> { /* 不处理 */ }
                }
            }
        }

        webView.loadUrl(profile.loginUrl)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理轮询
        pollRunnable?.let { pollHandler.removeCallbacks(it) }
        if (showCancelToast && !loginDetected) {
            Toast.makeText(
                this,
                "未检测到登录状态，已取消授权",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun checkAndSaveCookies() {
        if (loginDetected) return

        val cookieString = CookieManager.getInstance().getCookie(profile.cookieDomain) ?: ""
        if (cookieString.isBlank()) return

        val cookies = parseCookieString(cookieString)
        val hasRequiredCookies = profile.requiredCookieNames.all(cookies::containsKey)
        if (!hasRequiredCookies) return

        loginDetected = true
        val allCookies = buildString {
            cookies.forEach { (name, value) ->
                if (isNotEmpty()) append("; ")
                append("$name=$value")
            }
        }

        saveCredentialAndRefresh(allCookies)
    }

    private fun saveCredentialAndRefresh(cookieString: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val authConfig = BackgroundAuthConfig(
            authType = profile.authType,
            authValue = cookieString,
            enabled = true,
            updatedAt = System.currentTimeMillis()
        )

        BackgroundAuthRepository.save(
            prefs = prefs,
            instanceKey = profile.instanceKey,
            config = authConfig
        )

        Toast.makeText(
            this,
            "${profile.displayName} 网页授权成功",
            Toast.LENGTH_SHORT
        ).show()

        refreshWidget(this)
        finish()
    }

    /**
     * 启动 localStorage 轮询，用于 BEARER_TOKEN 自动提取。
     * 每 2 秒读取一次 localStorage，直到获取到非空 token。
     */
    private fun startLocalStoragePolling() {
        if (loginDetected) return
        val key = profile.localStorageKey ?: return

        val runnable = object : Runnable {
            override fun run() {
                if (loginDetected) return
                webView.evaluateJavascript(
                    "localStorage.getItem('$key')",
                    android.webkit.ValueCallback { value ->
                        if (loginDetected) return@ValueCallback
                        val token = value?.trim()?.removeSurrounding("\"")?.trim()
                        if (!token.isNullOrBlank() && token != "null") {
                            loginDetected = true
                            saveCredentialAndRefresh(token)
                        }
                    }
                )
                if (!loginDetected) {
                    pollHandler.postDelayed(this, 2000)
                }
            }
        }
        pollRunnable = runnable
        pollHandler.postDelayed(runnable, 2000)
    }

    private fun refreshWidget(context: Context) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, BalanceWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                val intent = Intent(context, BalanceWidgetProvider::class.java)
                intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                context.sendBroadcast(intent)
            }
        } catch (_: Exception) {
        }
    }

    private fun parseCookieString(cookieString: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        cookieString.split(";").forEach { pair ->
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
