package com.java.myapplication.discovery

import android.app.Activity
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.java.myapplication.R
import org.json.JSONTokener
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

class DashboardDiscoveryActivity : Activity() {
    companion object {
        private const val INJECTION_ATTEMPTS = 36
        private const val INJECTION_INTERVAL_MS = 150L
        private const val CAPTURE_STATUS_INTERVAL_MS = 800L

        private val CAPTURE_SCRIPT = """
            (function() {
                if (window.__aadCaptureInstalled) return 'ready';
                window.__aadCaptureInstalled = true;
                window.__aadCaptureBuffer = [];

                function keep(url, method, status, body) {
                    try {
                        var text = String(body || '');
                        if (!text || text.length > 60000) text = text.substring(0, 60000);
                        var first = text.trim().charAt(0);
                        if (first !== '{' && first !== '[') return;
                        JSON.parse(text);
                        window.__aadCaptureBuffer.push({
                            url: String(url || '').substring(0, 2000),
                            method: String(method || 'GET').substring(0, 12),
                            status: Number(status || 0),
                            body: text
                        });
                        if (window.__aadCaptureBuffer.length > 30) window.__aadCaptureBuffer.shift();
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
                                    keep(clone.url || url, method, clone.status, text);
                                }).catch(function() {});
                            } catch (e) {}
                            return response;
                        });
                    };
                }

                var originalOpen = XMLHttpRequest.prototype.open;
                var originalSend = XMLHttpRequest.prototype.send;
                XMLHttpRequest.prototype.open = function(method, url) {
                    this.__aadMethod = method || 'GET';
                    this.__aadUrl = url || '';
                    return originalOpen.apply(this, arguments);
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
                            keep(xhr.responseURL || xhr.__aadUrl, xhr.__aadMethod, xhr.status, text);
                        } catch (e) {}
                    });
                    return originalSend.apply(this, arguments);
                };
                return 'installed';
            })();
        """.trimIndent()

        private val EXPORT_SCRIPT = """
            (function() {
                try {
                    return JSON.stringify({
                        pageUrl: location.origin + location.pathname,
                        visibleText: String((document.body && document.body.innerText) || '').substring(0, 12000),
                        records: (window.__aadCaptureBuffer || []).slice(-30)
                    });
                } catch (e) {
                    return '';
                }
            })();
        """.trimIndent()
    }

    private lateinit var setupPanel: View
    private lateinit var browserPanel: View
    private lateinit var resultPanel: View
    private lateinit var apiBaseInput: EditText
    private lateinit var apiKeyInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var dashboardUrlInput: EditText
    private lateinit var thirdPartyCookiesInput: CheckBox
    private lateinit var setupError: TextView
    private lateinit var browserStatus: TextView
    private lateinit var progress: ProgressBar
    private lateinit var webView: WebView
    private lateinit var confirmButton: Button
    private lateinit var analyzeButton: Button
    private lateinit var resultSummary: TextView
    private lateinit var resultBody: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val aiAnalyzer = DashboardAiAnalyzer()
    private var documentStartScriptHandler: ScriptHandler? = null
    private var injectionAttemptsRemaining = 0
    private var captureArmed = false
    private var earlyCaptureActive = false
    private var captureModeLabel = "兼容捕获"
    private var analysisInProgress = false
    private var lockedOrigin: String? = null
    private var apiBase = ""
    private var apiKey = ""
    private var model = ""

    private val injectionRunnable = object : Runnable {
        override fun run() {
            if (!captureArmed || !::webView.isInitialized || !isOnLockedOrigin()) return
            webView.evaluateJavascript(CAPTURE_SCRIPT, null)
            injectionAttemptsRemaining--
            if (injectionAttemptsRemaining > 0) {
                handler.postDelayed(this, INJECTION_INTERVAL_MS)
            }
        }
    }

    private val captureStatusRunnable = object : Runnable {
        override fun run() {
            if (!captureArmed || !::webView.isInitialized) return
            webView.evaluateJavascript("String((window.__aadCaptureBuffer || []).length)") { value ->
                if (!captureArmed) return@evaluateJavascript
                val count = value.orEmpty().trim('"').toIntOrNull() ?: 0
                browserStatus.text = "$captureModeLabel · 已捕获 $count 条 JSON 响应\n${lockedOrigin.orEmpty()}"
            }
            handler.postDelayed(this, CAPTURE_STATUS_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard_discovery)
        bindViews()
        configureWebView()
        bindActions()
    }

    private fun bindViews() {
        setupPanel = findViewById(R.id.discovery_setup_panel)
        browserPanel = findViewById(R.id.discovery_browser_panel)
        resultPanel = findViewById(R.id.discovery_result_panel)
        apiBaseInput = findViewById(R.id.discovery_api_base)
        apiKeyInput = findViewById(R.id.discovery_api_key)
        modelInput = findViewById(R.id.discovery_model)
        dashboardUrlInput = findViewById(R.id.discovery_dashboard_url)
        thirdPartyCookiesInput = findViewById(R.id.discovery_third_party_cookies)
        setupError = findViewById(R.id.discovery_setup_error)
        browserStatus = findViewById(R.id.discovery_browser_status)
        progress = findViewById(R.id.discovery_progress)
        webView = findViewById(R.id.discovery_web_view)
        confirmButton = findViewById(R.id.discovery_confirm_page)
        analyzeButton = findViewById(R.id.discovery_analyze)
        resultSummary = findViewById(R.id.discovery_result_summary)
        resultBody = findViewById(R.id.discovery_result_body)
    }

    private fun configureWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request == null || !request.isForMainFrame) return false
                if (DashboardDiscoveryRules.isHttpsUrl(request.url.toString())) return false
                Toast.makeText(this@DashboardDiscoveryActivity, "实验室只允许打开 HTTPS 网页", Toast.LENGTH_SHORT).show()
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progress.visibility = View.VISIBLE
                showCurrentUrl(url)
                if (captureArmed && DashboardDiscoveryRules.originOf(url.orEmpty()) != lockedOrigin) {
                    disarmCapture("页面已跳转到其他站点，请重新确认当前页面")
                } else if (captureArmed && !earlyCaptureActive) {
                    scheduleInjection()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progress.visibility = View.GONE
                showCurrentUrl(url)
                if (captureArmed && isOnLockedOrigin() && !earlyCaptureActive) scheduleInjection()
            }
        }
    }

    private fun bindActions() {
        findViewById<Button>(R.id.discovery_open_dashboard).setOnClickListener { openDashboard() }
        confirmButton.setOnClickListener { confirmAndCapture() }
        analyzeButton.setOnClickListener { analyzeCapture() }
        findViewById<Button>(R.id.discovery_back_to_browser).setOnClickListener { showBrowser() }
        findViewById<Button>(R.id.discovery_restart).setOnClickListener { restartExperiment() }
    }

    private fun openDashboard() {
        val candidateApiBase = apiBaseInput.text.toString().trim()
        val candidateApiKey = apiKeyInput.text.toString().trim()
        val candidateModel = modelInput.text.toString().trim()
        val candidateDashboardUrl = dashboardUrlInput.text.toString().trim()
        val error = when {
            DashboardDiscoveryRules.normalizeChatCompletionsUrl(candidateApiBase) == null -> "模型 API Base 必须是有效的 HTTPS 地址"
            candidateApiKey.isBlank() -> "请填写 API Key"
            candidateModel.isBlank() -> "请填写模型名称"
            !DashboardDiscoveryRules.isHttpsUrl(candidateDashboardUrl) -> "仪表盘网址必须是有效的 HTTPS 地址"
            else -> null
        }
        if (error != null) {
            setupError.text = error
            setupError.visibility = View.VISIBLE
            return
        }

        apiBase = candidateApiBase
        apiKey = candidateApiKey
        model = candidateModel
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, thirdPartyCookiesInput.isChecked)
        setupError.visibility = View.GONE
        setupPanel.visibility = View.GONE
        resultPanel.visibility = View.GONE
        browserPanel.visibility = View.VISIBLE
        browserStatus.text = "请登录并打开要识别的仪表盘页面"
        webView.loadUrl(candidateDashboardUrl)
    }

    private fun confirmAndCapture() {
        val currentUrl = webView.url.orEmpty()
        val origin = DashboardDiscoveryRules.originOf(currentUrl)
        if (origin == null) {
            Toast.makeText(this, "当前页面不是有效的 HTTPS 页面", Toast.LENGTH_SHORT).show()
            return
        }
        lockedOrigin = origin
        captureArmed = true
        confirmButton.isEnabled = false
        analyzeButton.isEnabled = true
        earlyCaptureActive = installDocumentStartCapture(origin)
        captureModeLabel = if (earlyCaptureActive) "提前捕获已启用" else "兼容捕获已启用"
        browserStatus.text = "$captureModeLabel，正在刷新页面，请等待数据加载完成"
        if (!earlyCaptureActive) scheduleInjection()
        handler.removeCallbacks(captureStatusRunnable)
        handler.postDelayed(captureStatusRunnable, CAPTURE_STATUS_INTERVAL_MS)
        webView.reload()
    }

    private fun analyzeCapture() {
        if (!captureArmed || analysisInProgress || !isOnLockedOrigin()) return
        analysisInProgress = true
        analyzeButton.isEnabled = false
        browserStatus.text = "正在读取并脱敏本次捕获…"
        webView.evaluateJavascript(EXPORT_SCRIPT) { rawValue ->
            val exported = decodeJavascriptString(rawValue)
            if (exported.isBlank()) {
                finishAnalysisWithError("没有读到页面捕获结果，请重新确认页面")
                return@evaluateJavascript
            }
            Thread {
                try {
                    val capture = DashboardCaptureSanitizer.prepare(exported, lockedOrigin.orEmpty())
                    if (capture.candidateCount == 0) {
                        runOnUiThread {
                            if (!isDestroyed) {
                                finishAnalysisWithError("暂未捕获到 JSON 响应，请让页面数据加载完成后再试")
                            }
                        }
                    } else {
                        runOnUiThread {
                            if (!isDestroyed) {
                                browserStatus.text = "正在调用模型核对数据，通常需要 10～90 秒，请稍候…"
                            }
                        }
                        val result = aiAnalyzer.analyze(apiBase, apiKey, model, capture)
                        runOnUiThread {
                            if (!isDestroyed) showResult(result, capture)
                        }
                    }
                } catch (error: Exception) {
                    runOnUiThread {
                        if (!isDestroyed) finishAnalysisWithError(userFacingError(error))
                    }
                }
            }.start()
        }
    }

    private fun showResult(result: DashboardAnalysisResult, capture: PreparedDashboardCapture) {
        analysisInProgress = false
        browserPanel.visibility = View.GONE
        setupPanel.visibility = View.GONE
        resultPanel.visibility = View.VISIBLE
        resultSummary.text = "${result.summary}\n捕获 ${capture.rawCaptureCount} 条，筛选 ${capture.candidateCount} 条；已验证字段才具备后续自动刷新资格。"
        resultBody.text = result.displayBody
    }

    private fun finishAnalysisWithError(message: String) {
        analysisInProgress = false
        analyzeButton.isEnabled = captureArmed
        browserStatus.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showBrowser() {
        resultPanel.visibility = View.GONE
        setupPanel.visibility = View.GONE
        browserPanel.visibility = View.VISIBLE
        analyzeButton.isEnabled = captureArmed && !analysisInProgress
    }

    private fun restartExperiment() {
        disarmCapture("请重新填写并开始")
        webView.loadUrl("about:blank")
        apiKey = ""
        apiKeyInput.text?.clear()
        resultBody.text = ""
        resultSummary.text = ""
        browserPanel.visibility = View.GONE
        resultPanel.visibility = View.GONE
        setupPanel.visibility = View.VISIBLE
    }

    private fun scheduleInjection() {
        handler.removeCallbacks(injectionRunnable)
        injectionAttemptsRemaining = INJECTION_ATTEMPTS
        handler.post(injectionRunnable)
    }

    private fun installDocumentStartCapture(origin: String): Boolean {
        removeDocumentStartCapture()
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return false
        return try {
            documentStartScriptHandler = WebViewCompat.addDocumentStartJavaScript(
                webView,
                CAPTURE_SCRIPT,
                setOf(origin)
            )
            true
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: UnsupportedOperationException) {
            false
        }
    }

    private fun removeDocumentStartCapture() {
        val scriptHandler = documentStartScriptHandler ?: return
        documentStartScriptHandler = null
        runCatching { scriptHandler.remove() }
    }

    private fun disarmCapture(message: String) {
        captureArmed = false
        earlyCaptureActive = false
        lockedOrigin = null
        removeDocumentStartCapture()
        handler.removeCallbacks(injectionRunnable)
        handler.removeCallbacks(captureStatusRunnable)
        confirmButton.isEnabled = true
        analyzeButton.isEnabled = false
        browserStatus.text = message
    }

    private fun isOnLockedOrigin(): Boolean {
        return lockedOrigin != null && DashboardDiscoveryRules.originOf(webView.url.orEmpty()) == lockedOrigin
    }

    private fun showCurrentUrl(url: String?) {
        if (!captureArmed) {
            browserStatus.text = DashboardDiscoveryRules.withoutQuery(url.orEmpty()).ifBlank {
                "请登录并打开要识别的仪表盘页面"
            }
        }
    }

    private fun decodeJavascriptString(value: String?): String {
        return try {
            (JSONTokener(value.orEmpty()).nextValue() as? String).orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun userFacingError(error: Exception): String {
        return when (error) {
            is SocketTimeoutException -> "模型在 120 秒内没有完成识别，请稍后重试或换用响应更快的模型"
            is SSLException -> "模型接口 HTTPS 连接失败"
            is IllegalArgumentException, is IllegalStateException -> error.message ?: "识别失败"
            else -> "识别请求失败，请检查网络和接口配置"
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            resultPanel.visibility == View.VISIBLE -> showBrowser()
            browserPanel.visibility == View.VISIBLE -> {
                disarmCapture("已返回配置页")
                webView.loadUrl("about:blank")
                browserPanel.visibility = View.GONE
                setupPanel.visibility = View.VISIBLE
            }
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        aiAnalyzer.cancel()
        removeDocumentStartCapture()
        apiKey = ""
        if (::apiKeyInput.isInitialized) apiKeyInput.text?.clear()
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearCache(true)
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
        WebStorage.getInstance().deleteAllData()
        super.onDestroy()
    }
}
