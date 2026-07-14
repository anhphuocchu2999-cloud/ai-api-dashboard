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

`Stage 8B：四张配置卡统一连接方式面板` 尚未验收完成。当前正在执行一个限定范围的子任务：

> DeepSeek 网页登录后的只读接口结构摸排。

- 当前开发分支：`feature/stage-8a-model-instances`
- Stage 8A-4 已验收基线：`f5a9507201f1bf836f2e03fbae2dd297eb2a3409`
- Stage 8B 第二轮纠偏基线：`92e9c571ebfab3aa8954f84688fcefc7be7053e2`
- DeepSeek 余额变化快照：`9319f628c62ce1354717d22e928abff05147ab17`
- DeepSeek 网页摸排当前业务 HEAD：`8edd7c9bc2fb77f49859866b6c8e3b2336c43899`
- 当前状态：云端代码已推送，尚未声称编译、登录或真机摸排成功
- 下一步：拉取、一次 `assembleDebug`、一次覆盖安装；用户本人登录 DeepSeek 并查看捕获结果

## 主线目标

四个 Widget / 配置窗口必须是独立模型实例。每个窗口可以选择服务，并按服务真实能力开放：

1. API
2. 网页授权
3. Billing
4. 余额、近期消费/用量、请求次数、Token 等数据能力

未经真实接口验证的能力必须明确标注“当前未接入”，不得伪造数据。

## 已验收基础

- Stage 8A 模型实例、instanceId、授权缓存迁移和 serviceType 路由已完成；
- 四张 Widget 卡保留原布局；
- MiMo Cookie 登录已验证；
- 爱黄牛 localStorage Bearer Token 已验证；
- Kimi / NewAPI Billing 已验证；
- DeepSeek 官方 `/user/balance` 已验证；
- 断网快速恢复最近成功数据已验证。

## Stage 8B 当前页面

- 四张配置卡可选择 Kimi / NewAPI、MiMo、DeepSeek 官方、爱黄牛；
- 每张卡显示 API、网页授权、Billing 和可获取数据；
- 不支持的能力显示“当前未接入”，不再显示“卡片锁定”；
- Stage 8B 尚未由用户最终验收，页面易用性仍需要后续收口。

## DeepSeek 当前真实能力

### 官方 API

已接入：

- 模型列表；
- 账户是否可调用；
- 总余额；
- 赠送余额；
- 充值余额。

本地补充：

- 两次成功刷新之间的“余额净减少 / 余额增加 / 无变化”；
- 该数据明确不是官方消费明细，充值、赠送、退款也可能影响余额。

### 网页摸排

DeepSeek 新增 `deepseek-probe` Profile：

- 打开 `https://platform.deepseek.com/usage`；
- 用户本人完成登录；
- WebView 只读观察 fetch / XMLHttpRequest；
- 同时记录非静态网络请求作为兜底；
- 仅保存去掉 query/fragment 的 endpoint、请求方法、HTTP 状态码、JSON 字段路径和字段类型；
- 不保存响应值、请求头、Cookie、Token 或 URL 查询参数；
- 页面底部提供“查看捕获结果”和“完成并返回”；
- 有可识别 JSON 响应且存在会话 Cookie 时，才把 Cookie 保存到当前卡片；
- 摸排结果在完成真实字段验证前不得声明为正式消费数据源。

相关文件：

- `WebAuthActivity.kt`
- `webauth/DeepSeekWebProbeRepository.kt`
- `webauth/WebAuthProfile.kt`
- `webauth/WebAuthProfileRegistry.kt`
- `adapter/DeepSeekOfficialAdapter.kt`
- `res/layout/activity_web_auth.xml`

## 本轮明确未修改

- 未修改 Widget XML、四卡数量和布局；
- 未把任何未知网页字段接入 Adapter；
- 未把余额差值冒充官方消费；
- 未保存网页响应原始内容；
- 未输出 API Key、Cookie 或 Token；
- 未卸载、未清数据；
- 未合并到 `main`。

## 本轮验收

安装后：

1. 在任意配置卡选择 DeepSeek 官方；
2. “网页授权”区域应出现可点击入口；
3. 打开后进入 DeepSeek 用量页并本人登录；
4. 登录后停留在用量页，必要时刷新页面一次；
5. 页面底部状态应从“尚未捕获”变为“已捕获 N 个接口”；
6. 点击“查看捕获结果”，截图 endpoint、状态码和字段结构；
7. 不得截图或发送账号密码、Cookie、Token、API Key 或账户数值；
8. 点击“完成并返回”。

只有拿到字段结构后，才能决定 DeepSeek 网页端可正式接入今日消费、历史趋势、Token、请求次数中的哪些能力。

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
