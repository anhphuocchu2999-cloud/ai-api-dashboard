package com.java.myapplication.adapter.auth

/**
 * 后台授权策略工厂
 * 根据授权类型返回对应的策略实现
 */
object BackgroundAuthFactory {

    /**
     * 根据授权类型获取对应的策略
     * @param type 授权类型
     * @return 对应的策略实现，NONE 返回 null
     */
    fun getStrategy(type: BackgroundAuthType): BackgroundAuthStrategy? {
        return when (type) {
            BackgroundAuthType.NONE -> null
            BackgroundAuthType.BEARER_TOKEN -> BearerTokenAuthStrategy()
            BackgroundAuthType.COOKIE -> CookieAuthStrategy()
        }
    }
}
