package com.java.myapplication.discovery

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.java.myapplication.ModelCatalogClient
import com.java.myapplication.R
import com.java.myapplication.TestResult
import com.java.myapplication.config.ApiAccountConfig
import com.java.myapplication.config.ConfigRepository
import com.java.myapplication.config.InstanceKeyResolver
import com.java.myapplication.refreshWidget
import org.json.JSONTokener
import java.net.SocketTimeoutException
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLException

class DashboardDiscoveryActivity : Activity() {
    companion object {
        private const val INJECTION_ATTEMPTS = 36
        private const val INJECTION_INTERVAL_MS = 150L
        private const val CAPTURE_STATUS_INTERVAL_MS = 800L
        const val EXTRA_UI_TEST_STALE_RECIPE_STATE =
            "com.java.myapplication.discovery.extra.UI_TEST_STALE_RECIPE_STATE"

        private val CAPTURE_SCRIPT = """
            (function() {
                if (window.__aadCaptureInstalled) return 'ready';
                window.__aadCaptureInstalled = true;
                window.__aadCaptureBuffer = [];

                function captureBody(value) {
                    try {
                        if (value === undefined || value === null) {
                            return { body: '', contentType: '', truncated: false, error: '' };
                        }
                        if (typeof URLSearchParams !== 'undefined' && value instanceof URLSearchParams) {
                            var formText = value.toString();
                            return {
                                body: formText.length > 16384 ? formText.substring(0, 16384) : formText,
                                contentType: 'application/x-www-form-urlencoded;charset=UTF-8',
                                truncated: formText.length > 16384,
                                error: ''
                            };
                        }
                        if (typeof FormData !== 'undefined' && value instanceof FormData) {
                            return { body: '', contentType: 'multipart/form-data', truncated: false,
                                error: 'multipart 或文件表单不能安全直接重放；需要网页登录辅助刷新' };
                        }
                        if (typeof Blob !== 'undefined' && value instanceof Blob) {
                            return { body: '', contentType: value.type || '', truncated: false,
                                error: '二进制请求体不能安全直接重放；需要网页登录辅助刷新' };
                        }
                        if (typeof ArrayBuffer !== 'undefined' &&
                            (value instanceof ArrayBuffer || ArrayBuffer.isView(value))) {
                            return { body: '', contentType: '', truncated: false,
                                error: '二进制请求体不能安全直接重放；需要网页登录辅助刷新' };
                        }
                        var text = String(value);
                        var truncated = text.length > 16384;
                        return {
                            body: truncated ? text.substring(0, 16384) : text,
                            contentType: '',
                            truncated: truncated,
                            error: ''
                        };
                    } catch (e) {
                        return { body: '', contentType: '', truncated: false,
                            error: '请求体无法读取；需要网页登录辅助刷新' };
                    }
                }

                function headerValue(headers, name) {
                    try {
                        var normalized = new Headers(headers || {});
                        return normalized.get(name) || '';
                    } catch (e) {
                        return '';
                    }
                }

                function keep(url, method, status, body, requestMeta) {
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
                            body: text,
                            requestBody: String((requestMeta && requestMeta.body) || ''),
                            requestContentType: String((requestMeta && requestMeta.contentType) || '').substring(0, 200),
                            requestBodyTruncated: Boolean(requestMeta && requestMeta.truncated),
                            requestCaptureError: String((requestMeta && requestMeta.error) || '').substring(0, 300)
                        });
                        if (window.__aadCaptureBuffer.length > 30) window.__aadCaptureBuffer.shift();
                    } catch (e) {}
                }

                var originalFetch = window.fetch;
                if (originalFetch) {
                    window.fetch = function(input, init) {
                        var method = (init && init.method) || (input && input.method) || 'GET';
                        var url = (typeof input === 'string') ? input : ((input && input.url) || '');
                        var requestHeaders = (init && init.headers) || (input && input.headers) || {};
                        var requestMetaPromise;
                        if (init && Object.prototype.hasOwnProperty.call(init, 'body')) {
                            requestMetaPromise = Promise.resolve(captureBody(init.body));
                        } else if (input && typeof input.clone === 'function' && String(method).toUpperCase() !== 'GET') {
                            try {
                                requestMetaPromise = input.clone().text().then(captureBody).catch(function() {
                                    return { body: '', contentType: '', truncated: false,
                                        error: '请求体无法读取；需要网页登录辅助刷新' };
                                });
                            } catch (e) {
                                requestMetaPromise = Promise.resolve({ body: '', contentType: '', truncated: false,
                                    error: '请求体无法读取；需要网页登录辅助刷新' });
                            }
                        } else {
                            requestMetaPromise = Promise.resolve(captureBody(null));
                        }
                        requestMetaPromise = requestMetaPromise.then(function(meta) {
                            meta.contentType = headerValue(requestHeaders, 'content-type') || meta.contentType || '';
                            return meta;
                        });
                        return originalFetch.apply(this, arguments).then(function(response) {
                            try {
                                var clone = response.clone();
                                Promise.all([clone.text(), requestMetaPromise]).then(function(values) {
                                    keep(clone.url || url, method, clone.status, values[0], values[1]);
                                }).catch(function() {});
                            } catch (e) {}
                            return response;
                        });
                    };
                }

                var originalOpen = XMLHttpRequest.prototype.open;
                var originalSend = XMLHttpRequest.prototype.send;
                var originalSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
                XMLHttpRequest.prototype.open = function(method, url) {
                    this.__aadMethod = method || 'GET';
                    this.__aadUrl = url || '';
                    this.__aadContentType = '';
                    return originalOpen.apply(this, arguments);
                };
                XMLHttpRequest.prototype.setRequestHeader = function(name, value) {
                    if (String(name || '').toLowerCase() === 'content-type') {
                        this.__aadContentType = String(value || '');
                    }
                    return originalSetRequestHeader.apply(this, arguments);
                };
                XMLHttpRequest.prototype.send = function(body) {
                    var xhr = this;
                    var requestMeta = captureBody(body);
                    requestMeta.contentType = xhr.__aadContentType || requestMeta.contentType || '';
                    xhr.addEventListener('loadend', function() {
                        try {
                            var text = '';
                            if (xhr.responseType === 'json') {
                                text = JSON.stringify(xhr.response || {});
                            } else if (!xhr.responseType || xhr.responseType === 'text') {
                                text = (typeof xhr.responseText === 'string')
                                    ? xhr.responseText
                                    : JSON.stringify(xhr.response || {});
                            }
                            keep(xhr.responseURL || xhr.__aadUrl, xhr.__aadMethod, xhr.status, text, requestMeta);
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
    private lateinit var modelDetectButton: Button
    private lateinit var modelStatus: TextView
    private lateinit var modelChoiceSpinner: Spinner
    private lateinit var openDashboardButton: Button
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
    private lateinit var resultRecipeStatus: TextView
    private lateinit var saveRecipeButton: Button
    private lateinit var savedRecipePanel: View
    private lateinit var savedRecipeSummary: TextView
    private lateinit var recipeInstanceSpinner: Spinner
    private lateinit var bindRecipeButton: Button
    private lateinit var unbindRecipeButton: Button
    private lateinit var refreshSavedRecipeButton: Button
    private lateinit var deleteSavedRecipeButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private val aiAnalyzer = DashboardAiAnalyzer()
    private val recipeClient = DashboardRecipeClient()
    private val recipeRepository by lazy { DashboardRecipeRepository(applicationContext) }
    private var documentStartScriptHandler: ScriptHandler? = null
    private var injectionAttemptsRemaining = 0
    @Volatile
    private var captureArmed = false
    private var earlyCaptureActive = false
    private var captureModeLabel = "兼容捕获"
    private var analysisInProgress = false
    @Volatile
    private var lockedOrigin: String? = null
    private val capturedReplayHeaders = ConcurrentHashMap<String, Map<String, String>>()
    private var apiBase = ""
    private var apiKey = ""
    private var model = ""
    private var detectedModels: List<String> = emptyList()
    private var detectedApiBase = ""
    private var detectedApiKey = ""
    private var modelDetectionGeneration = 0
    private var modelDetectionInProgress = false
    private var currentAnalysis: DashboardAnalysisResult? = null
    private var currentCapture: PreparedDashboardCapture? = null
    private var recipeInProgress = false
    private var bindableConfigs: List<ApiAccountConfig> = emptyList()

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
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val staleRecipeUiTest =
            debuggable && intent.getBooleanExtra(EXTRA_UI_TEST_STALE_RECIPE_STATE, false)
        if (staleRecipeUiTest) {
            bindActions()
            showStaleRecipeUiTestState()
            return
        }
        configureWebView()
        bindActions()
        updateSavedRecipePanel()
    }

    private fun showStaleRecipeUiTestState() {
        setupPanel.visibility = View.GONE
        browserPanel.visibility = View.GONE
        resultPanel.visibility = View.VISIBLE
        resultSummary.text = "自动化测试：已恢复识别结果页面"
        resultBody.text = "{}"
        resultRecipeStatus.text = "点击按钮验证状态反馈"
        saveRecipeButton.visibility = View.VISIBLE
        saveRecipeButton.isEnabled = true
        saveRecipeButton.text = "直接测试并加密保存"
    }

    private fun bindViews() {
        setupPanel = findViewById(R.id.discovery_setup_panel)
        browserPanel = findViewById(R.id.discovery_browser_panel)
        resultPanel = findViewById(R.id.discovery_result_panel)
        apiBaseInput = findViewById(R.id.discovery_api_base)
        apiKeyInput = findViewById(R.id.discovery_api_key)
        modelDetectButton = findViewById(R.id.discovery_detect_models)
        modelStatus = findViewById(R.id.discovery_model_status)
        modelChoiceSpinner = findViewById(R.id.discovery_model_choice)
        openDashboardButton = findViewById(R.id.discovery_open_dashboard)
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
        resultRecipeStatus = findViewById(R.id.discovery_recipe_status)
        saveRecipeButton = findViewById(R.id.discovery_save_recipe)
        savedRecipePanel = findViewById(R.id.discovery_saved_recipe_panel)
        savedRecipeSummary = findViewById(R.id.discovery_saved_recipe_summary)
        recipeInstanceSpinner = findViewById(R.id.discovery_recipe_instance)
        bindRecipeButton = findViewById(R.id.discovery_bind_recipe)
        unbindRecipeButton = findViewById(R.id.discovery_unbind_recipe)
        refreshSavedRecipeButton = findViewById(R.id.discovery_refresh_saved_recipe)
        deleteSavedRecipeButton = findViewById(R.id.discovery_delete_saved_recipe)
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
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                observeReplayHeaders(request)
                return super.shouldInterceptRequest(view, request)
            }

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

    private fun observeReplayHeaders(request: WebResourceRequest?) {
        val currentOrigin = lockedOrigin ?: return
        if (!captureArmed || request == null) return
        val requestKey = DashboardReplayHeaderPolicy.requestKey(
            method = request.method.orEmpty(),
            url = request.url.toString(),
            lockedOrigin = currentOrigin
        ) ?: return
        val headers = DashboardReplayHeaderPolicy.sanitize(request.requestHeaders.orEmpty())
        if (headers.isNotEmpty()) capturedReplayHeaders[requestKey] = headers
    }

    private fun bindActions() {
        modelDetectButton.setOnClickListener { detectModels() }
        openDashboardButton.setOnClickListener { openDashboard() }
        apiBaseInput.doAfterTextChanged { invalidateModelSelection() }
        apiKeyInput.doAfterTextChanged { invalidateModelSelection() }
        confirmButton.setOnClickListener { confirmAndCapture() }
        analyzeButton.setOnClickListener { analyzeCapture() }
        saveRecipeButton.setOnClickListener { testAndSaveRecipe() }
        bindRecipeButton.setOnClickListener { bindSavedRecipeToWidget() }
        unbindRecipeButton.setOnClickListener { unbindSavedRecipeFromWidget() }
        refreshSavedRecipeButton.setOnClickListener { refreshSavedRecipe() }
        deleteSavedRecipeButton.setOnClickListener { deleteSavedRecipe() }
        findViewById<Button>(R.id.discovery_back_to_browser).setOnClickListener { showBrowser() }
        findViewById<Button>(R.id.discovery_restart).setOnClickListener { restartExperiment() }
    }

    private fun detectModels() {
        if (modelDetectionInProgress) return
        val candidateApiBase = apiBaseInput.text.toString().trim()
        val candidateApiKey = apiKeyInput.text.toString().trim()
        val error = when {
            ModelCatalogClient.modelListUrl(candidateApiBase) == null -> "模型 API Base 必须是有效的 HTTPS 地址"
            candidateApiKey.isBlank() -> "请填写 API Key"
            else -> null
        }
        if (error != null) {
            invalidateModelSelection(error)
            return
        }

        val generation = ++modelDetectionGeneration
        modelDetectionInProgress = true
        detectedModels = emptyList()
        detectedApiBase = ""
        detectedApiKey = ""
        model = ""
        modelDetectButton.isEnabled = false
        modelChoiceSpinner.visibility = View.GONE
        openDashboardButton.isEnabled = false
        setupError.visibility = View.GONE
        modelStatus.text = "正在连接并读取模型列表…"

        Thread {
            val result = ModelCatalogClient.fetch(candidateApiBase, candidateApiKey)
            runOnUiThread {
                if (
                    isDestroyed ||
                    generation != modelDetectionGeneration ||
                    apiBaseInput.text.toString().trim() != candidateApiBase ||
                    apiKeyInput.text.toString().trim() != candidateApiKey
                ) {
                    return@runOnUiThread
                }
                modelDetectionInProgress = false
                modelDetectButton.isEnabled = true
                when (result) {
                    is TestResult.Success -> showDetectedModels(
                        candidateApiBase,
                        candidateApiKey,
                        result.models
                    )
                    is TestResult.Error -> {
                        modelStatus.text = "${result.title}\n${result.reason}\n${result.suggestion}"
                        modelChoiceSpinner.visibility = View.GONE
                        openDashboardButton.isEnabled = false
                    }
                }
            }
        }.start()
    }

    private fun showDetectedModels(apiBase: String, apiKey: String, models: List<String>) {
        detectedApiBase = apiBase
        detectedApiKey = apiKey
        detectedModels = models
        modelChoiceSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            models
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        modelChoiceSpinner.setSelection(0)
        modelChoiceSpinner.visibility = View.VISIBLE
        modelStatus.text = if (models.size == 1) {
            "连接成功，已选择 ${models.first()}"
        } else {
            "连接成功，找到 ${models.size} 个模型，请从下方选择"
        }
        openDashboardButton.isEnabled = true
    }

    private fun invalidateModelSelection(message: String = "地址或 Key 已改变，请重新测试连接") {
        modelDetectionGeneration++
        modelDetectionInProgress = false
        detectedModels = emptyList()
        detectedApiBase = ""
        detectedApiKey = ""
        model = ""
        if (::modelDetectButton.isInitialized) modelDetectButton.isEnabled = true
        if (::modelChoiceSpinner.isInitialized) modelChoiceSpinner.visibility = View.GONE
        if (::openDashboardButton.isInitialized) openDashboardButton.isEnabled = false
        if (::modelStatus.isInitialized) modelStatus.text = message
    }

    private fun openDashboard() {
        val candidateApiBase = apiBaseInput.text.toString().trim()
        val candidateApiKey = apiKeyInput.text.toString().trim()
        val candidateModel = detectedModels.getOrNull(modelChoiceSpinner.selectedItemPosition).orEmpty()
        val candidateDashboardUrl = dashboardUrlInput.text.toString().trim()
        val error = when {
            DashboardDiscoveryRules.normalizeChatCompletionsUrl(candidateApiBase) == null -> "模型 API Base 必须是有效的 HTTPS 地址"
            candidateApiKey.isBlank() -> "请填写 API Key"
            candidateApiBase != detectedApiBase || candidateApiKey != detectedApiKey || candidateModel.isBlank() ->
                "请先测试连接并选择模型"
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
        capturedReplayHeaders.clear()
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
        analyzeButton.text = "AI 识别中…"
        browserStatus.text = "正在读取并脱敏本次捕获…"
        webView.evaluateJavascript(EXPORT_SCRIPT) { rawValue ->
            val exported = decodeJavascriptString(rawValue)
            if (exported.isBlank()) {
                finishAnalysisWithError("没有读到页面捕获结果，请重新确认页面")
                return@evaluateJavascript
            }
            Thread {
                try {
                    val capture = DashboardCaptureSanitizer.prepare(
                        exportedJson = exported,
                        lockedOrigin = lockedOrigin.orEmpty(),
                        replayHeadersByRequest = capturedReplayHeaders.toMap()
                    )
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
        analyzeButton.text = "调用一次 AI 识别"
        currentAnalysis = result
        currentCapture = capture
        browserPanel.visibility = View.GONE
        setupPanel.visibility = View.GONE
        resultPanel.visibility = View.VISIBLE
        resultSummary.text = "${result.summary}\n捕获 ${capture.rawCaptureCount} 条，筛选 ${capture.candidateCount} 条；已验证字段才具备后续自动刷新资格。"
        resultBody.text = result.displayBody
        resultRecipeStatus.text = if (result.structureValid) {
            "下一步会使用当前网页登录状态直接请求一次；全部字段再次通过后才保存。"
        } else {
            "没有可保存的已验证字段。"
        }
        saveRecipeButton.text = "直接测试并加密保存"
        saveRecipeButton.visibility = if (result.structureValid) View.VISIBLE else View.GONE
        saveRecipeButton.isEnabled = result.structureValid
    }

    private fun finishAnalysisWithError(message: String) {
        analysisInProgress = false
        analyzeButton.isEnabled = captureArmed
        analyzeButton.text = if (captureArmed) "重新调用一次 AI 识别" else "调用一次 AI 识别"
        browserStatus.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showBrowser() {
        resultPanel.visibility = View.GONE
        if (captureArmed) {
            setupPanel.visibility = View.GONE
            browserPanel.visibility = View.VISIBLE
            analyzeButton.isEnabled = !analysisInProgress
        } else {
            browserPanel.visibility = View.GONE
            setupPanel.visibility = View.VISIBLE
            updateSavedRecipePanel()
        }
    }

    private fun restartExperiment() {
        disarmCapture("请重新填写并开始")
        webView.loadUrl("about:blank")
        apiKey = ""
        apiKeyInput.text?.clear()
        invalidateModelSelection("请先测试连接并选择模型")
        currentAnalysis = null
        currentCapture = null
        resultBody.text = ""
        resultSummary.text = ""
        resultRecipeStatus.text = ""
        saveRecipeButton.text = "直接测试并加密保存"
        saveRecipeButton.visibility = View.VISIBLE
        browserPanel.visibility = View.GONE
        resultPanel.visibility = View.GONE
        setupPanel.visibility = View.VISIBLE
        setupError.visibility = View.GONE
        updateSavedRecipePanel()
    }

    private fun testAndSaveRecipe() {
        if (recipeInProgress) {
            resultRecipeStatus.text = "正在直接测试，请稍候…"
            Toast.makeText(this, "直接测试仍在进行", Toast.LENGTH_SHORT).show()
            return
        }
        val analysis = currentAnalysis
        val capture = currentCapture
        if (analysis == null || capture == null) {
            showRecipePreparationError("当前识别结果已经失效，请返回仪表盘重新识别后再试")
            return
        }
        saveRecipeButton.isEnabled = false
        saveRecipeButton.text = "正在准备直连测试…"
        resultRecipeStatus.text = "正在检查接口与本机登录状态…"
        val origin = lockedOrigin.orEmpty()
        val draft = runCatching {
            DashboardRecipeRules.create(
                pagePurpose = analysis.pagePurpose,
                dashboardUrl = webView.url.orEmpty(),
                lockedOrigin = origin,
                metrics = analysis.verifiedMetrics,
                candidates = capture.candidates,
                now = System.currentTimeMillis()
            )
        }.getOrElse { error ->
            val exception = error as? Exception ?: IllegalStateException("当前结果不能生成请求配方")
            showRecipePreparationError(recipeUserFacingError(exception))
            return
        }
        val recipe = draft.recipe
        if (recipe == null) {
            showRecipePreparationError(draft.error ?: "当前结果不能保存为请求配方")
            return
        }
        val cookies = runCatching {
            recipe.endpoints.associateWith { endpoint ->
                val requestUrl = draft.replayRequestsByEndpoint[endpoint]?.requestUrl ?: endpoint
                CookieManager.getInstance().getCookie(requestUrl).orEmpty()
            }
        }.getOrElse {
            showRecipePreparationError("读取本机网页登录状态失败，请返回仪表盘重新登录后再试")
            return
        }
        val replayHeaders = draft.replayHeadersByEndpoint
        val replayRequests = draft.replayRequestsByEndpoint
        recipeInProgress = true
        saveRecipeButton.text = "正在直接测试，请稍候…"
        resultRecipeStatus.text = "正在直接请求并二次核对 ${recipe.metrics.size} 个字段…"
        Toast.makeText(this, "已开始直接测试", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val replay = recipeClient.fetch(recipe, cookies, replayHeaders, replayRequests)
                val savedRecipe = recipe.copy(lastSuccessAt = System.currentTimeMillis())
                if (!recipeRepository.save(savedRecipe, cookies, replayHeaders, replayRequests)) {
                    throw IllegalStateException("本机加密保存失败，没有写入明文凭据")
                }
                runOnUiThread {
                    if (!isDestroyed) {
                        recipeInProgress = false
                        resultSummary.text = "直连测试通过并已加密保存 ${replay.metricCount} 个字段"
                        resultBody.text = replay.displayBody
                        resultRecipeStatus.text = "以后打开实验室可以直接刷新，不再调用 AI。"
                        saveRecipeButton.text = "已保存本次请求配方"
                        saveRecipeButton.isEnabled = false
                        updateSavedRecipePanel()
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isDestroyed) {
                        recipeInProgress = false
                        saveRecipeButton.isEnabled = true
                        saveRecipeButton.text = "重新直接测试并加密保存"
                        val message = recipeUserFacingError(error)
                        resultRecipeStatus.text = message
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }.start()
    }

    private fun showRecipePreparationError(message: String) {
        recipeInProgress = false
        saveRecipeButton.isEnabled = true
        saveRecipeButton.text = "重新直接测试并加密保存"
        resultRecipeStatus.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun refreshSavedRecipe() {
        if (recipeInProgress) return
        val saved = recipeRepository.load()
        if (saved == null) {
            updateSavedRecipePanel()
            setupError.text = "保存的配方或登录状态已经损坏，请删除后重新识别"
            setupError.visibility = View.VISIBLE
            return
        }
        recipeInProgress = true
        refreshSavedRecipeButton.isEnabled = false
        setupPanel.visibility = View.GONE
        browserPanel.visibility = View.GONE
        resultPanel.visibility = View.VISIBLE
        saveRecipeButton.visibility = View.GONE
        resultRecipeStatus.text = "正在按已保存配方直接请求，不会调用 AI…"
        resultSummary.text = "正在刷新 ${saved.recipe.pagePurpose}"
        resultBody.text = ""
        Thread {
            try {
                val replay = recipeClient.fetch(
                    saved.recipe,
                    saved.cookiesByEndpoint,
                    saved.replayHeadersByEndpoint,
                    saved.replayRequestsByEndpoint
                )
                val updated = saved.recipe.copy(lastSuccessAt = System.currentTimeMillis())
                if (!recipeRepository.save(
                        updated,
                        saved.cookiesByEndpoint,
                        saved.replayHeadersByEndpoint,
                        saved.replayRequestsByEndpoint
                    )
                ) {
                    throw IllegalStateException("刷新成功，但本机状态更新时间保存失败")
                }
                runOnUiThread {
                    if (!isDestroyed) {
                        recipeInProgress = false
                        refreshSavedRecipeButton.isEnabled = true
                        resultSummary.text = "直接刷新成功，本次取得 ${replay.metricCount} 个真实字段"
                        resultBody.text = replay.displayBody
                        resultRecipeStatus.text = "本次没有调用 AI，也没有打开仪表盘网页。"
                        updateSavedRecipePanel()
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isDestroyed) {
                        recipeInProgress = false
                        refreshSavedRecipeButton.isEnabled = true
                        resultSummary.text = "直接刷新没有完成"
                        resultBody.text = ""
                        resultRecipeStatus.text = recipeUserFacingError(error)
                    }
                }
            }
        }.start()
    }

    private fun deleteSavedRecipe() {
        if (recipeInProgress) return
        if (recipeRepository.clear()) {
            Toast.makeText(this, "已删除请求配方和加密登录状态", Toast.LENGTH_SHORT).show()
            refreshWidget(applicationContext)
        } else {
            Toast.makeText(this, "删除失败，请稍后重试", Toast.LENGTH_SHORT).show()
        }
        updateSavedRecipePanel()
    }

    private fun updateSavedRecipePanel() {
        if (!::savedRecipePanel.isInitialized) return
        val saved = recipeRepository.load()
        savedRecipePanel.visibility = if (saved == null) View.GONE else View.VISIBLE
        if (saved != null) {
            bindableConfigs = loadBindableConfigs()
            val labels = bindableConfigs.map { config ->
                buildString {
                    append(config.model)
                    config.name.trim().takeIf { it.isNotBlank() && it != config.model }?.let {
                        append(" · ").append(it)
                    }
                }
            }
            recipeInstanceSpinner.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                labels
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            val boundInstanceId = saved.recipe.boundInstanceId
            val widgetEligible = saved.recipe.isWidgetReplayEligible()
            val selectedIndex = bindableConfigs.indexOfFirst { config ->
                InstanceKeyResolver.canonicalInstanceId(config.id) == boundInstanceId
            }
            if (selectedIndex >= 0) recipeInstanceSpinner.setSelection(selectedIndex)
            recipeInstanceSpinner.visibility = if (bindableConfigs.isEmpty() || !widgetEligible) View.GONE else View.VISIBLE
            bindRecipeButton.visibility = if (widgetEligible) View.VISIBLE else View.GONE
            bindRecipeButton.isEnabled = bindableConfigs.isNotEmpty() && widgetEligible
            unbindRecipeButton.visibility = if (boundInstanceId == null) View.GONE else View.VISIBLE
            val lastSuccess = saved.recipe.lastSuccessAt.takeIf { it > 0L }?.let {
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
            } ?: "尚未刷新"
            val bindingText = when {
                boundInstanceId == null -> "尚未接入 Widget"
                selectedIndex >= 0 -> "已接入 ${labels[selectedIndex]}"
                else -> "已接入实例 $boundInstanceId（当前卡片不可用）"
            }
            val configHint = when {
                !widgetEligible -> "\nP5-U1 查询/POST 配方已保存；当前阶段只允许在实验室直接刷新，暂不接入 Widget"
                bindableConfigs.isEmpty() -> "\n请先在主 App 完整配置并启用一张卡片"
                else -> ""
            }
            val methods = saved.recipe.effectiveRequestSpecs().joinToString("/") { it.method }.ifBlank { "GET" }
            savedRecipeSummary.text = "${saved.recipe.pagePurpose}\n${saved.recipe.metrics.size} 个字段 · $methods · 上次成功 $lastSuccess\n$bindingText$configHint"
        }
    }

    private fun loadBindableConfigs(): List<ApiAccountConfig> {
        val prefs = getSharedPreferences("api_config", MODE_PRIVATE)
        return ConfigRepository.loadAllConfigs(prefs).filter { config ->
            config.enabled &&
                config.apiBase.isNotBlank() &&
                config.apiKey.isNotBlank() &&
                config.model.isNotBlank()
        }
    }

    private fun bindSavedRecipeToWidget() {
        if (recipeInProgress) return
        val saved = recipeRepository.load()
        if (saved == null || !saved.recipe.isWidgetReplayEligible()) {
            Toast.makeText(this, "P5-U1 查询/POST 配方暂不接入 Widget，请先在实验室直接刷新", Toast.LENGTH_LONG).show()
            return
        }
        val config = bindableConfigs.getOrNull(recipeInstanceSpinner.selectedItemPosition)
        if (config == null) {
            Toast.makeText(this, "请先选择一张已配置卡片", Toast.LENGTH_SHORT).show()
            return
        }
        if (recipeRepository.bindToInstance(config.id)) {
            refreshWidget(applicationContext)
            Toast.makeText(this, "已接入 ${config.model}", Toast.LENGTH_SHORT).show()
            updateSavedRecipePanel()
        } else {
            Toast.makeText(this, "接入失败，请重新测试并保存配方", Toast.LENGTH_SHORT).show()
        }
    }

    private fun unbindSavedRecipeFromWidget() {
        if (recipeInProgress) return
        if (recipeRepository.unbind()) {
            refreshWidget(applicationContext)
            Toast.makeText(this, "已解除 Widget 接入", Toast.LENGTH_SHORT).show()
            updateSavedRecipePanel()
        } else {
            Toast.makeText(this, "解除失败，请稍后重试", Toast.LENGTH_SHORT).show()
        }
    }

    private fun recipeUserFacingError(error: Exception): String = when (error) {
        is SocketTimeoutException -> "仪表盘接口响应超时，请稍后再试"
        is SSLException -> "仪表盘 HTTPS 连接失败"
        is IllegalArgumentException, is IllegalStateException -> error.message ?: "直接请求失败"
        else -> "直接请求失败，请检查网络或重新登录"
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
        capturedReplayHeaders.clear()
        removeDocumentStartCapture()
        handler.removeCallbacks(injectionRunnable)
        handler.removeCallbacks(captureStatusRunnable)
        confirmButton.isEnabled = true
        analyzeButton.isEnabled = false
        analyzeButton.text = "调用一次 AI 识别"
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
        modelDetectionGeneration++
        modelDetectionInProgress = false
        handler.removeCallbacksAndMessages(null)
        aiAnalyzer.cancel()
        recipeClient.cancel()
        removeDocumentStartCapture()
        capturedReplayHeaders.clear()
        currentCapture = null
        currentAnalysis = null
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
