# AI API Dashboard｜AI 交接状态

> 最近更新：2026-07-14
> 本文件只保存当前状态快照；历史过程以 `DEVELOPMENT_LOG.md` 和 GitHub 提交为准。

## 强制阅读顺序

1. `AGENTS.md`
2. `PROJECT.md`
3. `AI_HANDOFF.md`
4. `DEVELOPMENT_LOG.md` 最近阶段
5. 当前任务直接相关源码

不得只依据聊天摘要直接改代码。

## 当前阶段

`Stage 8B：四张配置卡统一连接方式面板` 尚未最终验收。当前限定子任务为：

> MiMo 网页授权后的 Billing / Usage 汇总正式接入。

- 当前开发分支：`feature/stage-8a-model-instances`
- Stage 8A-4 已验收基线：`f5a9507201f1bf836f2e03fbae2dd297eb2a3409`
- DeepSeek 网页账单接入 HEAD：`0b9d658eb27cc2dcb758edd98541a2f9a48423c1`
- MiMo Billing 业务提交：`53aee213f4a906e6dff1a10cf8b8917e6681fb97`
- 当前状态：云端代码已推送，尚未声称编译、安装或真机数据显示成功
- 下一步：一次 `assembleDebug`、一次覆盖安装、用户真机刷新 MiMo 验证

## 主线目标

四个 Widget / 配置窗口必须是独立模型实例。每个窗口可以选择服务，并按服务真实能力开放：

1. API
2. 网页授权
3. Billing
4. 余额、近期消费/用量、请求次数、Token 等数据能力

未经真实接口验证的能力不得伪造。

## MiMo 已验证网页接口

用户在 MiMo 开放平台 Billing / Usage 页面完成只读接口摸排，已验证：

### 余额汇总

`GET https://platform.xiaomimimo.com/api/v1/balance`

返回字段包括：

- `balance`
- `frozenBalance`
- `currency`
- `overdraftLimit`
- `remainingOverdraftLimit`
- `giftBalance`
- `cashBalance`

### 用量汇总

`GET https://platform.xiaomimimo.com/api/v1/usage`

返回字段包括：

- `tokenUsage.inputToken`
- `tokenUsage.outputToken`
- `tokenUsage.cacheToken`
- `tokenUsage.totalToken`
- `tokenUsage.inputAudioDuration`
- `accountRateLimit.tpm`
- `accountRateLimit.rpm`
- `accountRateLimit.queryTpm`
- `accountRateLimit.concurrency`
- `costUsage.totalCost`
- `costUsage.currentMonthCost`
- `pluginUsage.totalRequestCount`
- `pluginUsage.webSearchRequestCount`

### 已发现但本轮未接入

`POST /api/v1/usage/detail/list`

该接口包含按日期、模型和 API Key 的消费与 Token 明细，但请求体、分页和时间范围尚未单独验证。本轮不扩大到明细趋势。

## 本轮正式实现

### MiMoAdapter

- 继续使用网页登录 Cookie 获取余额；
- 余额成功后，同 Host 间隔至少 500ms，再请求 `/api/v1/usage`；
- Widget 主指标继续显示余额；
- 近期轮播显示本月消费、累计 Token；
- 辅助轮播显示累计消费、累计请求、Web 搜索次数、输入／输出／缓存 Token、RPM／TPM；
- 用量接口失败时保留正常余额，不用 Billing 失败覆盖真实余额；
- Cookie 失效时提示“网页登录需重连”；
- 不记录或输出 Cookie、API Key、响应原文。

能力声明更新为：

- 来源：`API + WEB_AUTH + BILLING`
- 后台授权：`COOKIE`
- 数据：`MODELS + BALANCE + USAGE + REQUESTS + TOKENS`

## DeepSeek 当前已接入

- API Key：模型列表、总余额、赠送余额、充值余额；
- 网页 Cookie：`get_user_summary`；
- 近期轮播：本月消费、本月 Token；
- 辅助轮播：充值余额、赠送余额、累计消费、预计可用 Token；
- 网页失败时保留 API 余额。

## 已保留能力

- Kimi / NewAPI API 与 Billing；
- MiMo API、Cookie 网页授权、余额与 Billing / Usage；
- DeepSeek API、Cookie 网页授权、余额与网页账单；
- 爱黄牛 API、Bearer 网页授权、余额／用量／请求／Token；
- 四张 Widget 原布局、缓存、断网快速兜底；
- 旧配置、授权和最近成功数据不删除；
- `main` 未合并。

## 本轮未修改

- 未修改 Widget XML、四卡数量和布局；
- 未接入 MiMo `usage/detail/list` 明细；
- 未保存网页响应原文；
- 未输出 API Key、Cookie、Bearer Token；
- 未卸载、未清数据；
- 未合并到 `main`。

## 真机验收

安装后确认：

1. MiMo 余额继续正常；
2. 已有 MiMo 网页登录状态不丢失；
3. 刷新后近期轮播出现“本月消费”和“累计 Token”；
4. 辅助轮播可见累计消费、累计请求等真实汇总；
5. 状态显示“网页账单已同步”；
6. Cookie 失效时提示“网页登录需重连”，历史成功缓存仍保留；
7. Kimi、DeepSeek、爱黄牛和断网兜底不回归；
8. App 无崩溃、空白、串卡或凭据泄露。

## 应用信息

- Android 包名：`com.java.myapplication.dev`
- Debug 构建：`./gradlew assembleDebug`
- APK：`app/build/outputs/apk/debug/app-debug.apk`
- 覆盖安装：`pm install -r`

严禁卸载、`pm clear` 或清除应用数据。

## 严禁操作

- 不得修改 `main`；
- 不得未经用户确认合并分支；
- 不得卸载 App、`pm clear` 或清数据；
- 不得打印或暴露 API Key、Cookie、Bearer Token；
- 不得绕过 `AdapterFactory`；
- 不得让 Provider 解析平台协议或授权内容；
- 每次只推进一个可独立验收的目标；
- OperitAI 仅执行拉取、编译、覆盖安装和启动，不参与代码修改、文档修改、提交或推送。
