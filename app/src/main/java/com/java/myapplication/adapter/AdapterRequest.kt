package com.java.myapplication.adapter

import com.java.myapplication.adapter.auth.BackgroundAuthType

/**
 * Adapter 的统一输入。
 *
 * 模型 API Key 与网页登录获得的后台凭据必须分开传递，
 * 禁止继续把 Cookie / Bearer Token 混入名为 apiKey 的参数。
 *
 * instanceId 标识当前模型实例／槽位，只用于本地缓存隔离，绝不发送给平台。
 */
data class AdapterRequest(
    val apiBase: String,
    val modelApiKey: String,
    val modelName: String? = null,
    val backgroundAuthType: BackgroundAuthType = BackgroundAuthType.NONE,
    val backgroundCredential: String = "",
    val instanceId: String = ""
) {
    val hasBackgroundCredential: Boolean
        get() = backgroundAuthType != BackgroundAuthType.NONE && backgroundCredential.isNotBlank()
}
