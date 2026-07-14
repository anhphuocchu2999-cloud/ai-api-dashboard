package com.java.myapplication

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.java.myapplication.adapter.AdapterFactory
import com.java.myapplication.adapter.auth.BackgroundAuthConfig
import com.java.myapplication.adapter.auth.BackgroundAuthRepository
import com.java.myapplication.adapter.auth.BackgroundAuthType
import com.java.myapplication.adapter.capability.DataSourceType
import com.java.myapplication.config.ApiAccountConfig
import com.java.myapplication.config.ConfigRepository
import com.java.myapplication.ui.theme.MyApplicationTheme
import com.java.myapplication.webauth.WebAuthProfileRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    private var authRefreshToken by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ConfigScreen(
                        modifier = Modifier.padding(innerPadding),
                        context = this,
                        authRefreshToken = authRefreshToken
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        authRefreshToken++
    }
}

data class PlatformConfig(
    val name: String,
    val apiBase: String,
    val apiKey: String,
    val model: String,
    val enabled: Boolean
)

sealed class TestResult {
    data class Success(val models: List<String>) : TestResult()
    data class Error(
        val title: String,
        val reason: String,
        val suggestion: String,
        val detail: String? = null
    ) : TestResult()
}

sealed class ConnectionStatus {
    data class Success(val model: String) : ConnectionStatus()
    data class Error(val title: String, val reason: String, val suggestion: String) : ConnectionStatus()
}

data class ProbeResultData(
    val endpoint: String,
    val statusCode: Int,
    val body: String,
    val fields: List<String>
)

suspend fun fetchModels(apiBase: String, apiKey: String): TestResult {
    return withContext(Dispatchers.IO) {
        try {
            val normalizedBase = apiBase.trim().trimEnd('/')
            val requestUrl = if (normalizedBase.endsWith("/v1", ignoreCase = true)) {
                "$normalizedBase/models"
            } else {
                "$normalizedBase/v1/models"
            }

            val conn = URL(requestUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doInput = true

            try {
                conn.connect()
            } catch (e: Exception) {
                return@withContext TestResult.Error(
                    title = "🌐 没有连接成功",
                    reason = "API Base 地址填写有误，或者服务器暂时无法访问。",
                    suggestion = "检查一下 API Base 地址，确认没有多写或少写字符，然后再试一次。",
                    detail = "请求 URL: $requestUrl\n异常: ${e.javaClass.simpleName}\n${e.message}"
                )
            }

            val responseCode = conn.responseCode
            val response = if (responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorText = try {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (_: Exception) {
                    ""
                }
                conn.disconnect()
                return@withContext TestResult.Error(
                    title = "🔑 API Key 好像不对",
                    reason = "当前 API Key 无效，或者已经失效。",
                    suggestion = "重新复制一遍 API Key，再点击「测试连接」试试看。",
                    detail = "请求 URL: $requestUrl\nHTTP 状态码: $responseCode\n返回内容: $errorText"
                )
            }
            conn.disconnect()

            val models = mutableListOf<String>()
            val dataStart = response.indexOf("\"data\":")
            if (dataStart == -1) {
                return@withContext TestResult.Error(
                    title = "📦 没找到模型",
                    reason = "这个服务没有开放兼容的模型列表接口。",
                    suggestion = "可以联系服务提供方，或者稍后再试。",
                    detail = "请求 URL: $requestUrl\n返回内容: ${response.take(500)}"
                )
            }

            val arrayStart = response.indexOf('[', dataStart)
            val arrayEnd = response.lastIndexOf(']')
            if (arrayStart == -1 || arrayEnd == -1 || arrayEnd <= arrayStart) {
                return@withContext TestResult.Error(
                    title = "📦 没找到模型",
                    reason = "模型列表返回格式暂时无法识别。",
                    suggestion = "请确认服务兼容 OpenAI 的 /v1/models 格式。",
                    detail = "请求 URL: $requestUrl\n返回内容: ${response.take(500)}"
                )
            }

            val arrayContent = response.substring(arrayStart + 1, arrayEnd)
            var index = 0
            while (index < arrayContent.length) {
                val idIndex = arrayContent.indexOf("\"id\":", index)
                if (idIndex == -1) break
                val quoteStart = arrayContent.indexOf('"', idIndex + 5)
                if (quoteStart == -1) break
                val quoteEnd = arrayContent.indexOf('"', quoteStart + 1)
                if (quoteEnd == -1) break
                val modelId = arrayContent.substring(quoteStart + 1, quoteEnd)
                if (modelId.isNotBlank()) models.add(modelId)
                index = quoteEnd + 1
            }

            if (models.isEmpty()) {
                TestResult.Error(
                    title = "📦 没找到模型",
                    reason = "接口没有返回可使用的模型名称。",
                    suggestion = "可以联系服务提供方，或者稍后再试。",
                    detail = "请求 URL: $requestUrl\n返回内容: ${response.take(500)}"
                )
            } else {
                TestResult.Success(models.distinct())
            }
        } catch (e: java.net.SocketTimeoutException) {
            TestResult.Error(
                title = "🐢 网络有点慢",
                reason = "连接超时了。",
                suggestion = "换个网络，或者等几秒钟再试一次。",
                detail = "异常: ${e.javaClass.simpleName}\n${e.message}"
            )
        } catch (e: Exception) {
            TestResult.Error(
                title = "🌐 没有连接成功",
                reason = "API Base 地址填写有误，或者服务器暂时无法访问。",
                suggestion = "检查一下 API Base 地址，确认没有多写或少写字符，然后再试一次。",
                detail = "异常: ${e.javaClass.simpleName}\n${e.message}"
            )
        }
    }
}

suspend fun probeEndpoints(apiBase: String, apiKey: String): List<ProbeResultData> {
    return withContext(Dispatchers.IO) {
        val results = mutableListOf<ProbeResultData>()
        val normalizedBase = apiBase.trim().trimEnd('/')
        val endpoints = listOf(
            "/v1/models",
            "/v1/dashboard/billing/subscription",
            "/v1/dashboard/billing/usage"
        )

        for (endpoint in endpoints) {
            try {
                val conn = URL("$normalizedBase$endpoint").openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val responseCode = conn.responseCode
                val response = if (responseCode == 200) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    try {
                        conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    } catch (_: Exception) {
                        ""
                    }
                }
                conn.disconnect()
                results.add(
                    ProbeResultData(
                        endpoint = endpoint,
                        statusCode = responseCode,
                        body = response,
                        fields = if (responseCode == 200) extractAllKeys(response) else emptyList()
                    )
                )
            } catch (e: Exception) {
                results.add(
                    ProbeResultData(
                        endpoint = endpoint,
                        statusCode = -1,
                        body = "异常: ${e.javaClass.simpleName}",
                        fields = emptyList()
                    )
                )
            }
        }
        results
    }
}

fun extractAllKeys(json: String): List<String> {
    val keys = mutableListOf<String>()
    var index = 0
    while (index < json.length) {
        val quoteIndex = json.indexOf('"', index)
        if (quoteIndex == -1) break
        val endQuote = json.indexOf('"', quoteIndex + 1)
        if (endQuote == -1) break
        val key = json.substring(quoteIndex + 1, endQuote)
        val afterQuote = endQuote + 1
        if (afterQuote < json.length && json[afterQuote] == ':') keys.add(key)
        index = endQuote + 1
    }
    return keys.distinct()
}

@Composable
fun ConfigScreen(
    modifier: Modifier = Modifier,
    context: Context,
    authRefreshToken: Int
) {
    val prefs = remember { context.getSharedPreferences("api_config", Context.MODE_PRIVATE) }
    val platforms = remember { listOf("Kimi", "MiMo", "DeepSeek", "OpenAI") }
    val scope = rememberCoroutineScope()

    var configs by remember {
        mutableStateOf(loadPlatformConfigs(prefs, platforms))
    }
    var backgroundAuths by remember {
        mutableStateOf(platforms.map { BackgroundAuthRepository.load(prefs, it) })
    }
    var connectionStatuses by remember {
        mutableStateOf(List<ConnectionStatus?>(platforms.size) { null })
    }
    var testingIndex by remember { mutableIntStateOf(-1) }

    var showModelDialog by remember { mutableStateOf(false) }
    var modelList by remember { mutableStateOf(emptyList<String>()) }
    var selectedPlatformIndex by remember { mutableIntStateOf(-1) }

    var showErrorDialog by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf("") }
    var errorReason by remember { mutableStateOf("") }
    var errorSuggestion by remember { mutableStateOf("") }
    var errorDetail by remember { mutableStateOf<String?>(null) }
    var showDetail by remember { mutableStateOf(false) }

    var showProbeDialog by remember { mutableStateOf(false) }
    var probeResults by remember { mutableStateOf(emptyList<ProbeResultData>()) }
    var probePlatform by remember { mutableStateOf("") }

    LaunchedEffect(authRefreshToken) {
        backgroundAuths = platforms.map { BackgroundAuthRepository.load(prefs, it) }
        configs = loadPlatformConfigs(prefs, platforms)
    }

    fun updateConfig(index: Int, newConfig: PlatformConfig) {
        configs = configs.toMutableList().apply { this[index] = newConfig }
        savePlatformConfig(prefs, platforms[index], newConfig)
    }

    fun updateAuth(index: Int, newAuth: BackgroundAuthConfig) {
        backgroundAuths = backgroundAuths.toMutableList().apply { this[index] = newAuth }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "AI API Dashboard",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Build: 2026-07-14-8B-001 | Stage: 8B",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = "每个实例统一展示 API、网页授权和 Billing。仅开放当前服务真实支持的能力。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
        )

        platforms.forEachIndexed { index, platform ->
            val config = configs[index]
            val capabilityAdapter = AdapterFactory.getAdapter(platform, config.apiBase)
            val capabilityProfile = capabilityAdapter?.capabilityProfile
            val webAuthProfile = WebAuthProfileRegistry.findFor(platform, config.apiBase)
            val expectedAuthType = capabilityProfile?.backgroundAuthType
                ?: webAuthProfile?.authType
                ?: BackgroundAuthType.NONE
            val webAuthSupported = webAuthProfile != null && expectedAuthType != BackgroundAuthType.NONE
            val billingSupported = capabilityProfile?.sources?.contains(DataSourceType.BILLING) == true
            val auth = backgroundAuths[index]
            val authConnected = webAuthSupported &&
                auth.enabled &&
                auth.authType == expectedAuthType &&
                auth.authValue.isNotBlank()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = config.model.ifBlank { "未选择模型" },
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = config.name.ifBlank { platform },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            capabilityProfile?.let { profile ->
                                val dataText = profile.capabilities
                                    .joinToString("、") { it.displayName }
                                Text(
                                    text = "可用数据：$dataText",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Switch(
                                checked = config.enabled,
                                onCheckedChange = { enabled ->
                                    updateConfig(index, config.copy(enabled = enabled))
                                }
                            )
                            Text(
                                text = if (config.enabled) "已启用" else "已停用",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ConnectionMethodSection(
                        title = "1. API",
                        status = apiStatusText(config, connectionStatuses[index], testingIndex == index),
                        supported = true,
                        description = "用于获取模型列表，以及当前服务已开放的余额、额度或用量接口。"
                    ) {
                        OutlinedTextField(
                            value = config.name,
                            onValueChange = { updateConfig(index, config.copy(name = it)) },
                            label = { Text("实例备注（可选）") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = config.apiBase,
                            onValueChange = { updateConfig(index, config.copy(apiBase = it)) },
                            label = { Text("API Base URL *") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = config.apiKey,
                            onValueChange = { updateConfig(index, config.copy(apiKey = it)) },
                            label = { Text("API Key *") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                        OutlinedTextField(
                            value = config.model,
                            onValueChange = { },
                            label = { Text("模型名称（测试连接后自动选择）") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            singleLine = true,
                            readOnly = true
                        )

                        connectionStatuses[index]?.let { status ->
                            when (status) {
                                is ConnectionStatus.Success -> StatusMessage(
                                    title = "🎉 API 已连接",
                                    body = "已选择模型：${status.model}",
                                    isError = false
                                )
                                is ConnectionStatus.Error -> StatusMessage(
                                    title = status.title,
                                    body = "${status.reason}\n${status.suggestion}",
                                    isError = true
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (config.apiBase.isBlank() || config.apiKey.isBlank()) {
                                    val status = ConnectionStatus.Error(
                                        title = "🌐 没有连接成功",
                                        reason = "API Base URL 或 API Key 没有填写。",
                                        suggestion = "请确认两项都填写完整，然后再试一次。"
                                    )
                                    connectionStatuses = connectionStatuses.toMutableList().apply {
                                        this[index] = status
                                    }
                                    return@Button
                                }

                                savePlatformConfig(prefs, platform, config)
                                testingIndex = index
                                connectionStatuses = connectionStatuses.toMutableList().apply {
                                    this[index] = null
                                }

                                scope.launch {
                                    when (val result = fetchModels(config.apiBase, config.apiKey)) {
                                        is TestResult.Success -> {
                                            testingIndex = -1
                                            if (result.models.size == 1) {
                                                val selectedModel = result.models.first()
                                                updateConfig(index, config.copy(model = selectedModel))
                                                connectionStatuses = connectionStatuses.toMutableList().apply {
                                                    this[index] = ConnectionStatus.Success(selectedModel)
                                                }
                                                refreshWidget(context)
                                            } else {
                                                modelList = result.models
                                                selectedPlatformIndex = index
                                                showModelDialog = true
                                            }
                                        }
                                        is TestResult.Error -> {
                                            testingIndex = -1
                                            val status = ConnectionStatus.Error(
                                                result.title,
                                                result.reason,
                                                result.suggestion
                                            )
                                            connectionStatuses = connectionStatuses.toMutableList().apply {
                                                this[index] = status
                                            }
                                            errorTitle = result.title
                                            errorReason = result.reason
                                            errorSuggestion = result.suggestion
                                            errorDetail = result.detail
                                            showDetail = false
                                            showErrorDialog = true
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            enabled = testingIndex != index
                        ) {
                            Text(if (testingIndex == index) "正在测试…" else "测试 API 连接")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    ConnectionMethodSection(
                        title = "2. 网页授权",
                        status = when {
                            !webAuthSupported -> "当前服务暂未接入"
                            authConnected -> "已连接 · ${authTypeLabel(expectedAuthType)}"
                            else -> "未连接 · ${authTypeLabel(expectedAuthType)}"
                        },
                        supported = webAuthSupported,
                        description = when {
                            !webAuthSupported -> "当前服务没有经过验证的网页登录方案，不开放无效的 Cookie 或 Token 选择。"
                            expectedAuthType == BackgroundAuthType.COOKIE -> "网页登录后保存 Cookie，用于读取 API Key 无法提供的账户数据。"
                            else -> "网页登录后保存 Bearer Token，用于读取账户余额、Profile 等后台数据。"
                        }
                    ) {
                        if (webAuthSupported) {
                            var authValueVisible by remember(platform) { mutableStateOf(false) }
                            OutlinedTextField(
                                value = auth.authValue,
                                onValueChange = { newValue ->
                                    updateAuth(
                                        index,
                                        auth.copy(
                                            authType = expectedAuthType,
                                            authValue = newValue,
                                            enabled = newValue.isNotBlank()
                                        )
                                    )
                                },
                                label = {
                                    Text(
                                        if (expectedAuthType == BackgroundAuthType.COOKIE) {
                                            "手动 Cookie（备用）"
                                        } else {
                                            "手动 Bearer Token（备用）"
                                        }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3,
                                visualTransformation = if (authValueVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    TextButton(onClick = { authValueVisible = !authValueVisible }) {
                                        Text(if (authValueVisible) "隐藏" else "显示")
                                    }
                                }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val value = backgroundAuths[index].authValue
                                        if (value.isBlank()) {
                                            Toast.makeText(context, "请先输入授权内容", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val newAuth = BackgroundAuthConfig(
                                                authType = expectedAuthType,
                                                authValue = value,
                                                enabled = true,
                                                updatedAt = System.currentTimeMillis()
                                            )
                                            updateAuth(index, newAuth)
                                            BackgroundAuthRepository.save(prefs, platform, newAuth)
                                            Toast.makeText(context, "授权已保存", Toast.LENGTH_SHORT).show()
                                            refreshWidget(context)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("保存授权")
                                }
                                OutlinedButton(
                                    onClick = {
                                        updateAuth(index, BackgroundAuthConfig())
                                        BackgroundAuthRepository.clear(prefs, platform)
                                        Toast.makeText(context, "授权已清除", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("清除")
                                }
                            }

                            webAuthProfile?.let { profile ->
                                Button(
                                    onClick = {
                                        context.startActivity(
                                            WebAuthActivity.createIntent(
                                                context = context,
                                                profileId = profile.profileId
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) {
                                    Text(if (authConnected) "🌐 重新连接账户" else "🌐 连接账户")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    ConnectionMethodSection(
                        title = "3. Billing",
                        status = if (billingSupported) "已接入" else "当前服务暂未接入",
                        supported = billingSupported,
                        description = if (billingSupported) {
                            "Billing 是数据来源，不是第三份密码。当前自动复用模型 API Key 读取 Subscription / Usage Billing。"
                        } else {
                            "当前服务没有已经验证并接入的 Billing 数据接口，不伪造额度、套餐或用量。"
                        }
                    ) {
                        if (billingSupported) {
                            Text(
                                text = "认证：自动复用 API Key",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "展示：Billing 额度 / Billing 用量（保持中性数值）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            OutlinedButton(
                                onClick = {
                                    if (config.apiBase.isBlank() || config.apiKey.isBlank()) {
                                        Toast.makeText(context, "请先配置 API Base 和 API Key", Toast.LENGTH_SHORT).show()
                                    } else {
                                        testingIndex = index
                                        scope.launch {
                                            probeResults = probeEndpoints(config.apiBase, config.apiKey)
                                            probePlatform = config.model.ifBlank { platform }
                                            testingIndex = -1
                                            showProbeDialog = true
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                enabled = testingIndex != index
                            ) {
                                Text("查看 Billing 接口状态")
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                configs.forEachIndexed { index, config ->
                    savePlatformConfig(prefs, platforms[index], config)
                }
                refreshWidget(context)
                Toast.makeText(context, "全部配置已保存", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 32.dp)
        ) {
            Text("保存全部配置并刷新 Widget")
        }
    }

    if (showModelDialog && modelList.isNotEmpty() && selectedPlatformIndex in platforms.indices) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text("🎉 API 已连接") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "找到多个模型，请选择当前实例需要监控的模型。",
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    modelList.forEach { modelId ->
                        TextButton(
                            onClick = {
                                val index = selectedPlatformIndex
                                val selectedConfig = configs[index].copy(model = modelId)
                                updateConfig(index, selectedConfig)
                                connectionStatuses = connectionStatuses.toMutableList().apply {
                                    this[index] = ConnectionStatus.Success(modelId)
                                }
                                showModelDialog = false
                                refreshWidget(context)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(modelId)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) { Text("取消") }
            }
        )
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text(errorTitle) },
            text = {
                Column {
                    Text("可能原因：", style = MaterialTheme.typography.titleSmall)
                    Text(errorReason, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                    Text("建议：", style = MaterialTheme.typography.titleSmall)
                    Text(errorSuggestion, modifier = Modifier.padding(top = 4.dp))
                    errorDetail?.let { detail ->
                        TextButton(onClick = { showDetail = !showDetail }) {
                            Text(if (showDetail) "隐藏详情" else "查看详情")
                        }
                        if (showDetail) {
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) { Text("知道了") }
            }
        )
    }

    if (showProbeDialog) {
        AlertDialog(
            onDismissRequest = { showProbeDialog = false },
            title = { Text("Billing 接口状态") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "实例：$probePlatform",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    probeResults.forEach { result ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(result.endpoint, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = when (result.statusCode) {
                                        200 -> "✅ HTTP 200"
                                        -1 -> "❌ 网络异常"
                                        else -> "❌ HTTP ${result.statusCode}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                if (result.fields.isNotEmpty()) {
                                    Text(
                                        text = "字段：${result.fields.joinToString("、")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProbeDialog = false }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun ConnectionMethodSection(
    title: String,
    status: String,
    supported: Boolean,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (supported) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
            if (supported) {
                content()
            } else {
                Text(
                    text = "该入口已锁定，待服务提供真实接口并完成验证后再开放。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusMessage(title: String, body: String, isError: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun apiStatusText(
    config: PlatformConfig,
    status: ConnectionStatus?,
    isTesting: Boolean
): String {
    return when {
        isTesting -> "正在测试…"
        status is ConnectionStatus.Success -> "已连接"
        status is ConnectionStatus.Error -> "连接失败"
        config.apiBase.isBlank() || config.apiKey.isBlank() -> "未配置"
        config.model.isNotBlank() -> "已连接"
        else -> "已配置 · 待测试"
    }
}

private fun authTypeLabel(type: BackgroundAuthType): String {
    return when (type) {
        BackgroundAuthType.NONE -> "无需网页授权"
        BackgroundAuthType.COOKIE -> "Cookie"
        BackgroundAuthType.BEARER_TOKEN -> "Bearer Token"
    }
}

private fun loadPlatformConfigs(
    prefs: android.content.SharedPreferences,
    platforms: List<String>
): List<PlatformConfig> {
    val repositoryConfigs = ConfigRepository.loadAllConfigs(prefs)
    return platforms.map { platform ->
        val config = repositoryConfigs.firstOrNull {
            it.id.equals(platform, ignoreCase = true)
        }
        if (config != null) {
            PlatformConfig(
                name = config.name,
                apiBase = config.apiBase,
                apiKey = config.apiKey,
                model = config.model,
                enabled = config.enabled
            )
        } else {
            PlatformConfig(platform, "", "", "", true)
        }
    }
}

fun savePlatformConfig(
    prefs: android.content.SharedPreferences,
    platform: String,
    config: PlatformConfig
) {
    val json = JSONObject()
        .put("name", config.name)
        .put("apiBase", config.apiBase)
        .put("apiKey", config.apiKey)
        .put("model", config.model)
        .put("enabled", config.enabled)
        .toString()

    prefs.edit().putString(platform, json).apply()

    ConfigRepository.saveConfig(
        prefs,
        ApiAccountConfig(
            id = platform,
            name = config.name,
            apiBase = config.apiBase,
            apiKey = config.apiKey,
            model = config.model,
            enabled = config.enabled
        )
    )
}

fun refreshWidget(context: Context) {
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
        // Widget 不存在或桌面暂不可用时，不影响配置页保存。
    }
}
