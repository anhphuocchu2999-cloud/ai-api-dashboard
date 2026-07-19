package com.java.myapplication.discovery

import org.json.JSONArray
import org.json.JSONObject

data class DashboardMetricRecipe(
    val type: String,
    val label: String,
    val endpoint: String,
    val jsonPath: String,
    val valueType: String,
    val unit: String
)

data class DashboardRequestSpec(
    val endpoint: String,
    val method: String,
    val queryParameterNames: List<String> = emptyList(),
    val bodyKind: String = DashboardReplayRequestPolicy.BODY_NONE,
    val bodyFieldNames: List<String> = emptyList()
)

/** Full URL query values and POST bodies are credentials and only belong in encrypted storage. */
data class DashboardReplayRequestSecret(
    val requestUrl: String,
    val requestBody: String = ""
) {
    override fun toString(): String = "DashboardReplayRequestSecret([已脱敏])"
}

data class DashboardRequestRecipe(
    val pagePurpose: String,
    val dashboardUrl: String,
    val origin: String,
    val endpoints: List<String>,
    val metrics: List<DashboardMetricRecipe>,
    val createdAt: Long,
    val lastSuccessAt: Long,
    val boundInstanceId: String? = null,
    val requestSpecs: List<DashboardRequestSpec> = emptyList()
) {
    fun effectiveRequestSpecs(): List<DashboardRequestSpec> = requestSpecs.ifEmpty {
        endpoints.map { endpoint ->
            DashboardRequestSpec(endpoint = endpoint, method = "GET")
        }
    }

    fun isWidgetReplayEligible(): Boolean = effectiveRequestSpecs().all { spec ->
        spec.method == "GET" &&
            spec.queryParameterNames.isEmpty() &&
            spec.bodyKind == DashboardReplayRequestPolicy.BODY_NONE
    }

    fun toJson(): JSONObject = JSONObject()
        .put("version", 2)
        .put("pagePurpose", pagePurpose)
        .put("dashboardUrl", dashboardUrl)
        .put("origin", origin)
        .put("endpoints", JSONArray(endpoints))
        .put(
            "requests",
            JSONArray(effectiveRequestSpecs().map { spec ->
                JSONObject()
                    .put("endpoint", spec.endpoint)
                    .put("method", spec.method)
                    .put("queryParameterNames", JSONArray(spec.queryParameterNames))
                    .put("bodyKind", spec.bodyKind)
                    .put("bodyFieldNames", JSONArray(spec.bodyFieldNames))
            })
        )
        .put(
            "metrics",
            JSONArray(metrics.map { metric ->
                JSONObject()
                    .put("type", metric.type)
                    .put("label", metric.label)
                    .put("endpoint", metric.endpoint)
                    .put("jsonPath", metric.jsonPath)
                    .put("valueType", metric.valueType)
                    .put("unit", metric.unit)
            })
        )
        .put("createdAt", createdAt)
        .put("lastSuccessAt", lastSuccessAt)
        .putOpt("boundInstanceId", boundInstanceId)

    companion object {
        fun fromJson(root: JSONObject): DashboardRequestRecipe? {
            val version = root.optInt("version")
            if (version !in 1..2) return null
            val endpointArray = root.optJSONArray("endpoints") ?: return null
            val endpoints = stringList(endpointArray)
            val metricArray = root.optJSONArray("metrics") ?: return null
            val metrics = buildList {
                for (index in 0 until metricArray.length()) {
                    val metric = metricArray.optJSONObject(index) ?: continue
                    add(
                        DashboardMetricRecipe(
                            type = metric.optString("type"),
                            label = metric.optString("label"),
                            endpoint = metric.optString("endpoint"),
                            jsonPath = metric.optString("jsonPath"),
                            valueType = metric.optString("valueType"),
                            unit = metric.optString("unit")
                        )
                    )
                }
            }
            val requestSpecs = if (version >= 2) {
                val array = root.optJSONArray("requests") ?: return null
                buildList {
                    for (index in 0 until array.length()) {
                        val request = array.optJSONObject(index) ?: continue
                        add(
                            DashboardRequestSpec(
                                endpoint = request.optString("endpoint"),
                                method = request.optString("method").uppercase(),
                                queryParameterNames = stringList(
                                    request.optJSONArray("queryParameterNames") ?: JSONArray()
                                ),
                                bodyKind = request.optString(
                                    "bodyKind",
                                    DashboardReplayRequestPolicy.BODY_NONE
                                ),
                                bodyFieldNames = stringList(
                                    request.optJSONArray("bodyFieldNames") ?: JSONArray()
                                )
                            )
                        )
                    }
                }
            } else {
                emptyList()
            }
            return DashboardRequestRecipe(
                pagePurpose = root.optString("pagePurpose"),
                dashboardUrl = root.optString("dashboardUrl"),
                origin = root.optString("origin"),
                endpoints = endpoints,
                metrics = metrics,
                createdAt = root.optLong("createdAt"),
                lastSuccessAt = root.optLong("lastSuccessAt"),
                boundInstanceId = root.optString("boundInstanceId", "")
                    .trim()
                    .takeIf { it.isNotBlank() },
                requestSpecs = requestSpecs
            ).takeIf { DashboardRecipeRules.validate(it) == null }
        }

        private fun stringList(array: JSONArray): List<String> = buildList {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
}

data class DashboardRecipeDraft(
    val recipe: DashboardRequestRecipe? = null,
    val error: String? = null,
    val replayHeadersByEndpoint: Map<String, Map<String, String>> = emptyMap(),
    val replayRequestsByEndpoint: Map<String, DashboardReplayRequestSecret> = emptyMap()
)

object DashboardRecipeRules {
    private const val MAX_ENDPOINTS = 4
    private const val MAX_METRICS = 40
    private const val MAX_FIELD_NAMES = 40
    private val allowedMetricTypes = setOf(
        "balance", "usage", "quota", "tokens", "requests",
        "subscription", "reset_time", "other"
    )
    private val allowedValueTypes = setOf("number", "string", "boolean", "object", "array")
    private val allowedBodyKinds = setOf(
        DashboardReplayRequestPolicy.BODY_NONE,
        DashboardReplayRequestPolicy.BODY_JSON,
        DashboardReplayRequestPolicy.BODY_FORM
    )

    fun create(
        pagePurpose: String,
        dashboardUrl: String,
        lockedOrigin: String,
        metrics: List<DashboardMetricRecipe>,
        candidates: List<CapturedResponseCandidate>,
        now: Long
    ): DashboardRecipeDraft {
        if (metrics.isEmpty()) return DashboardRecipeDraft(error = "没有通过本机核对的字段，不能保存配方")
        val endpoints = metrics.map { it.endpoint }.distinct()
        if (endpoints.size > MAX_ENDPOINTS) {
            return DashboardRecipeDraft(error = "当前最多支持 $MAX_ENDPOINTS 个数据接口")
        }

        val selectedByEndpoint = linkedMapOf<String, CapturedResponseCandidate>()
        for (endpoint in endpoints) {
            if (DashboardDiscoveryRules.originOf(endpoint) != lockedOrigin) {
                return DashboardRecipeDraft(error = "P5-U1 只能保存与当前仪表盘同站点的数据接口")
            }
            val selected = candidates
                .asSequence()
                .filter { it.endpoint == endpoint && it.status in 200..299 }
                .maxByOrNull { it.captureIndex }
                ?: return DashboardRecipeDraft(error = "数据接口没有可重复核对的成功请求")
            if (selected.method !in setOf("GET", "POST")) {
                return DashboardRecipeDraft(error = "该接口使用 ${selected.method}；需要网页登录辅助刷新")
            }
            selected.replayBlockReason?.let { return DashboardRecipeDraft(error = it) }
            val requestUrl = DashboardReplayRequestPolicy.normalizeRequestUrl(
                selected.method,
                selected.requestUrl,
                lockedOrigin
            ) ?: return DashboardRecipeDraft(error = "数据请求地址不能安全直接重放；需要网页登录辅助刷新")
            val queryNames = DashboardReplayRequestPolicy.queryParameterNames(requestUrl)
                ?: return DashboardRecipeDraft(error = "查询参数过多或名称无效；需要网页登录辅助刷新")
            if (selected.method == "POST" && selected.requestBodyKind !in setOf(
                    DashboardReplayRequestPolicy.BODY_JSON,
                    DashboardReplayRequestPolicy.BODY_FORM
                )
            ) {
                return DashboardRecipeDraft(error = "POST 请求体不是有限 JSON 或 Form；需要网页登录辅助刷新")
            }
            selectedByEndpoint[endpoint] = selected.copy(requestUrl = requestUrl)
        }

        val requestSpecs = endpoints.map { endpoint ->
            val selected = requireNotNull(selectedByEndpoint[endpoint])
            DashboardRequestSpec(
                endpoint = endpoint,
                method = selected.method,
                queryParameterNames = DashboardReplayRequestPolicy.queryParameterNames(selected.requestUrl).orEmpty(),
                bodyKind = selected.requestBodyKind,
                bodyFieldNames = selected.requestBodyFieldNames
            )
        }
        val replayHeadersByEndpoint = endpoints.associateWith { endpoint ->
            DashboardReplayHeaderPolicy.sanitize(selectedByEndpoint[endpoint]?.replayHeaders.orEmpty())
        }
        val replayRequestsByEndpoint = endpoints.associateWith { endpoint ->
            val selected = requireNotNull(selectedByEndpoint[endpoint])
            DashboardReplayRequestSecret(
                requestUrl = selected.requestUrl,
                requestBody = selected.requestBody
            )
        }
        val recipe = DashboardRequestRecipe(
            pagePurpose = pagePurpose.trim().take(200).ifBlank { "网页仪表盘" },
            dashboardUrl = DashboardDiscoveryRules.withoutQuery(dashboardUrl, lockedOrigin),
            origin = lockedOrigin,
            endpoints = endpoints,
            metrics = metrics,
            createdAt = now,
            lastSuccessAt = 0L,
            requestSpecs = requestSpecs
        )
        return validate(recipe)?.let { DashboardRecipeDraft(error = it) }
            ?: DashboardRecipeDraft(
                recipe = recipe,
                replayHeadersByEndpoint = replayHeadersByEndpoint,
                replayRequestsByEndpoint = replayRequestsByEndpoint
            )
    }

    fun validate(recipe: DashboardRequestRecipe): String? {
        if (recipe.boundInstanceId?.let { !SAFE_INSTANCE_ID.matches(it) } == true) {
            return "请求配方绑定的模型实例无效"
        }
        if (DashboardDiscoveryRules.originOf(recipe.dashboardUrl) != recipe.origin) {
            return "仪表盘地址与保存站点不一致"
        }
        if (recipe.endpoints.isEmpty() || recipe.endpoints.size > MAX_ENDPOINTS || recipe.endpoints.distinct().size != recipe.endpoints.size) {
            return "请求配方的数据接口数量无效"
        }
        if (recipe.metrics.isEmpty() || recipe.metrics.size > MAX_METRICS) {
            return "请求配方的字段数量无效"
        }
        val specs = recipe.effectiveRequestSpecs()
        if (specs.size != recipe.endpoints.size || specs.map { it.endpoint }.toSet() != recipe.endpoints.toSet()) {
            return "请求配方的请求规则与数据接口不一致"
        }
        for (spec in specs) {
            if (!DashboardDiscoveryRules.isHttpsUrl(spec.endpoint) ||
                DashboardDiscoveryRules.originOf(spec.endpoint) != recipe.origin ||
                DashboardDiscoveryRules.hasQueryOrFragment(spec.endpoint)
            ) {
                return "请求配方包含不安全的数据接口"
            }
            if (spec.method !in setOf("GET", "POST") || spec.bodyKind !in allowedBodyKinds) {
                return "请求配方包含不支持的请求方法或请求体"
            }
            if (spec.method == "GET" && spec.bodyKind != DashboardReplayRequestPolicy.BODY_NONE) {
                return "GET 请求配方不能携带请求体"
            }
            if (spec.method == "POST" && spec.bodyKind !in setOf(
                    DashboardReplayRequestPolicy.BODY_JSON,
                    DashboardReplayRequestPolicy.BODY_FORM
                )
            ) {
                return "POST 请求配方必须使用 JSON 或 Form 请求体"
            }
            if (!validFieldNames(spec.queryParameterNames) || !validFieldNames(spec.bodyFieldNames)) {
                return "请求配方包含无效的参数字段名"
            }
        }
        for (metric in recipe.metrics) {
            if (metric.type !in allowedMetricTypes || metric.valueType !in allowedValueTypes) {
                return "请求配方包含不支持的字段类型"
            }
            if (metric.endpoint !in recipe.endpoints || DashboardJsonPathValidator.canonicalize(metric.jsonPath) == null) {
                return "请求配方包含无效字段路径"
            }
        }
        return null
    }

    private fun validFieldNames(names: List<String>): Boolean =
        names.size <= MAX_FIELD_NAMES && names.all { name ->
            name.isNotBlank() && name.length <= 128 && name.none { it == '\r' || it == '\n' || it == '\u0000' }
        }

    private val SAFE_INSTANCE_ID = Regex("[A-Za-z0-9_-]{1,128}")
}
