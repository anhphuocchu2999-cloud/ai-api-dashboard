package com.java.myapplication.discovery

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class DashboardReplayResult(
    val displayBody: String,
    val metrics: List<DashboardReplayMetric>
) {
    val metricCount: Int
        get() = metrics.size
}

data class DashboardReplayMetric(
    val type: String,
    val label: String,
    val value: String,
    val unit: String
)

class DashboardRecipeClient {
    @Volatile
    private var activeConnection: HttpURLConnection? = null

    fun fetch(
        recipe: DashboardRequestRecipe,
        cookiesByEndpoint: Map<String, String>,
        replayHeadersByEndpoint: Map<String, Map<String, String>> = emptyMap()
    ): DashboardReplayResult {
        DashboardRecipeRules.validate(recipe)?.let { throw IllegalArgumentException(it) }
        val responses = mutableMapOf<String, Any>()
        var previousRequestAt = 0L
        for (endpoint in recipe.endpoints) {
            val cookie = cookiesByEndpoint[endpoint].orEmpty()
            val replayHeaders = DashboardReplayHeaderPolicy.sanitize(
                replayHeadersByEndpoint[endpoint].orEmpty()
            )
            if (cookie.isBlank() && replayHeaders.isEmpty()) {
                throw IllegalStateException("直连认证信息缺失，请重新打开网页并登录后捕获")
            }
            val waitMillis = MIN_HOST_INTERVAL_MS - (System.currentTimeMillis() - previousRequestAt)
            if (previousRequestAt > 0L && waitMillis > 0L) Thread.sleep(waitMillis)
            responses[endpoint] = requestJson(endpoint, recipe, cookie, replayHeaders)
            previousRequestAt = System.currentTimeMillis()
        }

        val outputMetrics = JSONArray()
        val replayMetrics = mutableListOf<DashboardReplayMetric>()
        for (metric in recipe.metrics) {
            val root = responses[metric.endpoint]
                ?: throw IllegalStateException("数据接口没有返回可用内容")
            val resolution = DashboardJsonPathValidator.resolve(root, metric.jsonPath)
            if (!resolution.pathSafe || !resolution.found) {
                throw IllegalStateException("“${metric.label}”字段已经变化，请重新识别这个仪表盘")
            }
            if (resolution.valueType != metric.valueType) {
                throw IllegalStateException("“${metric.label}”的数据类型已经变化，请重新识别这个仪表盘")
            }
            val sampleValue = DashboardJsonPathValidator.sampleValue(resolution.value)
            val replayMetric = DashboardReplayMetric(
                type = metric.type,
                label = metric.label.ifBlank { metric.type },
                value = sampleValue.toString(),
                unit = metric.unit
            )
            replayMetrics += replayMetric
            outputMetrics.put(
                JSONObject()
                    .put("type", replayMetric.type)
                    .put("label", replayMetric.label)
                    .put("value", sampleValue)
                    .put("unit", replayMetric.unit)
                    .put("source", "本次直接请求")
            )
        }
        val output = JSONObject()
            .put("pagePurpose", recipe.pagePurpose)
            .put("refreshedAt", System.currentTimeMillis())
            .put("metrics", outputMetrics)
        return DashboardReplayResult(output.toString(2), replayMetrics)
    }

    fun cancel() {
        activeConnection?.disconnect()
        activeConnection = null
    }

    private fun requestJson(
        endpoint: String,
        recipe: DashboardRequestRecipe,
        cookie: String,
        replayHeaders: Map<String, String>
    ): Any {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        activeConnection = connection
        return try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            if (cookie.isNotBlank()) connection.setRequestProperty("Cookie", cookie)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Origin", recipe.origin)
            connection.setRequestProperty("Referer", recipe.dashboardUrl)
            connection.setRequestProperty("User-Agent", MOBILE_USER_AGENT)
            replayHeaders.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException(httpError(status))
            val responseText = connection.inputStream.use { stream ->
                val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
                readLimited(reader)
            }
            val parsed = runCatching { JSONTokener(responseText).nextValue() }.getOrNull()
            if (parsed !is JSONObject && parsed !is JSONArray) {
                throw IllegalStateException("数据接口没有返回 JSON，请重新识别这个仪表盘")
            }
            parsed
        } finally {
            connection.disconnect()
            if (activeConnection === connection) activeConnection = null
        }
    }

    private fun readLimited(reader: BufferedReader): String {
        val output = StringBuilder()
        val buffer = CharArray(8_192)
        while (output.length <= MAX_RESPONSE_CHARS) {
            val read = reader.read(buffer)
            if (read <= 0) break
            output.append(buffer, 0, read)
            if (output.length > MAX_RESPONSE_CHARS) {
                throw IllegalStateException("数据接口返回内容过大，首版暂不支持")
            }
        }
        if (output.isBlank()) throw IllegalStateException("数据接口返回了空内容")
        return output.toString()
    }

    private fun httpError(status: Int): String = when (status) {
        301, 302, 303, 307, 308 -> "数据接口发生跳转，首版不会携带登录状态继续跳转"
        401, 403 -> "当前直连授权被站点拒绝；登录可能已过期，或站点还需要未支持的动态认证信息"
        404 -> "保存的数据接口已经不存在，请重新识别这个仪表盘"
        429 -> "请求过于频繁，请稍后再试"
        in 500..599 -> "仪表盘服务器暂时异常"
        else -> "数据接口请求失败（HTTP $status）"
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
        const val MIN_HOST_INTERVAL_MS = 500L
        const val MAX_RESPONSE_CHARS = 262_144
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
    }
}
