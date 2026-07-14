package com.java.myapplication.adapter.auth

import java.net.HttpURLConnection

/**
 * 后台授权策略接口
 * 负责将授权信息应用到 HTTP 请求
 */
interface BackgroundAuthStrategy {

    /**
     * 将授权信息应用到 HTTP 连接
     * @param connection HTTP 连接
     * @param authValue 授权值（如 auth_token 或 Cookie 字符串）
     */
    fun apply(connection: HttpURLConnection, authValue: String)
}
