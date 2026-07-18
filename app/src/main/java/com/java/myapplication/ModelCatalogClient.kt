package com.java.myapplication

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed class TestResult {
    data class Success(val models: List<String>) : TestResult()

    data class Error(
        val title: String,
        val reason: String,
        val suggestion: String,
        val detail: String? = null
    ) : TestResult()
}

/** Shared OpenAI-compatible `/v1/models` client used by the main app and discovery lab. */
object ModelCatalogClient {
    fun fetch(apiBase: String, apiKey: String): TestResult {
        val requestUrl = modelListUrl(apiBase)
            ?: return TestResult.Error(
                title = "API 地址无效",
                reason = "API Base 必须是有效的 HTTPS 地址。",
                suggestion = "请检查 API 地址后再试。"
            )
        return try {
            val conn = URL(requestUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doInput = true

            try {
                conn.connect()
            } catch (error: Exception) {
                conn.disconnect()
                return TestResult.Error(
                    title = "没有连接成功",
                    reason = "API 地址填写有误，或者服务器暂时无法访问。",
                    suggestion = "检查 API 地址后再试一次。",
                    detail = "请求 URL: $requestUrl\n异常: ${error.javaClass.simpleName}\n${error.message}"
                )
            }

            val responseCode = conn.responseCode
            val response = if (responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorText = try {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                } catch (_: Exception) {
                    ""
                }
                conn.disconnect()
                val error = when (responseCode) {
                    401, 403 -> Triple(
                        "API Key 无法通过验证",
                        "当前 API Key 无效、已失效，或者没有读取模型列表的权限。",
                        "请检查 API Key 和对应账户权限后再试。"
                    )
                    404 -> Triple(
                        "没有找到模型接口",
                        "当前地址没有提供兼容的 /v1/models 接口。",
                        "请检查 API 地址，或向服务提供方确认模型列表路径。"
                    )
                    429 -> Triple(
                        "请求过于频繁",
                        "服务暂时限制了模型检测请求。",
                        "请稍等一会再试，不需要更换 API Key。"
                    )
                    in 500..599 -> Triple(
                        "服务器暂时异常",
                        "服务端当前无法完成模型检测。",
                        "请稍后重试；如果持续失败，再联系服务提供方。"
                    )
                    else -> Triple(
                        "模型检测失败",
                        "服务返回了 HTTP $responseCode，当前没有取得模型列表。",
                        "请检查 API 地址后重试。"
                    )
                }
                return TestResult.Error(
                    title = error.first,
                    reason = error.second,
                    suggestion = error.third,
                    detail = "请求 URL: $requestUrl\nHTTP 状态码: $responseCode\n返回内容: ${errorText.take(500)}"
                )
            }
            conn.disconnect()

            val root = JSONObject(response)
            val data = root.optJSONArray("data")
                ?: return TestResult.Error(
                    title = "没找到模型",
                    reason = "这个服务没有返回兼容的模型列表。",
                    suggestion = "确认服务支持 OpenAI 兼容的 /v1/models 接口。",
                    detail = "请求 URL: $requestUrl\n返回内容: ${response.take(500)}"
                )
            val models = buildList {
                for (index in 0 until data.length()) {
                    val modelId = data.optJSONObject(index)?.optString("id").orEmpty().trim()
                    if (modelId.isNotBlank()) add(modelId)
                }
            }.distinct()

            if (models.isEmpty()) {
                TestResult.Error(
                    title = "没找到模型",
                    reason = "接口没有返回可使用的模型名称。",
                    suggestion = "稍后再试，或向服务提供方确认模型列表接口。",
                    detail = "请求 URL: $requestUrl\n返回内容: ${response.take(500)}"
                )
            } else {
                TestResult.Success(models)
            }
        } catch (_: java.net.SocketTimeoutException) {
            TestResult.Error(
                title = "网络有点慢",
                reason = "连接超时了。",
                suggestion = "换个网络，或者等几秒钟再试一次。"
            )
        } catch (error: Exception) {
            TestResult.Error(
                title = "没有连接成功",
                reason = "API 地址填写有误，或者服务器暂时无法访问。",
                suggestion = "检查 API 地址后再试一次。",
                detail = "异常: ${error.javaClass.simpleName}\n${error.message}"
            )
        }
    }

    fun modelListUrl(apiBase: String): String? {
        val normalizedBase = apiBase.trim().trimEnd('/')
        val parsed = runCatching { java.net.URI(normalizedBase) }.getOrNull() ?: return null
        if (
            !parsed.scheme.equals("https", ignoreCase = true) ||
            parsed.host.isNullOrBlank() ||
            parsed.userInfo != null ||
            parsed.query != null ||
            parsed.fragment != null
        ) {
            return null
        }
        return if (normalizedBase.endsWith("/v1", ignoreCase = true)) {
            "$normalizedBase/models"
        } else {
            "$normalizedBase/v1/models"
        }
    }
}

suspend fun fetchModels(apiBase: String, apiKey: String): TestResult =
    withContext(Dispatchers.IO) { ModelCatalogClient.fetch(apiBase, apiKey) }
