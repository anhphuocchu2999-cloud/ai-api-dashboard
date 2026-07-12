package com.java.myapplication.adapter.auth

import java.net.HttpURLConnection

/**
 * Cookie 授权策略
 * 适用于小米 MiMo 等网页登录 Cookie
 * 不解析 Cookie 内容，按原始字符串发送
 */
class CookieAuthStrategy : BackgroundAuthStrategy {

    override fun apply(connection: HttpURLConnection, authValue: String) {
        connection.setRequestProperty(
            "Cookie",
            authValue
        )
    }
}
