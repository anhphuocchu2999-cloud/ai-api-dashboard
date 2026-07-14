package com.java.myapplication.adapter.auth

/**
 * 后台授权类型枚举
 * 用于区分不同平台的后台余额/用量查询鉴权方式
 */
enum class BackgroundAuthType {
    /** 不需要后台授权 */
    NONE,

    /** Bearer Token 授权（如 aihuangniu 登录 auth_token） */
    BEARER_TOKEN,

    /** Cookie 授权（如小米 MiMo 网页登录 Cookie） */
    COOKIE
}
