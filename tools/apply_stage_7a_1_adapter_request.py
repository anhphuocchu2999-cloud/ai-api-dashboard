from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


# 1) PROJECT.md: document the confirmed scope before business code changes.
project_path = "PROJECT.md"
project = read(project_path)
marker = "## Stage 7A-1：Adapter 凭据输入统一"
if marker not in project:
    project = project.rstrip() + """

---

## Stage 7A-1：Adapter 凭据输入统一

本阶段不新建第二套后端，不改变现有 Adapter 的职责。

现有 Adapter 继续作为平台差异统一层，负责：

- HTTP 请求
- 平台协议
- JSON 解析
- 错误转换
- WidgetData 标准化

本阶段只解决一个问题：

`PlatformAdapter.fetchData()` 不再把 API Key、Bearer Token、Cookie 混在同一个 `apiKey` 参数中。

统一输入对象：

```text
AdapterRequest
├─ apiBase
├─ modelApiKey
├─ modelName
├─ backgroundAuthType
└─ backgroundCredential
```

认证规则：

- 模型 API Key：用于 `/v1/models`、模型调用、模型用量等接口。
- Bearer Token：通过网页登录获得，用于账户后台余额、套餐、Profile 等接口。
- Cookie：通过网页登录获得，用于账户后台余额、套餐、Profile 等接口。
- Billing、Usage、Balance、Profile 属于数据接口类型，不属于新的认证方式。

平台组合示例：

- Kimi / New API：模型 API Key。
- DeepSeek 官方：模型 API Key。
- MiMo：模型 API Key + Cookie。
- 爱黄牛：模型 API Key + 后台 Bearer Token。

兼容要求：

- 保留现有 API Key、Cookie、Bearer Token 的存储格式。
- 覆盖安装后不得要求 MiMo 重新登录。
- 不改变现有余额、用量、缓存和 Widget 展示结果。
- 不新增平台，不修改 UI，不修改 Widget 布局。
- Adapter 仍统一通过 AdapterFactory 路由。
""".rstrip() + "\n"
    write(project_path, project)


# 2) Add explicit adapter request model.
adapter_request_path = "app/src/main/java/com/java/myapplication/adapter/AdapterRequest.kt"
adapter_request = """package com.java.myapplication.adapter

import com.java.myapplication.adapter.auth.BackgroundAuthType

/**
 * Adapter 的统一输入。
 *
 * 模型 API Key 与网页登录获得的后台凭据必须分开传递，
 * 禁止继续把 Cookie / Bearer Token 混入名为 apiKey 的参数。
 */
data class AdapterRequest(
    val apiBase: String,
    val modelApiKey: String,
    val modelName: String? = null,
    val backgroundAuthType: BackgroundAuthType = BackgroundAuthType.NONE,
    val backgroundCredential: String = ""
) {
    val hasBackgroundCredential: Boolean
        get() = backgroundAuthType != BackgroundAuthType.NONE && backgroundCredential.isNotBlank()
}
"""
write(adapter_request_path, adapter_request)


# 3) Extend PlatformAdapter with the new input while retaining the legacy method.
platform_adapter_path = "app/src/main/java/com/java/myapplication/adapter/PlatformAdapter.kt"
platform_adapter = read(platform_adapter_path)
old_platform_method = """    /**
     * 获取数据
     * @param apiBase API Base URL
     * @param apiKey API Key 或授权 Token
     * @param modelName 配置的模型名称（可选，用于用量查询等场景）
     * @return WidgetData 统一数据模型
     */
    fun fetchData(apiBase: String, apiKey: String, modelName: String? = null): WidgetData
"""
new_platform_method = """    /**
     * 使用明确区分的凭据输入获取数据。
     *
     * 默认实现仍调用旧方法，供只需要模型 API Key 的 Adapter 兼容使用。
     * 需要 Cookie / Bearer Token 的 Adapter 应覆盖本方法。
     */
    fun fetchData(request: AdapterRequest): WidgetData {
        return fetchData(request.apiBase, request.modelApiKey, request.modelName)
    }

    /**
     * 旧版兼容入口。
     *
     * 新代码不得再把 Cookie / Bearer Token 作为 apiKey 传入。
     */
    fun fetchData(apiBase: String, apiKey: String, modelName: String? = null): WidgetData
"""
if "fun fetchData(request: AdapterRequest)" not in platform_adapter:
    platform_adapter = replace_once(
        platform_adapter,
        old_platform_method,
        new_platform_method,
        "PlatformAdapter method"
    )
    write(platform_adapter_path, platform_adapter)


# 4) MiMo: consume Cookie from the background credential field.
mimo_path = "app/src/main/java/com/java/myapplication/adapter/MiMoAdapter.kt"
mimo = read(mimo_path)
if "override fun fetchData(request: AdapterRequest)" not in mimo:
    mimo = replace_once(
        mimo,
        "package com.java.myapplication.adapter\n\n",
        "package com.java.myapplication.adapter\n\nimport com.java.myapplication.adapter.auth.BackgroundAuthType\n",
        "MiMo import"
    )
    insertion = """    override fun fetchData(request: AdapterRequest): WidgetData {
        val cookie = if (
            request.backgroundAuthType == BackgroundAuthType.COOKIE &&
            request.backgroundCredential.isNotBlank()
        ) {
            request.backgroundCredential
        } else {
            // 兼容旧调用；正常新流程应从 backgroundCredential 取得 Cookie。
            request.modelApiKey
        }
        return fetchData(request.apiBase, cookie, request.modelName)
    }

"""
    mimo = replace_once(
        mimo,
        "    override fun fetchData(apiBase: String, apiKey: String, modelName: String?): WidgetData {\n",
        insertion + "    override fun fetchData(apiBase: String, apiKey: String, modelName: String?): WidgetData {\n",
        "MiMo request override"
    )
    write(mimo_path, mimo)


# 5) Aihuangniu: use model API key and background Bearer Token independently.
ai_path = "app/src/main/java/com/java/myapplication/adapter/AihuangniuAdapter.kt"
ai = read(ai_path)
old_ai_method = """    override fun fetchData(apiBase: String, apiKey: String, modelName: String?): WidgetData {
        val normalizedBase = apiBase.trim().trimEnd('/')

        if (apiKey.isBlank()) {
            return WidgetData.error(platformName, "Key未配置")
        }

        // 1. 获取用户资料（余额等）——使用后台 Bearer Token
        val profileData = fetchProfileData(normalizedBase, backgroundBearerToken ?: apiKey)

        // 2. 获取 /v1/usage 累计统计——使用模型 API Key
        val effectiveModelName = modelName?.takeIf { it.isNotBlank() } ?: profileData.modelName
        val usageCumulative = fetchUsageCumulative(normalizedBase, apiKey, effectiveModelName)

        android.util.Log.d("AihuangniuAdapter", "累计统计: requests=${usageCumulative?.first}, tokens=${usageCumulative?.second}, cost=${usageCumulative?.third}")

        // 3. 合并结果
        return if (profileData.isSuccess) {
            WidgetData(
                platformName = platformName,
                modelName = effectiveModelName,
                primaryMetric = profileData.primaryMetric,
                usageMetrics = emptyList(), // Core 2 由 Provider 本地快照生成
                percentage = profileData.percentage,
                percentageLabel = profileData.percentageLabel,
                auxiliaryMetrics = profileData.auxiliaryMetrics,
                statusText = profileData.statusText,
                isSuccess = true,
                displayLabel = profileData.displayLabel,
                total = profileData.total,
                used = profileData.used,
                remaining = profileData.remaining,
                usagePercent = profileData.usagePercent,
                isAvailable = true,
                cumulativeUsageRequests = usageCumulative?.first,
                cumulativeUsageTokens = usageCumulative?.second,
                cumulativeUsageActualCost = usageCumulative?.third
            )
        } else {
            profileData
        }
    }
"""
new_ai_method = """    override fun fetchData(request: AdapterRequest): WidgetData {
        val backgroundToken = if (
            request.backgroundAuthType == BackgroundAuthType.BEARER_TOKEN &&
            request.backgroundCredential.isNotBlank()
        ) {
            request.backgroundCredential
        } else {
            backgroundBearerToken.orEmpty()
        }

        return fetchDataWithCredentials(
            apiBase = request.apiBase,
            modelApiKey = request.modelApiKey,
            modelName = request.modelName,
            backgroundToken = backgroundToken
        )
    }

    override fun fetchData(apiBase: String, apiKey: String, modelName: String?): WidgetData {
        return fetchDataWithCredentials(
            apiBase = apiBase,
            modelApiKey = apiKey,
            modelName = modelName,
            backgroundToken = backgroundBearerToken.orEmpty()
        )
    }

    private fun fetchDataWithCredentials(
        apiBase: String,
        modelApiKey: String,
        modelName: String?,
        backgroundToken: String
    ): WidgetData {
        val normalizedBase = apiBase.trim().trimEnd('/')

        if (modelApiKey.isBlank()) {
            return WidgetData.error(platformName, "Key未配置")
        }

        // 1. 获取用户资料（余额等）——优先使用网页登录获得的后台 Bearer Token
        val profileCredential = backgroundToken.ifBlank { modelApiKey }
        val profileData = fetchProfileData(normalizedBase, profileCredential)

        // 2. 获取 /v1/usage 累计统计——始终使用模型 API Key
        val effectiveModelName = modelName?.takeIf { it.isNotBlank() } ?: profileData.modelName
        val usageCumulative = fetchUsageCumulative(normalizedBase, modelApiKey, effectiveModelName)

        android.util.Log.d("AihuangniuAdapter", "累计统计: requests=${usageCumulative?.first}, tokens=${usageCumulative?.second}, cost=${usageCumulative?.third}")

        // 3. 合并结果
        return if (profileData.isSuccess) {
            WidgetData(
                platformName = platformName,
                modelName = effectiveModelName,
                primaryMetric = profileData.primaryMetric,
                usageMetrics = emptyList(), // Core 2 由 Provider 本地快照生成
                percentage = profileData.percentage,
                percentageLabel = profileData.percentageLabel,
                auxiliaryMetrics = profileData.auxiliaryMetrics,
                statusText = profileData.statusText,
                isSuccess = true,
                displayLabel = profileData.displayLabel,
                total = profileData.total,
                used = profileData.used,
                remaining = profileData.remaining,
                usagePercent = profileData.usagePercent,
                isAvailable = true,
                cumulativeUsageRequests = usageCumulative?.first,
                cumulativeUsageTokens = usageCumulative?.second,
                cumulativeUsageActualCost = usageCumulative?.third
            )
        } else {
            profileData
        }
    }
"""
if "private fun fetchDataWithCredentials(" not in ai:
    ai = replace_once(ai, old_ai_method, new_ai_method, "Aihuangniu credential split")
    write(ai_path, ai)


# 6) Provider: generic auth loading + AdapterRequest; no platform-specific adapter construction.
provider_path = "app/src/main/java/com/java/myapplication/BalanceWidgetProvider.kt"
provider = read(provider_path)
provider = provider.replace(
    "import com.java.myapplication.adapter.AihuangniuAdapter\nimport com.java.myapplication.adapter.MiMoAdapter\n",
    "import com.java.myapplication.adapter.AdapterRequest\n"
)
if "import com.java.myapplication.adapter.auth.BackgroundAuthConfig" not in provider:
    provider = provider.replace(
        "import com.java.myapplication.adapter.WidgetData\n",
        "import com.java.myapplication.adapter.WidgetData\n"
        "import com.java.myapplication.adapter.auth.BackgroundAuthConfig\n"
        "import com.java.myapplication.adapter.auth.BackgroundAuthType\n"
    )

helper_marker = "        private fun loadBackgroundAuth("
if helper_marker not in provider:
    helper = """        private fun loadBackgroundAuth(
            prefs: android.content.SharedPreferences,
            platformName: String
        ): BackgroundAuthConfig {
            val authJson = prefs.getString("${platformName}_auth", null)
            if (authJson.isNullOrBlank()) {
                return BackgroundAuthConfig()
            }

            return try {
                val obj = org.json.JSONObject(authJson)
                val enabled = obj.optBoolean("enabled", false)
                if (!enabled) {
                    BackgroundAuthConfig()
                } else {
                    val authType = try {
                        BackgroundAuthType.valueOf(
                            obj.optString("authType", BackgroundAuthType.NONE.name)
                                .uppercase(Locale.ROOT)
                        )
                    } catch (_: Exception) {
                        BackgroundAuthType.NONE
                    }

                    BackgroundAuthConfig(
                        authType = authType,
                        authValue = obj.optString("authValue", ""),
                        enabled = authType != BackgroundAuthType.NONE,
                        updatedAt = obj.optLong("updatedAt", 0L)
                    )
                }
            } catch (_: Exception) {
                BackgroundAuthConfig()
            }
        }

"""
    provider = replace_once(
        provider,
        "        private fun fetchWidgetDataForPlatform(\n",
        helper + "        private fun fetchWidgetDataForPlatform(\n",
        "Provider auth helper insertion"
    )

old_provider_block = """            // 获取对应 Adapter：MiMo 直接使用 MiMoAdapter，其他平台通过 AdapterFactory
            val adapter = if (platformName == "MiMo") {
                MiMoAdapter()
            } else if (config.apiBase.contains("aihuangniu.com", ignoreCase = true)) {
                // Aihuangniu 需要双凭证：后台 Bearer Token + 模型 API Key
                val bearerToken = prefs.getString(platformName + "_auth", null)?.let { authJson ->
                    if (!authJson.isNullOrBlank()) {
                        try {
                            val clean = authJson.trimStart('{').trimEnd('}')
                            val authValueStart = clean.indexOf("\\\"authValue\\\":\\\"")
                            if (authValueStart != -1) {
                                val valueStart = authValueStart + "\\\"authValue\\\":\\\"".length
                                val valueEnd = clean.indexOf("\\\"", valueStart)
                                if (valueEnd != -1) clean.substring(valueStart, valueEnd) else null
                            } else null
                        } catch (_: Exception) { null }
                    } else null
                }
                AihuangniuAdapter(backgroundBearerToken = bearerToken)
            } else {
                AdapterFactory.getAdapter(platformName, config.apiBase)
            }
            if (adapter == null) {
                // 模型连接正常但余额接口需要登录授权
                return WidgetData(
                    platformName = platformName,
                    modelName = "余额需登录授权",
                    displayLabel = null,
                    total = null,
                    used = null,
                    remaining = null,
                    usagePercent = null,
                    isAvailable = true
                )
            }

            // 调用 Adapter 获取数据
            // 对于 MiMo，使用 Cookie 授权
            val authKey = when {
                platformName == "MiMo" -> {
                    val authJson = prefs.getString("MiMo_auth", null)
                    if (!authJson.isNullOrBlank()) {
                        try {
                            val obj = org.json.JSONObject(authJson)
                            val authType = obj.optString("authType", "")
                            val enabled = obj.optBoolean("enabled", false)
                            if (authType == "COOKIE" && enabled) {
                                obj.optString("authValue", "")
                            } else ""
                        } catch (_: Exception) { "" }
                    } else ""
                }
                else -> config.apiKey
            }

            // 临时日志：确认最终传入的 token 类型
            val isJwt = authKey.startsWith("eyJ")
            val tokenType = when {
                platformName == "MiMo" -> if (authKey.isNotBlank()) "Cookie" else "未授权"
                isJwt -> "JWT"
                else -> "APIKey"
            }
            android.util.Log.d("BalanceWidgetProvider", "平台=$platformName, apiBase=${config.apiBase}, token类型=$tokenType, 长度=${authKey.length}")

            val result = try {
                adapter.fetchData(config.apiBase, authKey, config.model)
            } catch (e: Exception) {
                WidgetData.error(platformName, "获取失败")
            }
"""
new_provider_block = """            // 所有 Adapter 统一通过 AdapterFactory 路由。
            val adapter = AdapterFactory.getAdapter(platformName, config.apiBase)
            if (adapter == null) {
                // 模型连接正常但余额接口需要登录授权或当前无对应数据 Adapter。
                return WidgetData(
                    platformName = platformName,
                    modelName = "余额需登录授权",
                    displayLabel = null,
                    total = null,
                    used = null,
                    remaining = null,
                    usagePercent = null,
                    isAvailable = true
                )
            }

            val backgroundAuth = loadBackgroundAuth(prefs, platformName)
            val request = AdapterRequest(
                apiBase = config.apiBase,
                modelApiKey = config.apiKey,
                modelName = config.model,
                backgroundAuthType = backgroundAuth.authType,
                backgroundCredential = backgroundAuth.authValue
            )

            // 只记录认证类型，不记录凭据值、长度或原始内容。
            android.util.Log.d(
                "BalanceWidgetProvider",
                "平台=$platformName, 后台授权=${request.backgroundAuthType.name}, 模型Key=${if (request.modelApiKey.isBlank()) "未配置" else "已配置"}"
            )

            val result = try {
                adapter.fetchData(request)
            } catch (e: Exception) {
                WidgetData.error(platformName, "获取失败")
            }
"""
if "val request = AdapterRequest(" not in provider:
    provider = replace_once(provider, old_provider_block, new_provider_block, "Provider AdapterRequest migration")

write(provider_path, provider)

print("Stage 7A-1 source migration applied successfully.")
print("Changed:")
print("- PROJECT.md")
print("- adapter/AdapterRequest.kt")
print("- adapter/PlatformAdapter.kt")
print("- adapter/MiMoAdapter.kt")
print("- adapter/AihuangniuAdapter.kt")
print("- BalanceWidgetProvider.kt")
