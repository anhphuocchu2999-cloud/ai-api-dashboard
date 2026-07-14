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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.java.myapplication.adapter.AdapterFactory
import com.java.myapplication.adapter.auth.BackgroundAuthConfig
import com.java.myapplication.adapter.auth.BackgroundAuthRepository
import com.java.myapplication.adapter.auth.BackgroundAuthType
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

data class ServiceOption(
    val id: String,
    val displayName: String,
    val defaultApiBase: String
)

private enum class ConnectionMode {
    API,
    WEB
}

private val serviceOptions = listOf(
    ServiceOption("newapi", "Kimi / NewAPI", "https://code.coolyeah.net"),
    ServiceOption("mimo", "MiMo", "https://platform.xiaomimimo.com"),
    ServiceOption("deepseek", "DeepSeek 官方", "https://api.deepseek.com/v1"),
    ServiceOption("aihuangniu", "爱黄牛", "https://sub2.aihuangniu.com")
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
                    reason = "API 地址填写有误，或者服务器暂时无法访问。",
                    suggestion = "检查 API 地址后再试一次。",
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
                    reason = "当前 API Key 无效、已失效，或者这个地址不支持模型列表。",
                    suggestion = "重新检查 API 地址和 API Key，再点击检测连接。",
                    detail = "请求 URL: $requestUrl\nHTTP 状态码: $responseCode\n返回内容: $errorText"
                )
            }
            conn.disconnect()

            val models = mutableListOf<String>()
            val dataStart = response.indexOf("\"data\":")
            if (dataStart == -1) {
                return@withContext TestResult.Error(
                    title = "📦 没找到模型",
                    reason = "这个服务没有返回兼容的模型列表。",
                    suggestion = "确认服务支持 OpenAI 兼容的 /v1/models 接口。",
                    detail = "请求 URL: $requestUrl\n返回内容: ${response.take(500)}"
                )
            }

            val arrayStart = response.indexOf('[', dataStart)
            val arrayEnd = response.lastIndexOf(']')
            if (arrayStart == -1 || arrayEnd == -1 || arrayEnd <= arrayStart) {
                return@withContext TestResult.Error(
                    title = "📦 没找到模型",
                    reason = "模型列表的返回格式暂时无法识别。",
                    suggestion = "确认服务兼容 OpenAI 的 /v1/models 格式。",
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
                    suggestion = "稍后再试，或向服务提供方确认模型列表接口。",
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
                reason = "API 地址填写有误，或者服务器暂时无法访问。",
                suggestion = "检查 API 地址后再试一次。",
                detail = "异常: ${e.javaClass.simpleName}\n${e.message}"
            )
        }
    }
}

@Composable
fun ConfigScreen(
    modifier: Modifier = Modifier,
    context: Context,
    authRefreshToken: Int
) {
    val prefs = remember { context.getSharedPreferences("api_config", Context.MODE_PRIVATE) }
    val slots = remember { listOf("Kimi", "MiMo", "DeepSeek", "OpenAI") }
    val scope = rememberCoroutineScope()

    var configs by remember { mutableStateOf(loadPlatformConfigs(prefs, slots)) }
    var backgroundAuths by remember {
        mutableStateOf(slots.map { BackgroundAuthRepository.load(prefs, it) })
    }
    var connectionModes by remember {
        mutableStateOf(
            slots.mapIndexed { index, slotName ->
                loadConnectionMode(
                    prefs = prefs,
                    slotName = slotName,
                    config = configs[index],
                    auth = backgroundAuths[index]
                )
            }
        )
    }
    var connectionStatuses by remember {
        mutableStateOf(List<ConnectionStatus?>(slots.size) { null })
    }
    var testingIndex by remember { mutableIntStateOf(-1) }
    var serviceMenuIndex by remember { mutableIntStateOf(-1) }
    var expandedIndex by remember { mutableIntStateOf(-1) }

    var showModelDialog by remember { mutableStateOf(false) }
    var modelList by remember { mutableStateOf(emptyList<String>()) }
    var selectedPlatformIndex by remember { mutableIntStateOf(-1) }

    var showErrorDialog by remember { mutableStateOf(false) }
    var errorTitle by remember { mutableStateOf("") }
    var errorReason by remember { mutableStateOf("") }
    var errorSuggestion by remember { mutableStateOf("") }
    var errorDetail by remember { mutableStateOf<String?>(null) }
    var showDetail by remember { mutableStateOf(false) }

    LaunchedEffect(authRefreshToken) {
        backgroundAuths = slots.map { BackgroundAuthRepository.load(prefs, it) }
        configs = loadPlatformConfigs(prefs, slots)
    }

    fun updateConfig(index: Int, newConfig: PlatformConfig) {
        configs = configs.toMutableList().apply { this[index] = newConfig }
        savePlatformConfig(prefs, slots[index], newConfig)
    }

    fun updateAuth(index: Int, newAuth: BackgroundAuthConfig) {
        backgroundAuths = backgroundAuths.toMutableList().apply { this[index] = newAuth }
    }

    fun updateMode(index: Int, mode: ConnectionMode) {
        connectionModes = connectionModes.toMutableList().apply { this[index] = mode }
        prefs.edit().putString(connectionModeKey(slots[index]), mode.name).apply()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text("AI API Dashboard", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Build: 2026-07-14-8B-R001",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = "打开需要显示的槽位，再选择“使用 API”或“登录官网账户”。账户用量会在后台自动同步。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )

        Text("选择要显示的槽位", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "最多 4 个。关闭槽位不会删除原配置和历史数据。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        slots.forEachIndexed { index, slotName ->
            val config = configs[index]
            val selectedService = serviceOptionFor(slotName, config.apiBase)
            val webAuthProfile = WebAuthProfileRegistry.findFor(slotName, config.apiBase)
            val capabilityAdapter = AdapterFactory.getAdapter(slotName, config.apiBase)
            val expectedAuthType = capabilityAdapter?.capabilityProfile?.backgroundAuthType
                ?: webAuthProfile?.authType
                ?: BackgroundAuthType.NONE
            val webAuthSupported = webAuthProfile != null && expectedAuthType != BackgroundAuthType.NONE
            val auth = backgroundAuths[index]
            val authConnected = webAuthSupported &&
                auth.enabled &&
                auth.authType == expectedAuthType &&
                auth.authValue.isNotBlank()
            val mode = if (!webAuthSupported && connectionModes[index] == ConnectionMode.WEB) {
                ConnectionMode.API
            } else {
                connectionModes[index]
            }
            val expanded = expandedIndex == index

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "槽位 ${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = slotTitle(config, selectedService),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                text = slotStatus(config, mode, authConnected),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Switch(
                                checked = config.enabled,
                                onCheckedChange = { enabled ->
                                    updateConfig(index, config.copy(enabled = enabled))
                                    refreshWidget(context)
                                }
                            )
                            Text(
                                text = if (config.enabled) "显示" else "隐藏",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (config.enabled) {
                        OutlinedButton(
                            onClick = { expandedIndex = if (expanded) -1 else index },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) {
                            Text(if (expanded) "收起设置" else if (isConfigured(config, authConnected)) "编辑连接" else "开始配置")
                        }
                    }

                    if (config.enabled && expanded) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                        Text("选择服务", style = MaterialTheme.typography.titleMedium)
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            OutlinedButton(
                                onClick = { serviceMenuIndex = index },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("${selectedService.displayName}　▼")
                            }
                            DropdownMenu(
                                expanded = serviceMenuIndex == index,
                                onDismissRequest = { serviceMenuIndex = -1 },
                                modifier = Modifier.fillMaxWidth(0.86f)
                            ) {
                                serviceOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.displayName) },
                                        onClick = {
                                            serviceMenuIndex = -1
                                            if (option.id != selectedService.id) {
                                                BackgroundAuthRepository.clear(prefs, slotName)
                                                updateAuth(index, BackgroundAuthConfig())
                                                updateMode(index, ConnectionMode.API)
                                                updateConfig(
                                                    index,
                                                    config.copy(
                                                        name = option.displayName,
                                                        apiBase = option.defaultApiBase,
                                                        model = ""
                                                    )
                                                )
                                                connectionStatuses = connectionStatuses.toMutableList().apply {
                                                    this[index] = null
                                                }
                                                Toast.makeText(
                                                    context,
                                                    "已切换为 ${option.displayName}，请重新连接",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        val currentConfig = configs[index]
                        val currentService = serviceOptionFor(slotName, currentConfig.apiBase)
                        val currentWebProfile = WebAuthProfileRegistry.findFor(slotName, currentConfig.apiBase)
                        val currentAdapter = AdapterFactory.getAdapter(slotName, currentConfig.apiBase)
                        val currentExpectedAuth = currentAdapter?.capabilityProfile?.backgroundAuthType
                            ?: currentWebProfile?.authType
                            ?: BackgroundAuthType.NONE
                        val currentWebSupported = currentWebProfile != null && currentExpectedAuth != BackgroundAuthType.NONE
                        val currentAuth = backgroundAuths[index]
                        val currentAuthConnected = currentWebSupported &&
                            currentAuth.enabled &&
                            currentAuth.authType == currentExpectedAuth &&
                            currentAuth.authValue.isNotBlank()
                        val currentMode = if (!currentWebSupported && connectionModes[index] == ConnectionMode.WEB) {
                            ConnectionMode.API
                        } else {
                            connectionModes[index]
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("选择连接方式", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "只需要选择一种你最方便的方式。账户用量会自动读取，不需要单独配置 Billing。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (currentMode == ConnectionMode.API) {
                                Button(
                                    onClick = { updateMode(index, ConnectionMode.API) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("使用 API") }
                            } else {
                                OutlinedButton(
                                    onClick = { updateMode(index, ConnectionMode.API) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("使用 API") }
                            }

                            if (currentWebSupported) {
                                if (currentMode == ConnectionMode.WEB) {
                                    Button(
                                        onClick = { updateMode(index, ConnectionMode.WEB) },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("登录官网") }
                                } else {
                                    OutlinedButton(
                                        onClick = { updateMode(index, ConnectionMode.WEB) },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("登录官网") }
                                }
                            }
                        }

                        if (!currentWebSupported) {
                            Text(
                                text = "${currentService.displayName} 当前只开放 API 连接。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (currentMode == ConnectionMode.API) {
                            ApiConnectionEditor(
                                config = currentConfig,
                                status = connectionStatuses[index],
                                isTesting = testingIndex == index,
                                dataSummary = apiDataSummary(currentService),
                                onConfigChange = { updateConfig(index, it) },
                                onTest = {
                                    val testConfig = configs[index]
                                    if (testConfig.apiBase.isBlank() || testConfig.apiKey.isBlank()) {
                                        val status = ConnectionStatus.Error(
                                            title = "🌐 还不能检测",
                                            reason = "API 地址或 API Key 没有填写。",
                                            suggestion = "把两项填写完整后再试一次。"
                                        )
                                        connectionStatuses = connectionStatuses.toMutableList().apply {
                                            this[index] = status
                                        }
                                    } else {
                                        savePlatformConfig(prefs, slotName, testConfig)
                                        testingIndex = index
                                        connectionStatuses = connectionStatuses.toMutableList().apply {
                                            this[index] = null
                                        }
                                        scope.launch {
                                            when (val result = fetchModels(testConfig.apiBase, testConfig.apiKey)) {
                                                is TestResult.Success -> {
                                                    testingIndex = -1
                                                    if (result.models.size == 1) {
                                                        val selectedModel = result.models.first()
                                                        updateConfig(index, testConfig.copy(model = selectedModel))
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
                                    }
                                }
                            )
                        } else {
                            OfficialAccountEditor(
                                service = currentService,
                                config = currentConfig,
                                authConnected = currentAuthConnected,
                                dataSummary = webDataSummary(currentService),
                                onNameChange = { name -> updateConfig(index, currentConfig.copy(name = name)) },
                                onConnect = {
                                    currentWebProfile?.let { profile ->
                                        context.startActivity(
                                            WebAuthActivity.createIntent(
                                                context = context,
                                                profileId = profile.profileId,
                                                targetInstanceKey = slotName
                                            )
                                        )
                                    }
                                },
                                onDisconnect = {
                                    if (BackgroundAuthRepository.clear(prefs, slotName)) {
                                        updateAuth(index, BackgroundAuthConfig())
                                        Toast.makeText(context, "官网账户已断开", Toast.LENGTH_SHORT).show()
                                        refreshWidget(context)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                configs.forEachIndexed { index, config ->
                    savePlatformConfig(prefs, slots[index], config)
                }
                refreshWidget(context)
                Toast.makeText(context, "配置已保存，Widget 正在刷新", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 32.dp)
        ) {
            Text("保存并刷新 Widget")
        }
    }

    if (showModelDialog && modelList.isNotEmpty() && selectedPlatformIndex in slots.indices) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text("选择要显示的模型") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "已经找到 ${modelList.size} 个模型，请选择这个槽位要显示的模型。",
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
                    Text("发生了什么", style = MaterialTheme.typography.titleSmall)
                    Text(errorReason, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                    Text("怎么解决", style = MaterialTheme.typography.titleSmall)
                    Text(errorSuggestion, modifier = Modifier.padding(top = 4.dp))
                    errorDetail?.let { detail ->
                        TextButton(onClick = { showDetail = !showDetail }) {
                            Text(if (showDetail) "隐藏技术详情" else "查看技术详情")
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
}

@Composable
private fun ApiConnectionEditor(
    config: PlatformConfig,
    status: ConnectionStatus?,
    isTesting: Boolean,
    dataSummary: String,
    onConfigChange: (PlatformConfig) -> Unit,
    onTest: () -> Unit
) {
    Text("使用 API", style = MaterialTheme.typography.titleMedium)
    Text(
        text = "填入 API 地址和 API Key，系统会自动查找模型。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
    )

    OutlinedTextField(
        value = config.name,
        onValueChange = { onConfigChange(config.copy(name = it)) },
        label = { Text("备注名称（可选）") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = config.apiBase,
        onValueChange = { onConfigChange(config.copy(apiBase = it, model = "")) },
        label = { Text("API 地址") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true
    )
    OutlinedTextField(
        value = config.apiKey,
        onValueChange = { onConfigChange(config.copy(apiKey = it, model = "")) },
        label = { Text("API Key") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation()
    )

    if (config.model.isNotBlank()) {
        Text(
            text = "当前模型：${config.model}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 10.dp)
        )
    }

    status?.let {
        when (it) {
            is ConnectionStatus.Success -> SimpleStatusCard(
                title = "API 已连接",
                body = "已选择模型：${it.model}\n$dataSummary",
                isError = false
            )
            is ConnectionStatus.Error -> SimpleStatusCard(
                title = it.title,
                body = "${it.reason}\n${it.suggestion}",
                isError = true
            )
        }
    }

    Button(
        onClick = onTest,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        enabled = !isTesting
    ) {
        Text(if (isTesting) "正在查找模型…" else "检测连接并选择模型")
    }

    Text(
        text = "连接成功后：$dataSummary",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp)
    )
}

@Composable
private fun OfficialAccountEditor(
    service: ServiceOption,
    config: PlatformConfig,
    authConnected: Boolean,
    dataSummary: String,
    onNameChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Text("登录官网账户", style = MaterialTheme.typography.titleMedium)
    Text(
        text = "App 会打开 ${service.displayName} 官网。你亲自登录后，账户状态只保存在本机。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
    )

    OutlinedTextField(
        value = config.name,
        onValueChange = onNameChange,
        label = { Text("显示名称（可选）") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    SimpleStatusCard(
        title = if (authConnected) "官网账户已连接" else "官网账户未连接",
        body = if (authConnected) {
            "可自动读取：$dataSummary"
        } else {
            "登录后可读取：$dataSummary"
        },
        isError = false
    )

    Button(
        onClick = onConnect,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
    ) {
        Text(if (authConnected) "重新连接官网账户" else "登录 ${service.displayName}")
    }

    if (authConnected) {
        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("断开官网账户")
        }
    }

    Text(
        text = "无需手动复制 Cookie 或 Token；余额和用量会在后台自动同步。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp)
    )
}

@Composable
private fun SimpleStatusCard(title: String, body: String, isError: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun connectionModeKey(slotName: String): String {
    return "connection_mode_${slotName.lowercase()}"
}

private fun loadConnectionMode(
    prefs: android.content.SharedPreferences,
    slotName: String,
    config: PlatformConfig,
    auth: BackgroundAuthConfig
): ConnectionMode {
    val saved = prefs.getString(connectionModeKey(slotName), null)
    if (saved == ConnectionMode.API.name) return ConnectionMode.API
    if (saved == ConnectionMode.WEB.name) return ConnectionMode.WEB

    val webProfile = WebAuthProfileRegistry.findFor(slotName, config.apiBase)
    val webConnected = webProfile != null &&
        auth.enabled &&
        auth.authType == webProfile.authType &&
        auth.authValue.isNotBlank()
    return if (webConnected) ConnectionMode.WEB else ConnectionMode.API
}

private fun slotTitle(config: PlatformConfig, service: ServiceOption): String {
    return config.model.ifBlank {
        config.name.ifBlank { service.displayName }
    }
}

private fun slotStatus(
    config: PlatformConfig,
    mode: ConnectionMode,
    authConnected: Boolean
): String {
    if (!config.enabled) return "此槽位已隐藏"
    return when (mode) {
        ConnectionMode.API -> when {
            config.apiBase.isBlank() || config.apiKey.isBlank() -> "等待填写 API"
            config.model.isBlank() -> "API 已填写，等待检测模型"
            else -> "API 已连接 · ${config.model}"
        }
        ConnectionMode.WEB -> if (authConnected) {
            "官网账户已连接 · 用量自动同步"
        } else {
            "等待登录官网账户"
        }
    }
}

private fun isConfigured(config: PlatformConfig, authConnected: Boolean): Boolean {
    return (config.apiBase.isNotBlank() && config.apiKey.isNotBlank() && config.model.isNotBlank()) || authConnected
}

private fun apiDataSummary(service: ServiceOption): String {
    return when (service.id) {
        "newapi" -> "模型列表，以及服务实际开放的额度和用量"
        "mimo" -> "模型列表；官网登录后还能补充余额和用量"
        "deepseek" -> "模型列表和账户余额"
        "aihuangniu" -> "模型列表和接口实际返回的用量"
        else -> "接口实际返回的数据"
    }
}

private fun webDataSummary(service: ServiceOption): String {
    return when (service.id) {
        "mimo" -> "余额、本月消费、Token、请求次数"
        "deepseek" -> "余额、本月消费、累计消费、Token"
        "aihuangniu" -> "余额、账户用量、请求次数、Token"
        else -> "官网账户实际开放的数据"
    }
}

private fun serviceOptionFor(slotName: String, apiBase: String): ServiceOption {
    return when {
        apiBase.contains("coolyeah.net", ignoreCase = true) -> serviceOptions[0]
        apiBase.contains("platform.xiaomimimo.com", ignoreCase = true) -> serviceOptions[1]
        apiBase.contains("api.deepseek.com", ignoreCase = true) -> serviceOptions[2]
        apiBase.contains("aihuangniu.com", ignoreCase = true) -> serviceOptions[3]
        slotName.equals("MiMo", ignoreCase = true) -> serviceOptions[1]
        slotName.equals("DeepSeek", ignoreCase = true) -> serviceOptions[2]
        slotName.equals("OpenAI", ignoreCase = true) -> serviceOptions[3]
        else -> serviceOptions[0]
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
