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
import com.java.myapplication.adapter.WidgetData
import com.java.myapplication.config.ServiceHostMatcher
import com.java.myapplication.webauth.DeepSeekWebProbeRepository
import com.java.myapplication.webauth.WebAuthProfile
import com.java.myapplication.webauth.WebAuthProfileRegistry
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 通用网页登录授权 Activity。
 *
 * 正式路径：
 * - MiMo：网页登录 Cookie；
 * - DeepSeek：监听页面自己发出的已登录账户请求，保存 Cookie + 临时访问 Token；
 * - 爱黄牛：读取 localStorage auth_token。
 *
 * 凭据只写入 BackgroundAuthRepository，不输出到日志或界面。
 */
class WebAuthActivity : Activity() {

    companion object {
        private const val EXTRA_PROFILE_ID = "web_auth_profile_id"
        private const val EXTRA_TARGET_INSTANCE_KEY = "web_auth_target_instance_key"
        private const val PREFS_NAME = "api_config"
        private const val AUTH_POLL_INTERVAL_MS = 1000L
        private const val AUTH_OBSERVER_INSTALL_ATTEMPTS = 32
        private const val AUTH_OBSERVER_INSTALL_INTERVAL_MS = 250L
        private const val PROBE_INSTALL_ATTEMPTS = 24
        private const val PROBE_INSTALL_INTERVAL_MS = 250L
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

        /**
         * 复用此前已经在真机成功捕获 DeepSeek 接口的方式：不再自行猜请求头，
         * 而是监听页面自身的 fetch / XHR 成功响应。
         */
        private val DEEPSEEK_AUTH_OBSERVER_SCRIPT = """
            (function() {
                function report(kind, token) {
                    try {
                        if (!window.DashboardAuth) return;
                        window.DashboardAuth.onObservedSession(JSON.stringify({
                            kind: String(kind || ''),
                            token: String(token || '')
                        }));
                    } catch (e) {}
                }

                function bearerFrom(value) {
                    var text = String(value || '');
                    var match = text.match(/^Bearer\s+(.+)$/i);
                    return match ? match[1] : '';
                }

                function inspect(url, status, bodyText, authorization) {
                    try {
                        var target = String(url || '');
                        if (target.indexOf('platform.deepseek.com/') < 0 &&
                            target.indexOf('/api/v0/') < 0 &&
                            target.indexOf('/auth-api/v0/') < 0) return;

                        var body = JSON.parse(String(bodyText || ''));
                        var data = body && body.data;
                        var biz = data && data.biz_data;
                        var validEnvelope = Number(status || 0) >= 200 && Number(status || 0) < 300 &&
                            body && body.code === 0 && data && data.biz_code === 0 && biz;
                        if (!validEnvelope) return;

                        if (target.indexOf('/auth-api/v0/users/current') >= 0) {
                            var accountToken = biz.token || '';
                            var accountId = biz.id || '';
                            if (accountToken || accountId) report('current-user', accountToken);
                            return;
                        }

                        if (target.indexOf('/api/v0/users/get_user_summary') >= 0) {
                            report('summary', bearerFrom(authorization));
                        }
                    } catch (e) {}
                }

                function readAuthorization(input, init) {
                    try {
                        if (init && init.headers) {
                            var initHeaders = new Headers(init.headers);
                            var fromInit = initHeaders.get('Authorization');
                            if (fromInit) return fromInit;
                        }
                        if (input && input.headers && input.headers.get) {
                            return input.headers.get('Authorization') || '';
                        }
                    } catch (e) {}
                    return '';
                }

                if (!window.__dashboardDeepSeekAuthObserverInstalled) {
                    window.__dashboardDeepSeekAuthObserverInstalled = true;

                    var originalFetch = window.fetch;
                    if (originalFetch) {
                        window.fetch = function(input, init) {
                            var url = (typeof input === 'string') ? input : ((input && input.url) || '');
                            var authorization = readAuthorization(input, init);
                            return originalFetch.apply(this, arguments).then(function(response) {
                                try {
                                    var clone = response.clone();
                                    clone.text().then(function(text) {
                                        inspect(clone.url || url, clone.status, text, authorization);
                                    }).catch(function() {});
                                } catch (e) {}
                                return response;
                            });
                        };
                    }

                    var originalOpen = XMLHttpRequest.prototype.open;
                    var originalSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
                    var originalSend = XMLHttpRequest.prototype.send;

                    XMLHttpRequest.prototype.open = function(method, url) {
                        this.__dashboardAuthUrl = url || '';
                        this.__dashboardAuthHeader = '';
                        return originalOpen.apply(this, arguments);
                    };

                    XMLHttpRequest.prototype.setRequestHeader = function(name, value) {
                        try {
                            if (String(name || '').toLowerCase() === 'authorization') {
                                this.__dashboardAuthHeader = value || '';
                            }
                        } catch (e) {}
                        return originalSetRequestHeader.apply(this, arguments);
                    };

                    XMLHttpRequest.prototype.send = function() {
                        var xhr = this;
                        xhr.addEventListener('loadend', function() {
                            try {
                                var text = '';
                                if (!xhr.responseType || xhr.responseType === 'text' || xhr.responseType === 'json') {
                                    text = (typeof xhr.responseText === 'string')
                                        ? xhr.responseText
                                        : JSON.stringify(xhr.response || {});
                                }
                                inspect(
                                    xhr.responseURL || xhr.__dashboardAuthUrl,
                                    xhr.status,
                                    text,
                                    xhr.__dashboardAuthHeader || ''
                                );
                            } catch (e) {}
                        });
                        return originalSend.apply(this, arguments);
                    };

                    try {
                        window.dispatchEvent(new Event('focus'));
                        document.dispatchEvent(new Event('visibilitychange'));
                    } catch (e) {}
                }

                // 已经登录后直接打开用量页时，请求可能早于注入发生。
                // 同一标签页只自动刷新一次，让网页用自己的完整请求头重新请求账户数据。
                try {
                    if (location.hostname === 'platform.deepseek.com' &&
                        location.pathname.indexOf('/usage') >= 0 &&
                        sessionStorage.getItem('__dashboardDeepSeekAuthReloaded') !== '1') {
                        sessionStorage.setItem('__dashboardDeepSeekAuthReloaded', '1');
                        setTimeout(function() { location.reload(); }, 350);
                    }
                } catch (e) {}

                return 'ready';
            })();
        """.trimIndent()

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
    @Volatile private var credentialVerificationInProgress = false

    private val authHandler = Handler(Looper.getMainLooper())
    private var cookiePollRunnable: Runnable? = null
    private var localStoragePollRunnable: Runnable? = null
    private var authObserverInstallAttemptsRemaining = 0
    private val authObserverInstallRunnable = object : Runnable {
        override fun run() {
            if (!::webView.isInitialized || loginDetected || profile.probeOnly || !isDeepSeekProfile()) return
            webView.evaluateJavascript(DEEPSEEK_AUTH_OBSERVER_SCRIPT, null)
            authObserverInstallAttemptsRemaining--
            if (authObserverInstallAttemptsRemaining > 0 && !loginDetected) {
                authHandler.postDelayed(this, AUTH_OBSERVER_INSTALL_INTERVAL_MS)
            }
        }
    }

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
            ?: profile.sharedAuthKey

        setContentView(R.layout.activity_web_auth)
        title = if (profile.probeOnly) {
            "${profile.displayName} 网页数据摸排"
        } else {
            "${profile.displayName} 平台账户登录"
        }

        webView = findViewById(R.id.web_view)
        progressBar = findViewById(R.id.progress_bar)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = MOBILE_USER_AGENT
        }

        webView.addJavascriptInterface(AuthBridge(), "DashboardAuth")

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
            ): Boolean {
                val url = request?.url?.toString().orEmpty()
                if (request == null || !request.isForMainFrame || isAllowedPage(url)) return false
                Toast.makeText(this@WebAuthActivity, "已阻止跳转到非平台网页", Toast.LENGTH_SHORT).show()
                return true
            }

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
                if (!isAllowedPage(url.orEmpty())) {
                    view?.stopLoading()
                    return
                }
                when {
                    profile.probeOnly -> scheduleProbeInjection()
                    isDeepSeekProfile() -> scheduleAuthObserverInjection()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                if (!isAllowedPage(url.orEmpty())) return

                if (profile.probeOnly) {
                    scheduleProbeInjection()
                    updateProbeStatus()
                    return
                }

                when {
                    isDeepSeekProfile() -> scheduleAuthObserverInjection()
                    profile.authType == BackgroundAuthType.COOKIE -> startCookiePolling()
                    profile.authType == BackgroundAuthType.BEARER_TOKEN -> startLocalStoragePolling()
                }
            }
        }

        webView.loadUrl(profile.loginUrl)
    }

    override fun onDestroy() {
        cookiePollRunnable?.let { authHandler.removeCallbacks(it) }
        localStoragePollRunnable?.let { authHandler.removeCallbacks(it) }
        authHandler.removeCallbacks(authObserverInstallRunnable)
        probeHandler.removeCallbacks(probeInstallRunnable)
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("DashboardAuth")
            webView.removeJavascriptInterface("DashboardProbe")
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun isDeepSeekProfile(): Boolean {
        return profile.profileId.equals("deepseek", ignoreCase = true)
    }

    private fun scheduleAuthObserverInjection() {
        authHandler.removeCallbacks(authObserverInstallRunnable)
        authObserverInstallAttemptsRemaining = AUTH_OBSERVER_INSTALL_ATTEMPTS
        authHandler.post(authObserverInstallRunnable)
    }

    private fun startCookiePolling() {
        cookiePollRunnable?.let { authHandler.removeCallbacks(it) }
        val runnable = object : Runnable {
            override fun run() {
                if (loginDetected || isFinishing || isDestroyed) return
                checkAndSaveCookies()
                if (!loginDetected) {
                    authHandler.postDelayed(this, AUTH_POLL_INTERVAL_MS)
                }
            }
        }
        cookiePollRunnable = runnable
        authHandler.post(runnable)
    }

    private fun checkAndSaveCookies() {
        if (loginDetected || credentialVerificationInProgress || !isAllowedPage(webView.url.orEmpty())) return

        val cookieString = currentCookieString()
        if (cookieString.isBlank()) return

        val cookies = parseCookieString(cookieString)
        if (profile.requiredCookieNames.isNotEmpty()) {
            if (!profile.requiredCookieNames.all(cookies::containsKey)) return
            verifyCookieAndSave(buildCookieHeader(cookies))
            return
        }

        saveCredentialAndRefresh(buildCookieHeader(cookies))
    }

    private fun verifyCookieAndSave(cookieHeader: String) {
        val verificationUrl = profile.cookieVerificationUrl
        if (verificationUrl.isNullOrBlank()) {
            saveCredentialAndRefresh(cookieHeader)
            return
        }
        credentialVerificationInProgress = true
        Thread {
            var connection: HttpURLConnection? = null
            val valid = try {
                connection = URL(verificationUrl).openConnection() as HttpURLConnection
                connection?.requestMethod = "GET"
                connection?.connectTimeout = 10_000
                connection?.readTimeout = 10_000
                connection?.setRequestProperty("Cookie", cookieHeader)
                connection?.setRequestProperty("Accept", "application/json")
                (connection?.responseCode ?: -1) in 200..299
            } catch (_: Exception) {
                false
            } finally {
                connection?.disconnect()
            }
            runOnUiThread {
                credentialVerificationInProgress = false
                if (valid && !loginDetected) saveCredentialAndRefresh(cookieHeader)
            }
        }.start()
    }

    private fun isAllowedPage(url: String): Boolean {
        if (!url.startsWith("https://", ignoreCase = true)) return false
        val allowedHosts = buildSet {
            ServiceHostMatcher.hostOf(profile.loginUrl)?.let(::add)
            ServiceHostMatcher.hostOf(profile.cookieDomain)?.let(::add)
            ServiceHostMatcher.hostOf(profile.cookieVerificationUrl.orEmpty())?.let(::add)
            addAll(profile.apiBaseHostPatterns)
        }
        return ServiceHostMatcher.matchesAny(url, allowedHosts)
    }

    private fun currentCookieString(): String {
        val manager = CookieManager.getInstance()
        manager.flush()
        val candidates = linkedSetOf(
            profile.loginUrl,
            profile.cookieDomain,
            if (profile.cookieDomain.startsWith("http", ignoreCase = true)) {
                profile.cookieDomain
            } else {
                "https://${profile.cookieDomain}"
            }
        )
        return candidates.asSequence()
            .mapNotNull { candidate -> manager.getCookie(candidate) }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    private fun buildCookieHeader(cookies: Map<String, String>): String {
        return buildString {
            cookies.forEach { (name, value) ->
                if (isNotEmpty()) append("; ")
                append("$name=$value")
            }
        }
    }

    private fun buildDeepSeekCredential(cookie: String, accessToken: String): String? {
        if (cookie.isBlank() && accessToken.isBlank()) return null
        return JSONObject()
            .put("format", "deepseek-web-v1")
            .put("cookie", cookie)
            .put("accessToken", accessToken)
            .toString()
    }

    private fun saveCredentialAndRefresh(
        credential: String,
        closeAfterSave: Boolean = true
    ): Boolean {
        if (credential.isBlank()) return false

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
            Toast.makeText(this, "账户连接保存失败，请重试", Toast.LENGTH_SHORT).show()
            return false
        }

        prefs.edit()
            .putString("web_auth_profile_${targetInstanceKey.lowercase()}", profile.profileId)
            .commit()

        WidgetData.clearLastSuccessfulDataForInstance(targetInstanceKey)

        loginDetected = true
        cookiePollRunnable?.let { authHandler.removeCallbacks(it) }
        localStoragePollRunnable?.let { authHandler.removeCallbacks(it) }
        authHandler.removeCallbacks(authObserverInstallRunnable)

        if (!profile.probeOnly) {
            Toast.makeText(this, "平台账户连接成功", Toast.LENGTH_SHORT).show()
        }

        refreshWidget(this)
        if (closeAfterSave) finish()
        return true
    }

    private fun startLocalStoragePolling() {
        localStoragePollRunnable?.let { authHandler.removeCallbacks(it) }
        val key = profile.localStorageKey ?: return

        val runnable = object : Runnable {
            override fun run() {
                if (loginDetected || isFinishing || isDestroyed) return
                webView.evaluateJavascript(
                    "localStorage.getItem(${JSONObject.quote(key)})"
                ) { value ->
                    if (loginDetected || !isAllowedPage(webView.url.orEmpty())) return@evaluateJavascript
                    val token = value?.trim()?.removeSurrounding("\"")?.trim()
                    if (!token.isNullOrBlank() && token != "null") {
                        saveCredentialAndRefresh(token)
                    }
                }
                if (!loginDetected) {
                    authHandler.postDelayed(this, AUTH_POLL_INTERVAL_MS)
                }
            }
        }
        localStoragePollRunnable = runnable
        authHandler.post(runnable)
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
            records.isEmpty() -> "请登录并进入用量页面。系统只读取接口地址和字段结构。"
            structured == 0 -> "已看到 ${records.size} 个网络请求，正在等待可读取的 JSON 数据接口…"
            else -> "已捕获 ${records.size} 个接口，其中 $structured 个返回了可识别字段。"
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
            .setTitle("网页接口捕获结果")
            .setMessage("这里只显示 endpoint、状态码和 JSON 字段类型，不显示账户数值或凭据。")
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
            val cookie = currentCookieString()
            if (cookie.isNotBlank()) {
                saveCredentialAndRefresh(cookie, closeAfterSave = false)
            }
        }

        Toast.makeText(
            this,
            if (hasStructuredResponse) "摸排完成" else "尚未捕获到可识别的 JSON 数据",
            Toast.LENGTH_LONG
        ).show()
        finish()
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

    private inner class AuthBridge {
        @JavascriptInterface
        fun onObservedSession(result: String) {
            runOnUiThread {
                if (loginDetected || !isAllowedPage(webView.url.orEmpty())) return@runOnUiThread

                val payload = try {
                    JSONObject(result)
                } catch (_: Exception) {
                    return@runOnUiThread
                }
                val token = payload.optString("token", "").trim()
                val cookie = currentCookieString()
                val credential = buildDeepSeekCredential(cookie, token)
                if (!credential.isNullOrBlank()) {
                    saveCredentialAndRefresh(credential)
                }
            }
        }
    }

    private inner class ProbeBridge {
        @JavascriptInterface
        fun onResponse(payload: String) {
            try {
                if (!isAllowedPage(webView.url.orEmpty())) return
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
                // 摸排桥只忽略无法解析的事件，不影响网页登录。
            }
        }
    }
}
