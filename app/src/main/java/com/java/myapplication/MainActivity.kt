package com.java.myapplication

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
    var editingIndex by remember { mutableIntStateOf(-1) }
    var showServicePicker by remember { mutableStateOf(false) }

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

    fun closeEditor() {
        editingIndex = -1
        showServicePicker = false
    }

    BackHandler(enabled = editingIndex >= 0) {
        closeEditor()
    }

    if (editingIndex < 0) {
        SlotListScreen(
            modifier = modifier,
            configs = configs,
            slots = slots,
            backgroundAuths = backgroundAuths,
            connectionModes = connectionModes,
            onToggle = { index, enabled ->
                updateConfig(index, configs[index].copy(enabled = enabled))
                refreshWidget(context)
            },
            onEdit = { index ->
                editingIndex = index
                showServicePicker = false
            }
        )
    } else {
        val index = editingIndex
        val slotName = slots[index]
        val config = configs[index]
        val service = serviceOptionFor(slotName, config.apiBase)
        val webProfile = WebAuthProfileRegistry.findFor(slotName, config.apiBase)
        val adapter = AdapterFactory.getAdapter(slotName, config.apiBase)
        val expectedAuthType = adapter?.capabilityProfile?.backgroundAuthType
            ?: webProfile?.authType
            ?: BackgroundAuthType.NONE
        val webSupported = webProfile != null && expectedAuthType != BackgroundAuthType.NONE
        val auth = backgroundAuths[index]
        val authConnected = webSupported &&
            auth.enabled &&
            auth.authType == expectedAuthType &&
            auth.authValue.isNotBlank()
        val mode = if (!webSupported && connectionModes[index] == ConnectionMode.WEB) {
            ConnectionMode.API
        } else {
            connectionModes[index]
        }

        SlotEditorScreen(
            modifier = modifier,
            slotNumber = index + 1,
            config = config,
            service = service,
            showServicePicker = showServicePicker,
            webSupported = webSupported,
            authConnected = authConnected,
            mode = mode,
            status = connectionStatuses[index],
            isTesting = testingIndex == index,
            onBack = { closeEditor() },
            onShowServicePicker = { showServicePicker = !showServicePicker },
            onChooseService = { option ->
                showServicePicker = false
                if (option.id != service.id) {
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
                    Toast.makeText(context, "已选择 ${option.displayName}", Toast.LENGTH_SHORT).show()
                }
            },
            onModeChange = { newMode -> updateMode(index, newMode) },
            onConfigChange = { updated -> updateConfig(index, updated) },
            onTest = {
                val testConfig = configs[index]
                if (testConfig.apiBase.isBlank() || testConfig.apiKey.isBlank()) {
                    connectionStatuses = connectionStatuses.toMutableList().apply {
                        this[index] = ConnectionStatus.Error(
                            title = "还不能检测",
                            reason = "API 地址或 API Key 没有填写。",
                            suggestion = "把两项填写完整后再试一次。"
                        )
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
                                connectionStatuses = connectionStatuses.toMutableList().apply {
                                    this[index] = ConnectionStatus.Error(
                                        result.title,
                                        result.reason,
                                        result.suggestion
                                    )
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
            },
            onConnect = {
                webProfile?.let { profile ->
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

    if (showModelDialog && modelList.isNotEmpty() && selectedPlatformIndex in slots.indices) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text("选择模型") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    modelList.forEach { modelId ->
                        TextButton(
                            onClick = {
                                val index = selectedPlatformIndex
                                updateConfig(index, configs[index].copy(model = modelId))
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
                    Text(errorReason)
                    Text(
                        text = errorSuggestion,
                        modifier = Modifier.padding(top = 10.dp)
                    )
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
}

@Composable
private fun SlotListScreen(
    modifier: Modifier,
    configs: List<PlatformConfig>,
    slots: List<String>,
    backgroundAuths: List<BackgroundAuthConfig>,
    connectionModes: List<ConnectionMode>,
    onToggle: (Int, Boolean) -> Unit,
    onEdit: (Int) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("AI API Dashboard", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "打开要显示的槽位，点设置即可。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
        )

        configs.forEachIndexed { index, config ->
            val slotName = slots[index]
            val service = serviceOptionFor(slotName, config.apiBase)
            val webProfile = WebAuthProfileRegistry.findFor(slotName, config.apiBase)
            val adapter = AdapterFactory.getAdapter(slotName, config.apiBase)
            val expectedAuthType = adapter?.capabilityProfile?.backgroundAuthType
                ?: webProfile?.authType
                ?: BackgroundAuthType.NONE
            val auth = backgroundAuths[index]
            val authConnected = webProfile != null &&
                expectedAuthType != BackgroundAuthType.NONE &&
                auth.enabled &&
                auth.authType == expectedAuthType &&
                auth.authValue.isNotBlank()
            val mode = connectionModes[index]

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "槽位 ${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = slotTitle(config, service),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                text = simpleSlotStatus(config, mode, authConnected),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Switch(
                            checked = config.enabled,
                            onCheckedChange = { onToggle(index, it) }
                        )
                    }

                    if (config.enabled) {
                        Button(
                            onClick = { onEdit(index) },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) {
                            Text(if (isConfigured(config, authConnected)) "编辑" else "设置")
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SlotEditorScreen(
    modifier: Modifier,
    slotNumber: Int,
    config: PlatformConfig,
    service: ServiceOption,
    showServicePicker: Boolean,
    webSupported: Boolean,
    authConnected: Boolean,
    mode: ConnectionMode,
    status: ConnectionStatus?,
    isTesting: Boolean,
    onBack: () -> Unit,
    onShowServicePicker: () -> Unit,
    onChooseService: (ServiceOption) -> Unit,
    onModeChange: (ConnectionMode) -> Unit,
    onConfigChange: (PlatformConfig) -> Unit,
    onTest: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onBack) { Text("‹ 返回") }
        Text("设置槽位 $slotNumber", style = MaterialTheme.typography.headlineMedium)

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("服务", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = service.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onShowServicePicker) {
                        Text(if (showServicePicker) "收起" else "更换")
                    }
                }

                if (showServicePicker) {
                    Spacer(modifier = Modifier.height(8.dp))
                    serviceOptions.chunked(2).forEach { rowOptions ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowOptions.forEach { option ->
                                if (option.id == service.id) {
                                    Button(
                                        onClick = { onChooseService(option) },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(option.displayName) }
                                } else {
                                    OutlinedButton(
                                        onClick = { onChooseService(option) },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(option.displayName) }
                                }
                            }
                            if (rowOptions.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = "连接方式",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (mode == ConnectionMode.API) {
                Button(
                    onClick = { onModeChange(ConnectionMode.API) },
                    modifier = Modifier.weight(1f)
                ) { Text("使用 API") }
            } else {
                OutlinedButton(
                    onClick = { onModeChange(ConnectionMode.API) },
                    modifier = Modifier.weight(1f)
                ) { Text("使用 API") }
            }

            if (webSupported) {
                if (mode == ConnectionMode.WEB) {
                    Button(
                        onClick = { onModeChange(ConnectionMode.WEB) },
                        modifier = Modifier.weight(1f)
                    ) { Text("登录官网") }
                } else {
                    OutlinedButton(
                        onClick = { onModeChange(ConnectionMode.WEB) },
                        modifier = Modifier.weight(1f)
                    ) { Text("登录官网") }
                }
            }
        }

        if (!webSupported) {
            Text(
                text = "该服务目前使用 API 连接。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (mode == ConnectionMode.API) {
                    ApiConnectionEditor(
                        config = config,
                        status = status,
                        isTesting = isTesting,
                        dataSummary = apiDataSummary(service),
                        onConfigChange = onConfigChange,
                        onTest = onTest
                    )
                } else {
                    OfficialAccountEditor(
                        service = service,
                        authConnected = authConnected,
                        dataSummary = webDataSummary(service),
                        onConnect = onConnect,
                        onDisconnect = onDisconnect
                    )
                }
            }
        }
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
    Text("API 连接", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = config.apiBase,
        onValueChange = { onConfigChange(config.copy(apiBase = it, model = "")) },
        label = { Text("API 地址") },
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
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
            text = "模型：${config.model}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 10.dp)
        )
    }

    status?.let {
        when (it) {
            is ConnectionStatus.Success -> SimpleStatusCard(
                title = "连接成功",
                body = "已选择 ${it.model}\n$dataSummary",
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
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        enabled = !isTesting
    ) {
        Text(if (isTesting) "正在查找模型…" else "检测并选择模型")
    }
}

@Composable
private fun OfficialAccountEditor(
    service: ServiceOption,
    authConnected: Boolean,
    dataSummary: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Text("官网账户", style = MaterialTheme.typography.titleMedium)
    SimpleStatusCard(
        title = if (authConnected) "已连接" else "未连接",
        body = if (authConnected) {
            "正在自动同步：$dataSummary"
        } else {
            "登录后自动同步：$dataSummary"
        },
        isError = false
    )

    Button(
        onClick = onConnect,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    ) {
        Text(if (authConnected) "重新登录 ${service.displayName}" else "登录 ${service.displayName}")
    }

    if (authConnected) {
        TextButton(
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) {
            Text("断开账户")
        }
    }
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

private fun simpleSlotStatus(
    config: PlatformConfig,
    mode: ConnectionMode,
    authConnected: Boolean
): String {
    if (!config.enabled) return "已隐藏"
    return when (mode) {
        ConnectionMode.API -> if (config.model.isNotBlank()) "API 已连接" else "未配置"
        ConnectionMode.WEB -> if (authConnected) "官网账户已连接" else "未配置"
    }
}

private fun isConfigured(config: PlatformConfig, authConnected: Boolean): Boolean {
    return (config.apiBase.isNotBlank() && config.apiKey.isNotBlank() && config.model.isNotBlank()) || authConnected
}

private fun apiDataSummary(service: ServiceOption): String {
    return when (service.id) {
        "newapi" -> "模型及服务返回的额度和用量"
        "mimo" -> "模型列表"
        "deepseek" -> "模型和余额"
        "aihuangniu" -> "模型和接口返回的用量"
        else -> "接口实际返回的数据"
    }
}

private fun webDataSummary(service: ServiceOption): String {
    return when (service.id) {
        "mimo" -> "余额、本月消费、Token、请求次数"
        "deepseek" -> "余额、本月消费、累计消费、Token"
        "aihuangniu" -> "余额、用量、请求次数、Token"
        else -> "官网账户数据"
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
