package com.java.myapplication

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.java.myapplication.adapter.auth.BackgroundAuthConfig
import com.java.myapplication.adapter.auth.BackgroundAuthRepository
import com.java.myapplication.adapter.auth.BackgroundAuthType
import com.java.myapplication.webauth.DeepSeekWebProbeRepository
import com.java.myapplication.webauth.WebAuthProfile
import com.java.myapplication.webauth.WebAuthProfileRegistry
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * 通用网页登录授权 Activity。
 *
 * 已验证正式路径：
 * - MiMo Cookie
 * - DeepSeek Cookie，经 get_user_summary 真实接口验证
 * - 爱黄牛 localStorage auth_token
 *
 * probeOnly 仍保留给未来只读接口摸排；正式授权不会保存未通过账户接口验证的 Cookie。
 */
class WebAuthActivity : Activity() {

    companion object {
        private const val EXTRA_PROFILE_ID = "web_auth_profile_id"
        private const val EXTRA_TARGET_INSTANCE_KEY = "web_auth_target_instance_key"
        private const val PREFS_NAME = "api_config"
        private const val PROBE_INSTALL_ATTEMPTS = 24
        private const val PROBE_INSTALL_INTERVAL_MS = 250L
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

        private val PROBE_SCRIPT = """
            (function() {
                if (window.__dashboardProbeInstalled) return 'ready';
                window.__dashboardProbeInstalled = true;

                function report(url, method, status, body) {
                    try {
                        if (!window.DashboardProbe) return;
                        window.DashboardProbe.onResponse(JSON.stringify({
                            url: String(url || ''),
                            method: String(method || 'GET'),
                            status: Number(status || 0),
                            body: String(body || '').substring(0, 200000)
                        }));
                    } catch (e) {}
                }

                var originalFetch = window.fetch;
                if (originalFetch) {
                    window.fetch = function(input, init) {
                        var method = (init && init.method) || (input && input.method) || 'GET';
                        var url = (typeof input === 'string') ? input : ((input && input.url) || '');
                        return originalFetch.apply(this, arguments).then(function(response) {
                            try {
                                var clone = response.clone();
                                clone.text().then(function(text) {
                                    report(clone.url || url, method, clone.status, text);
                                }).catch(function() {});
                            } catch (e) {}
                            return response;
                        });
                    };
                }

                var originalOpen = XMLHttpRequest.prototype.open;
                var originalSend = XMLHttpRequest.prototype.send;
                XMLHttpRequest.prototype.open = function(method, url) {
                    this.__dashboardProbeMethod = method || 'GET';
                    this.__dashboardProbeUrl = url || '';
                    return originalOpen.apply(this, arguments);
                };
                XMLHttpRequest.prototype.send = function() {
                    var xhr = this;
                    xhr.addEventListener('loadend', function() {
                        try {
                            var text = '';
                            if (!xhr.responseType || xhr.responseType === 'text' || xhr.responseType === 'json') {
                                text = (typeof xhr.responseText === 'string') ? xhr.responseText : JSON.stringify(xhr.response || {});
                            }
                            report(xhr.responseURL || xhr.__dashboardProbeUrl, xhr.__dashboardProbeMethod, xhr.status, text);
                        } catch (e) {}
                    });
                    return originalSend.apply(this, arguments);
                };
                return 'installed';
            })();
        """.trimIndent()

        fun createIntent(
            context: Context,
            profileId: String,
            targetInstanceKey: String? = null
        ): Intent {
            return Intent(context, WebAuthActivity::class.java)
                .putExtra(EXTRA_PROFILE_ID, profileId)
                .apply {
                    if (!targetInstanceKey.isNullOrBlank()) {
                        putExtra(EXTRA_TARGET_INSTANCE_KEY, targetInstanceKey)
                    }
                }
        }
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var profile: WebAuthProfile
    private lateinit var targetInstanceKey: String
    private var loginDetected = false
    private var showCancelToast = false
    private var hadExistingAuthorization = false
    private var cookieVerificationInProgress = false

    private val pollHandler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null

    private val probeHandler = Handler(Looper.getMainLooper())
    private var probeInstallAttemptsRemaining = 0
    private val probeInstallRunnable = object : Runnable {
        override fun run() {
            if (!::webView.isInitialized || !::profile.isInitialized || !profile.probeOnly) return
            webView.evaluateJavascript(PROBE_SCRIPT, null)
            probeInstallAttemptsRemaining--
            if (probeInstallAttemptsRemaining > 0) {
                probeHandler.postDelayed(this, PROBE_INSTALL_INTERVAL_MS)
            }
        }
    }

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
        targetInstanceKey = intent.getStringExtra(EXTRA_TARGET_INSTANCE_KEY)
            ?.takeIf { it.isNotBlank() }
            ?: profile.instanceKey

        val existingAuth = BackgroundAuthRepository.load(
            prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            instanceKey = targetInstanceKey
        )
        hadExistingAuthorization = existingAuth.enabled &&
            existingAuth.authType == profile.authType &&
            existingAuth.authValue.isNotBlank()
        showCancelToast = !profile.probeOnly

        setContentView(R.layout.activity_web_auth)
        title = if (profile.probeOnly) {
            "${profile.displayName} 网页数据摸排"
        } else {
            "${profile.displayName} 网页登录授权"
        }

        webView = findViewById(R.id.web_view)
        progressBar = findViewById(R.id.progress_bar)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = MOBILE_USER_AGENT
        }

        if (profile.probeOnly) {
            DeepSeekWebProbeRepository.clear(this, targetInstanceKey)
            webView.addJavascriptInterface(ProbeBridge(), "DashboardProbe")
            configureProbePanel()
        }

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = false

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                if (profile.probeOnly && request != null) {
                    DeepSeekWebProbeRepository.recordRequest(
                        context = applicationContext,
                        instanceKey = targetInstanceKey,
                        url = request.url.toString(),
                        method = request.method
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: android.graphics.Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                if (profile.probeOnly) scheduleProbeInjection()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE

                if (profile.probeOnly) {
                    scheduleProbeInjection()
                    updateProbeStatus()
                    return
                }

                when (profile.authType) {
                    BackgroundAuthType.COOKIE -> checkAndSaveCookies()
                    BackgroundAuthType.BEARER_TOKEN -> startLocalStoragePolling()
                    else -> Unit
                }
            }
        }

        webView.loadUrl(profile.loginUrl)
    }

    override fun onDestroy() {
        pollRunnable?.let { pollHandler.removeCallbacks(it) }
        probeHandler.removeCallbacks(probeInstallRunnable)
        if (showCancelToast && !loginDetected) {
            Toast.makeText(
                this,
                if (hadExistingAuthorization) {
                    "未更新授权，原登录状态仍保留"
                } else {
                    "未检测到登录状态，已取消授权"
                },
                Toast.LENGTH_SHORT
            ).show()
        }
        super.onDestroy()
    }

    private fun scheduleProbeInjection() {
        probeHandler.removeCallbacks(probeInstallRunnable)
        probeInstallAttemptsRemaining = PROBE_INSTALL_ATTEMPTS
        probeHandler.post(probeInstallRunnable)
    }

    private fun configureProbePanel() {
        val panel = findViewById<LinearLayout>(R.id.probe_panel)
        panel.visibility = View.VISIBLE

        findViewById<Button>(R.id.probe_results).setOnClickListener {
            showProbeResults()
        }

        findViewById<Button>(R.id.probe_finish).setOnClickListener {
            finishProbeSession()
        }

        updateProbeStatus()
    }

    private fun updateProbeStatus() {
        if (!profile.probeOnly) return
        val records = DeepSeekWebProbeRepository.load(this, targetInstanceKey)
        val structured = records.count { it.fields.isNotEmpty() }
        findViewById<TextView>(R.id.probe_status).text = when {
            records.isEmpty() -> "请登录 DeepSeek，进入并停留在用量页面。系统只读取接口地址和字段结构。"
            structured == 0 -> "已看到 ${records.size} 个网络请求，正在等待可读取的 JSON 数据接口…"
            else -> "已捕获 ${records.size} 个接口，其中 $structured 个返回了可识别字段。可以查看结果后完成返回。"
        }
    }

    private fun showProbeResults() {
        val textView = TextView(this).apply {
            text = DeepSeekWebProbeRepository.summary(this@WebAuthActivity, targetInstanceKey)
            textSize = 13f
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            setTextIsSelectable(true)
        }
        val scrollView = ScrollView(this).apply { addView(textView) }

        AlertDialog.Builder(this)
            .setTitle("DeepSeek 捕获结果")
            .setMessage("这里只显示 endpoint、状态码和 JSON 字段类型，不显示账户数值、Cookie 或 Token。")
            .setView(scrollView)
            .setPositiveButton("继续摸排", null)
            .show()
    }

    private fun finishProbeSession() {
        val records = DeepSeekWebProbeRepository.load(this, targetInstanceKey)
        val hasStructuredResponse = records.any {
            it.fields.isNotEmpty() && (it.statusCode == null || it.statusCode in 200..299)
        }

        if (hasStructuredResponse) {
            val cookie = CookieManager.getInstance().getCookie(profile.cookieDomain).orEmpty()
            if (cookie.isNotBlank()) {
                saveCredentialAndRefresh(cookie, closeAfterSave = false)
            }
        }

        showCancelToast = false
        Toast.makeText(
            this,
            if (hasStructuredResponse) {
                "摸排完成，返回配置页查看下一步"
            } else {
                "尚未捕获到 JSON 数据；请登录后进入用量页再试"
            },
            Toast.LENGTH_LONG
        ).show()
        finish()
    }

    private fun checkAndSaveCookies() {
        if (loginDetected || cookieVerificationInProgress) return

        val cookieString = CookieManager.getInstance().getCookie(profile.cookieDomain).orEmpty()
        if (cookieString.isBlank()) return

        val cookies = parseCookieString(cookieString)
        if (profile.requiredCookieNames.isNotEmpty()) {
            if (!profile.requiredCookieNames.all(cookies::containsKey)) return
            saveCredentialAndRefresh(buildCookieHeader(cookies))
            return
        }

        val verificationUrl = profile.cookieVerificationUrl
        if (!verificationUrl.isNullOrBlank()) {
            verifyCookieAgainstAccountEndpoint(cookieString, verificationUrl)
            return
        }

        saveCredentialAndRefresh(buildCookieHeader(cookies))
    }

    private fun verifyCookieAgainstAccountEndpoint(cookieString: String, verificationUrl: String) {
        if (cookieVerificationInProgress || loginDetected) return
        cookieVerificationInProgress = true

        Thread {
            val valid = try {
                val conn = URL(verificationUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Cookie", cookieString)
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Referer", profile.loginUrl)
                conn.setRequestProperty("Origin", "https://platform.deepseek.com")
                conn.setRequestProperty("User-Agent", MOBILE_USER_AGENT)
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val responseCode = conn.responseCode
                val body = if (responseCode in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                }
                conn.disconnect()

                if (responseCode !in 200..299) {
                    false
                } else {
                    val root = JSONObject(body)
                    val data = root.optJSONObject("data")
                    root.optInt("code", -1) == 0 &&
                        data?.optInt("biz_code", -1) == 0 &&
                        data.optJSONObject("biz_data") != null
                }
            } catch (_: Exception) {
                false
            }

            runOnUiThread {
                cookieVerificationInProgress = false
                if (valid && !loginDetected) {
                    val cookies = parseCookieString(cookieString)
                    saveCredentialAndRefresh(buildCookieHeader(cookies))
                }
            }
        }.start()
    }

    private fun buildCookieHeader(cookies: Map<String, String>): String {
        return buildString {
            cookies.forEach { (name, value) ->
                if (isNotEmpty()) append("; ")
                append("$name=$value")
            }
        }
    }

    private fun saveCredentialAndRefresh(
        credential: String,
        closeAfterSave: Boolean = true
    ): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val authConfig = BackgroundAuthConfig(
            authType = profile.authType,
            authValue = credential,
            enabled = true,
            updatedAt = System.currentTimeMillis()
        )

        val saved = BackgroundAuthRepository.save(
            prefs = prefs,
            instanceKey = targetInstanceKey,
            config = authConfig
        )

        if (!saved) {
            loginDetected = false
            Toast.makeText(this, "授权保存失败，请重试", Toast.LENGTH_SHORT).show()
            return false
        }

        loginDetected = true
        if (!profile.probeOnly) {
            Toast.makeText(
                this,
                "${profile.displayName} 网页授权成功",
                Toast.LENGTH_SHORT
            ).show()
        }

        refreshWidget(this)
        if (closeAfterSave) finish()
        return true
    }

    /** 每 2 秒读取一次 localStorage，直到获得非空 Token。 */
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
                val intent = Intent(context, BalanceWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
                context.sendBroadcast(intent)
            }
        } catch (_: Exception) {
            // Widget 不存在时不影响授权保存。
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
                if (name.isNotEmpty()) result[name] = value
            }
        }
        return result
    }

    private inner class ProbeBridge {
        @JavascriptInterface
        fun onResponse(payload: String) {
            try {
                val obj = JSONObject(payload)
                DeepSeekWebProbeRepository.recordResponse(
                    context = applicationContext,
                    instanceKey = targetInstanceKey,
                    url = obj.optString("url", ""),
                    method = obj.optString("method", "GET"),
                    statusCode = obj.optInt("status", 0),
                    body = obj.optString("body", "")
                )
                runOnUiThread { updateProbeStatus() }
            } catch (_: Exception) {
                // 诊断桥只忽略无法解析的事件，不影响网页登录。
            }
        }
    }
}
