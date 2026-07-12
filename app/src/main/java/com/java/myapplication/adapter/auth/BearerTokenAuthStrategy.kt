package com.java.myapplication.adapter.auth

import java.net.HttpURLConnection

/**
 * Bearer Token 授权策略
 * 适用于 aihuangniu 等后台登录 Token
 */
class BearerTokenAuthStrategy : BackgroundAuthStrategy {

    override fun apply(connection: HttpURLConnection, authValue: String) {
        connection.setRequestProperty(
            "Authorization",
            "Bearer ${authValue.trim()}"
        )
    }
}
