package com.java.myapplication.adapter.capability

import com.java.myapplication.adapter.auth.BackgroundAuthType

/**
 * 一个 Adapter 当前真实支持的认证与数据能力描述。
 *
 * 这里只描述已经落地的能力，不做接口探测，也不保存任何凭据。
 */
data class ProviderCapabilityProfile(
    val modelApiKeyRequired: Boolean = true,
    val backgroundAuthType: BackgroundAuthType = BackgroundAuthType.NONE,
    val sources: Set<DataSourceType>,
    val capabilities: Set<DataCapability>
)
