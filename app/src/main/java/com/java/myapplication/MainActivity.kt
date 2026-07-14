package com.java.myapplication

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.java.myapplication.adapter.AdapterFactory
import com.java.myapplication.adapter.auth.BackgroundAuthConfig
import com.java.myapplication.adapter.capability.DataSourceType
import com.java.myapplication.adapter.auth.BackgroundAuthRepository
import com.java.myapplication.adapter.auth.BackgroundAuthType
import com.java.myapplication.config.ApiAccountConfig
import com.java.myapplication.config.ConfigRepository
import com.java.myapplication.ui.theme.MyApplicationTheme
import com.java.myapplication.webauth.WebAuthProfileRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ConfigScreen(
                        modifier = Modifier.padding(innerPadding),
                        context = this
                    )
                }
            }
        }
    }
}

fun parseJson(json: String): Map<String, String> {
    val result = mutableMapOf<String, String>()
    val clean = json.trim().removePrefix("{").removeSuffix("}")
    var i = 0
    while (i < clean.length) {
        val keyStart = clean.indexOf('"', i)
        if (keyStart == -1) break
        val keyEnd = clean.indexOf('"', keyStart + 1)
        if (keyEnd == -1) break
        val key = clean.substring(keyStart + 1, keyEnd)
        val colon = clean.indexOf(':', keyEnd + 1)
        if (colon == -1) break
        var valueStart = colon + 1
        while (valueStart < clean.length && clean[valueStart] == ' ') valueStart++
        val value: String
        if (clean[valueStart] == '"') {
            val valueEnd = clean.indexOf('"', valueStart + 1)
            value = clean.substring(valueStart + 1, valueEnd)
            i = valueEnd + 1
        } else {
            val comma = clean.indexOf(',', valueStart)
            val end = if (comma == -1) clean.length else comma
            value = clean.substring(valueStart, end).trim()
            i = if (comma == -1) clean.length else comma + 1
        }
        result[key] = value
    }
    return result
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
    data class Error(val title: String, val reason: String, val suggestion: String, val detail: String? = null) : TestResult()
}

suspend fun fetchModels(apiBase: String, apiKey: String): TestResult {
    return withContext(Dispatchers.IO) {
        try {
            val normalizedBase = apiBase.trim().trimEnd('/')
            val requestUrl = if (normalizedBase.endsWith("/v1", ignoreCase = true)) {
                "$normalizedBase/models"
            } else {
                "$normalizedBase/v1/models"
            }

            val url = URL(requestUrl)
            val conn = url.openConnection() as HttpURLConnection
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
                } catch (_: Exception) { "" }
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
            val dataKeyword = "\"data\":"
            val dataStart = response.indexOf(dataKeyword)
            if (dataStart == -1) {
                return@withContext TestResult.Error(
                    title = "📦 没找到模型",
                    reason = "这个中转站没有开放模型列表接口。",
                    suggestion = "可以联系中转站管理员，或者稍后再试。",
                    detail = "请求 URL: $requestUrl\n返回内容: ${response.take(500)}"
                )
            }

            val arrayStart = response.indexOf('[', dataStart)
            val arrayEnd = response.lastIndexOf(']')
            if (arrayStart == -1 || arrayEnd == -1) {
                return@withContext TestResult.Error(
                    title = "📦 没找到模型",
                    reason = "这个中转站没有开放模型列表接口。",
                    suggestion = "可以联系中转站管理员，或者稍后再试。",
                    detail = "请求 URL: $requestUrl\n返回内容: ${response.take(500)}"
                )
            }

            val arrayContent = response.substring(arrayStart + 1, arrayEnd)
            var idx = 0
            while (idx < arrayContent.length) {
                val idIdx = arrayContent.indexOf("\"id\":", idx)
                if (idIdx == -1) break
                val quoteStart = arrayContent.indexOf('"', idIdx + 5)
                if (quoteStart == -1) break
                val quoteEnd = arrayContent.indexOf('"', quoteStart + 1)
                if (quoteEnd == -1) break
                val modelId = arrayContent.substring(quoteStart + 1, quoteEnd)
                if (modelId.isNotEmpty()) {
                    models.add(modelId)
                }
                idx = quoteEnd + 1
            }

            if (models.isEmpty()) {
                return@withContext TestResult.Error(
                    title = "📦 没找到模型",
                    reason = "这个中转站没有开放模型列表接口。",
                    suggestion = "可以联系中转站管理员，或者稍后再试。",
                    detail = "请求 URL: $requestUrl\n返回内容: ${response.take(500)}"
                )
            }

            return@withContext TestResult.Success(models)
        } catch (e: java.net.SocketTimeoutException) {
            return@withContext TestResult.Error(
                title = "🐢 网络有点慢",
                reason = "连接超时了。",
                suggestion = "换个网络，或者等几秒钟再试一次。",
                detail = "异常: ${e.javaClass.simpleName}\n${e.message}"
            )
        } catch (e: Exception) {
            return@withContext TestResult.Error(
                title = "🌐 没有连接成功",
                reason = "API Base 地址填写有误，或者服务器暂时无法访问。",
                suggestion = "检查一下 API Base 地址，确认没有多写或少写字符，然后再试一次。",
                detail = "异常: ${e.javaClass.simpleName}\n${e.message}"
            )
        }
    }
}

// 探测结果数据类
data class ProbeResultData(
    val endpoint: String,
    val statusCode: Int,
    val body: String,
    val fields: List<String>
)

suspend fun probeEndpoints(apiBase: String, apiKey: String): List<ProbeResultData> {
    return withContext(Dispatchers.IO) {
        val results = mutableListOf<ProbeResultData>()
        val normalizedBase = apiBase.trim().trimEnd('/')
        val endpoints = listOf(
            "/v1/models",
            "/v1/dashboard/billing/subscription",
            "/v1/dashboard/billing/usage",
            "/v1/user/info",
            "/v1/user/balance"
        )

        for (endpoint in endpoints) {
            try {
                val url = URL("$normalizedBase$endpoint")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val responseCode = conn.responseCode
                val response = if (responseCode == 200) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    val errorText = try { conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "" } catch (_: Exception) { "" }
                    conn.disconnect()
                    results.add(ProbeResultData(endpoint, responseCode, errorText, emptyList()))
                    continue
                }
                conn.disconnect()

                val fields = extractAllKeys(response)
                results.add(ProbeResultData(endpoint, responseCode, response, fields))
            } catch (e: Exception) {
                results.add(ProbeResultData(endpoint, -1, "异常: ${e.javaClass.simpleName}", emptyList()))
            }
        }
        results
    }
}

fun extractAllKeys(json: String): List<String> {
    val keys = mutableListOf<String>()
    var i = 0
    while (i < json.length) {
        val quoteIdx = json.indexOf('"', i)
        if (quoteIdx == -1) break
        val endQuote = json.indexOf('"', quoteIdx + 1)
        if (endQuote == -1) break
        val key = json.substring(quoteIdx + 1, endQuote)
        val afterQuote = endQuote + 1
        if (afterQuote < json.length && json[afterQuote] == ':') {
            keys.add(key)
        }
        i = endQuote + 1
    }
    return keys
}

@Composable
fun ConfigScreen(modifier: Modifier = Modifier, context: Context) {
    val prefs = remember { context.getSharedPreferences("api_config", Context.MODE_PRIVATE) }
    val platforms = listOf("Kimi", "MiMo", "DeepSeek", "OpenAI")
    val scope = rememberCoroutineScope()

    var configs by remember {
        mutableStateOf(platforms.map { platform ->
            val json = prefs.getString(platform, null)
            if (json != null) {
                val obj = parseJson(json)
                PlatformConfig(
                    name = obj["name"] ?: platform,
                    apiBase = obj["apiBase"] ?: "",
                    apiKey = obj["apiKey"] ?: "",
                    model = obj["model"] ?: "",
                    enabled = (obj["enabled"] ?: "true") == "true"
                )
            } else {
                PlatformConfig(platform, "", "", "", true)
            }
        })
    }

    val scrollState = rememberScrollState()

    var showModelDialog by remember { mutableStateOf(false) }
    var modelList by remember { mutableStateOf(listOf<String>()) }
    var selectedPlatformIndex by remember { mutableStateOf(-1) }
    var testingIndex by remember { mutableStateOf(-1) }

    var showErrorDialog by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf("") }
    var errorReason by remember { mutableStateOf("") }
    var errorSuggestion by remember { mutableStateOf("") }
    var errorDetail by remember { mutableStateOf<String?>(null) }
    var showDetail by remember { mutableStateOf(false) }

    var showSuccessDialog by remember { mutableStateOf(false) }
    var successModel by remember { mutableStateOf("") }
    var successPlatform by remember { mutableStateOf("") }

    // 探测结果
    var showProbeDialog by remember { mutableStateOf(false) }
    var probeResults by remember { mutableStateOf(listOf<ProbeResultData>()) }
    var probePlatform by remember { mutableStateOf("") }

    // 后台授权配置（每个平台独立）
    var backgroundAuths by remember {
        mutableStateOf(platforms.map { platform ->
            BackgroundAuthRepository.load(prefs, platform)
        })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "API Balance Widget",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Build: 2026-07-12-002 | Stage: 6-3A",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Configure API platforms",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        platforms.forEachIndexed { index, platform ->
            val config = configs[index]
            var name by remember { mutableStateOf(config.name) }
            var apiBase by remember { mutableStateOf(config.apiBase) }
            var apiKey by remember { mutableStateOf(config.apiKey) }
            var model by remember { mutableStateOf(config.model) }
            var enabled by remember { mutableStateOf(config.enabled) }

            var connectionStatus by remember { mutableStateOf<ConnectionStatus?>(null) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            // 主标题：模型名称（或"未选择模型"）
                            val currentModel = configs[index].model
                            Text(
                                text = if (currentModel.isNotBlank()) currentModel else "未选择模型",
                                style = MaterialTheme.typography.titleMedium
                            )
                            // 副标题：用户备注（name字段）
                            if (name.isNotBlank() && name != platform) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            Switch(
                                checked = enabled,
                                onCheckedChange = {
                                    enabled = it
                                    val newConfig = configs[index].copy(enabled = it)
                                    configs = configs.toMutableList().apply {
                                        this[index] = newConfig
                                    }
                                    savePlatformConfig(prefs, platform, newConfig)
                                }
                            )
                            Text(
                                text = "启用此配置",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            val newConfig = configs[index].copy(name = it)
                            configs = configs.toMutableList().apply {
                                this[index] = newConfig
                            }
                            savePlatformConfig(prefs, platform, newConfig)
                        },
                        label = { Text("中转站名称（备注，可选）") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = apiBase,
                        onValueChange = {
                            apiBase = it
                            val newConfig = configs[index].copy(apiBase = it)
                            configs = configs.toMutableList().apply {
                                this[index] = newConfig
                            }
                            savePlatformConfig(prefs, platform, newConfig)
                        },
                        label = { Text("API Base URL *") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            val newConfig = configs[index].copy(apiKey = it)
                            configs = configs.toMutableList().apply {
                                this[index] = newConfig
                            }
                            savePlatformConfig(prefs, platform, newConfig)
                        },
                        label = { Text("API Key *") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )

                    // 显示当前 Key 后 4 位（调试用）
                    if (apiKey.length >= 4) {
                        Text(
                            text = "当前 Key：****${apiKey.takeLast(4)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 2.dp, start = 4.dp)
                        )
                    }

                    OutlinedTextField(
                        value = configs[index].model,
                        onValueChange = {
                            val newConfig = configs[index].copy(model = it)
                            configs = configs.toMutableList().apply {
                                this[index] = newConfig
                            }
                            savePlatformConfig(prefs, platform, newConfig)
                        },
                        label = { Text("模型名称（自动获取）") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true,
                        readOnly = true
                    )

                    if (connectionStatus != null) {
                        when (val status = connectionStatus!!) {
                            is ConnectionStatus.Success -> {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "🎉 连接成功！",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "已找到可用模型：${status.model}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                        Text(
                                            text = "已自动保存，Widget 会按照这个配置工作。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                            is ConnectionStatus.Error -> {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = status.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = "可能原因：${status.reason}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                        Text(
                                            text = "建议：${status.suggestion}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 测试连接按钮
                    Button(
                        onClick = {
                            if (apiBase.isBlank() || apiKey.isBlank()) {
                                connectionStatus = ConnectionStatus.Error(
                                    title = "🌐 没有连接成功",
                                    reason = "API Base URL 或 API Key 没有填写。",
                                    suggestion = "请确认 API Base URL 和 API Key 都已填写完整，然后再试一次。"
                                )
                                return@Button
                            }
                            // 测试连接前自动保存当前配置
                            savePlatformConfig(prefs, platform, configs[index])
                            testingIndex = index
                            connectionStatus = null
                            scope.launch {
                                val result = fetchModels(apiBase, apiKey)
                                testingIndex = -1
                                when (result) {
                                    is TestResult.Success -> {
                                        if (result.models.size == 1) {
                                            val newConfig = configs[index].copy(model = result.models[0])
                                            configs = configs.toMutableList().apply {
                                                this[index] = newConfig
                                            }
                                            model = result.models[0]
                                            savePlatformConfig(prefs, platform, newConfig)
                                            connectionStatus = ConnectionStatus.Success(result.models[0])
                                            // 测试连接成功后自动刷新 Widget
                                            refreshWidget(context)
                                        } else {
                                            modelList = result.models
                                            selectedPlatformIndex = index
                                            showModelDialog = true
                                        }
                                    }
                                    is TestResult.Error -> {
                                        connectionStatus = ConnectionStatus.Error(
                                            title = result.title,
                                            reason = result.reason,
                                            suggestion = result.suggestion
                                        )
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        enabled = testingIndex != index
                    ) {
                        Text(if (testingIndex == index) "测试中..." else "测试连接")
                    }

                    // 高级设置（后台授权）
                    var showAdvanced by remember { mutableStateOf(false) }
                    val currentAuth = backgroundAuths[index]

                    TextButton(
                        onClick = { showAdvanced = !showAdvanced },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Text(if (showAdvanced) "▲ 收起高级设置" else "▼ 高级设置")
                    }

                    if (showAdvanced) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                val capabilityAdapter = AdapterFactory.getAdapter(
                                    platform,
                                    configs[index].apiBase
                                )
                                val capabilityProfile = capabilityAdapter?.capabilityProfile

                                if (capabilityProfile != null) {
                                    val sourceText = capabilityProfile.sources
                                        .joinToString(" + ") { it.displayName }
                                    val authText = when (capabilityProfile.backgroundAuthType) {
                                        BackgroundAuthType.NONE -> "无需网页授权"
                                        BackgroundAuthType.COOKIE -> "网页 Cookie"
                                        BackgroundAuthType.BEARER_TOKEN -> "网页 Bearer Token"
                                    }
                                    val capabilityText = capabilityProfile.capabilities
                                        .joinToString("、") { it.displayName }
                                    val billingText = if (DataSourceType.BILLING in capabilityProfile.sources) {
                                        "已接入"
                                    } else {
                                        "当前未接入"
                                    }

                                    Text(
                                        text = "连接与数据能力",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "模型连接：${if (capabilityProfile.modelApiKeyRequired) "API Key" else "平台自定义"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                    Text(
                                        text = "数据来源：$sourceText",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    Text(
                                        text = "账户授权：$authText",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    Text(
                                        text = "Billing：$billingText",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    Text(
                                        text = "可用数据：$capabilityText",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(bottom = 10.dp))
                                }

                                Text(
                                    text = "后台授权",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                // 授权类型选择
                                var selectedAuthType by remember { mutableStateOf(currentAuth.authType) }

                                Column {
                                    BackgroundAuthType.entries.forEach { type ->
                                        Row(
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                        ) {
                                            RadioButton(
                                                selected = selectedAuthType == type,
                                                onClick = {
                                                    selectedAuthType = type
                                                }
                                            )
                                            Text(
                                                text = when (type) {
                                                    BackgroundAuthType.NONE -> "无"
                                                    BackgroundAuthType.BEARER_TOKEN -> "Bearer Token"
                                                    BackgroundAuthType.COOKIE -> "Cookie"
                                                },
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }

                                // 授权值输入框
                                if (selectedAuthType != BackgroundAuthType.NONE) {
                                    var authValueVisible by remember { mutableStateOf(false) }

                                    OutlinedTextField(
                                        value = backgroundAuths[index].authValue,
                                        onValueChange = { newValue ->
                                            val newAuth = backgroundAuths[index].copy(
                                                authValue = newValue,
                                                authType = selectedAuthType,
                                                enabled = true,
                                                updatedAt = System.currentTimeMillis()
                                            )
                                            backgroundAuths = backgroundAuths.toMutableList().apply {
                                                this[index] = newAuth
                                            }
                                            BackgroundAuthRepository.save(prefs, platform, newAuth)
                                        },
                                        label = { Text(when (selectedAuthType) {
                                            BackgroundAuthType.BEARER_TOKEN -> "请输入 auth_token"
                                            BackgroundAuthType.COOKIE -> "请输入完整 Cookie"
                                            else -> ""
                                        })},
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        singleLine = false,
                                        maxLines = 3,
                                        visualTransformation = if (authValueVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            TextButton(onClick = { authValueVisible = !authValueVisible }) {
                                                Text(if (authValueVisible) "隐藏" else "显示")
                                            }
                                        }
                                    )
                                }

                                // 保存/清除按钮
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                     Button(
                                         onClick = {
                                             val newAuth = BackgroundAuthConfig(
                                                 authType = selectedAuthType,
                                                 authValue = backgroundAuths[index].authValue,
                                                 enabled = selectedAuthType != BackgroundAuthType.NONE,
                                                 updatedAt = System.currentTimeMillis()
                                             )
                                             backgroundAuths = backgroundAuths.toMutableList().apply {
                                                 this[index] = newAuth
                                             }
                                             BackgroundAuthRepository.save(prefs, platform, newAuth)
                                             android.widget.Toast.makeText(
                                                 context,
                                                 "$platform 授权已保存",
                                                 android.widget.Toast.LENGTH_SHORT
                                             ).show()
                                         },
                                         modifier = Modifier.weight(1f)
                                     ) {
                                         Text("保存授权")
                                     }

                                    Button(
                                        onClick = {
                                            val emptyAuth = BackgroundAuthConfig()
                                            backgroundAuths = backgroundAuths.toMutableList().apply {
                                                this[index] = emptyAuth
                                            }
                                            BackgroundAuthRepository.clear(prefs, platform)
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors()
                                    ) {
                                        Text("清除授权")
                                    }
                                }

                                // 网页登录入口由平台能力描述决定，不再写死 platform == "xxx"。
                                val webAuthProfile = WebAuthProfileRegistry.findFor(platform, configs[index].apiBase)
                                if (webAuthProfile != null) {
                                    Button(
                                        onClick = {
                                            context.startActivity(
                                                WebAuthActivity.createIntent(
                                                    context = context,
                                                    profileId = webAuthProfile.profileId
                                                )
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text("🌐 连接账户")
                                    }
                                }
                            }
                        }
                    }

                    // 探测接口按钮（仅 Kimi 显示）
                    if (platform == "Kimi") {
                        Button(
                            onClick = {
                                if (apiBase.isBlank() || apiKey.isBlank()) {
                                    connectionStatus = ConnectionStatus.Error(
                                        title = "🌐 没有连接成功",
                                        reason = "API Base URL 或 API Key 没有填写。",
                                        suggestion = "请确认 API Base URL 和 API Key 都已填写完整，然后再试一次。"
                                    )
                                    return@Button
                                }
                                testingIndex = index
                                scope.launch {
                                    probeResults = probeEndpoints(apiBase, apiKey)
                                    probePlatform = platform
                                    testingIndex = -1
                                    showProbeDialog = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            enabled = testingIndex != index,
                            colors = ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text("🔍 探测接口")
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                val editor = prefs.edit()
                configs.forEachIndexed { index, config ->
                    val json = StringBuilder()
                        .append("{\"name\":\"").append(config.name).append("\",")
                        .append("\"apiBase\":\"").append(config.apiBase).append("\",")
                        .append("\"apiKey\":\"").append(config.apiKey).append("\",")
                        .append("\"model\":\"").append(config.model).append("\",")
                        .append("\"enabled\":").append(config.enabled).append("}")
                        .toString()
                    editor.putString(platforms[index], json)
                }
                editor.apply()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 32.dp)
        ) {
            Text("保存配置")
        }
    }

    if (showModelDialog && modelList.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text("🎉 连接成功！") },
            text = {
                Column {
                    Text(
                        text = "已经找到可用模型啦～请选择一个你想监控的模型吧。",
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    modelList.forEach { modelId ->
                        TextButton(
                            onClick = {
                                val platform = platforms[selectedPlatformIndex]
                                val newConfig = configs[selectedPlatformIndex].copy(model = modelId)
                                configs = configs.toMutableList().apply {
                                    this[selectedPlatformIndex] = newConfig
                                }
                                savePlatformConfig(prefs, platform, newConfig)
                                showModelDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(modelId)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text(errorTitle) },
            text = {
                Column {
                    Text(
                        text = "可能原因：",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = errorReason,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = "建议：",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = errorSuggestion,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    if (errorDetail != null) {
                        TextButton(
                            onClick = { showDetail = !showDetail },
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(if (showDetail) "隐藏详情" else "查看详情")
                        }
                        if (showDetail) {
                            Text(
                                text = errorDetail!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("知道了")
                }
            }
        )
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text("🎉 连接成功啦") },
            text = {
                Column {
                    Text(
                        text = "已经找到可用模型啦～",
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = successModel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "以后就可以用它来监控啦。",
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSuccessDialog = false }) {
                    Text("好的")
                }
            }
        )
    }

    // 探测结果弹窗
    if (showProbeDialog) {
        AlertDialog(
            onDismissRequest = { showProbeDialog = false },
            title = { Text("🔍 接口探测结果") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "平台: $probePlatform",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    probeResults.forEach { result ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "接口: ${result.endpoint}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "状态码: ${if (result.statusCode == 200) "✅ 200" else if (result.statusCode == -1) "❌ 异常" else "❌ ${result.statusCode}"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                if (result.fields.isNotEmpty()) {
                                    Text(
                                        text = "字段: ${result.fields.joinToString(", ")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                Text(
                                    text = "返回 Body (前1000字符):",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                Text(
                                    text = result.body.take(1000),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProbeDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

sealed class ConnectionStatus {
    data class Success(val model: String) : ConnectionStatus()
    data class Error(val title: String, val reason: String, val suggestion: String) : ConnectionStatus()
}
/**
 * 保存单个平台的配置到 SharedPreferences
 */
fun savePlatformConfig(prefs: android.content.SharedPreferences, platform: String, config: PlatformConfig) {
    val editor = prefs.edit()
    val json = StringBuilder()
        .append("{\"name\":\"").append(config.name).append("\",")
        .append("\"apiBase\":\"").append(config.apiBase).append("\",")
        .append("\"apiKey\":\"").append(config.apiKey).append("\",")
        .append("\"model\":\"").append(config.model).append("\",")
        .append("\"enabled\":").append(config.enabled).append("}")
        .toString()
    editor.putString(platform, json)
    editor.apply()
}

/**
 * 刷新 Widget（触发 onUpdate）
 */
fun refreshWidget(context: Context) {
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
