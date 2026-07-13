package com.java.myapplication.adapter.capability

/** Provider 已真实实现的数据能力。 */
enum class DataCapability(val displayName: String) {
    MODELS("模型"),
    BALANCE("余额"),
    QUOTA("额度"),
    USAGE("用量"),
    REQUESTS("请求次数"),
    TOKENS("Token"),
    PROFILE("账户信息"),
    SUBSCRIPTION("套餐")
}
