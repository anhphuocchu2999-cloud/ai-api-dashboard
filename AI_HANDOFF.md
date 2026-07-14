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

> DeepSeek 网页授权与真实账户汇总数据正式接入。

- 当前开发分支：`feature/stage-8a-model-instances`
- Stage 8A-4 已验收基线：`f5a9507201f1bf836f2e03fbae2dd297eb2a3409`
- Stage 8B 第二轮纠偏基线：`92e9c571ebfab3aa8954f84688fcefc7be7053e2`
- DeepSeek 余额变化快照：`9319f628c62ce1354717d22e928abff05147ab17`
- DeepSeek 网页接口摸排完成：`cabca26d62e282b6556c25e7b64ca079ff070c86`
- DeepSeek 正式网页账单业务提交：`70a918188551f3743c3e6563622f69198d9e294c`
- DeepSeek 正式网页登录配置：`d1ce70620e1a01dba2f06b840279e1b0d7cc7806`、`ad67c1ed493679e3dcb8e550f98e567c2a7d1561`、`f9116f507b3d375ce71a854216d455986ee38f71`
- 当前状态：云端代码已推送，尚未声称编译、安装或真机数据成功
- 下一步：一次 `assembleDebug`、一次覆盖安装、用户真机刷新 DeepSeek 验证

## 主线目标

四个 Widget / 配置窗口必须是独立模型实例。每个窗口可以选择服务，并按服务真实能力开放：

1. API
2. 网页授权
3. Billing
4. 余额、近期消费/用量、请求次数、Token 等数据能力

未经真实接口验证的能力不得伪造。

## DeepSeek 已验证接口

### 官方 API Key

- `GET /user/balance`
- 模型列表
- 账户是否可调用
- 总余额
- 赠送余额
- 充值余额

### 网页账户 Cookie

用户本人登录并完成只读接口摸排后，已验证：

- `GET https://platform.deepseek.com/api/v0/users/get_user_summary`
- HTTP 200
- 返回 `normal_wallets`
- 返回 `bonus_wallets`
- 返回 `monthly_costs`
- 返回 `monthly_token_usage`
- 返回 `total_costs`
- 返回 `total_available_token_estimation`

另已发现但本轮未正式解析：

- `/api/v0/usage/by_api_key/cost`
- `/api/v0/usage/by_api_key/amount`

按 API Key、模型、时间桶的明细留待后续独立任务，不在本轮扩大范围。

## 本轮正式实现

### DeepSeekOfficialAdapter

- `fetchData(AdapterRequest)` 先读取官方 API 余额；
- 当前卡片存在有效 Cookie 时，再请求 `get_user_summary`；
- Widget 主指标继续显示官方余额；
- 近期轮播显示本月消费、本月 Token；
- 辅助轮播显示充值余额、赠送余额、累计消费、余额预计可用 Token；
- 网页接口失效或网络异常时保留 API 余额，不用网页失败覆盖真实余额；
- Cookie、API Key、响应原文均不写日志。

能力声明更新为：

- 来源：`API + WEB_AUTH + BILLING`
- 后台授权：`COOKIE`
- 数据：`MODELS + BALANCE + USAGE + TOKENS`

### DeepSeek 网页登录

- `deepseek-probe` 已升级为正式 `deepseek` Profile；
- 打开 `https://platform.deepseek.com/usage`；
- Cookie 名称不固定，因此不凭“存在任意 Cookie”判断成功；
- 使用 `get_user_summary` 验证 Cookie，只有真实返回 `code=0`、`biz_code=0` 和 `biz_data` 才保存；
- 已有摸排阶段保存的 DeepSeek Cookie继续兼容，不要求主动清除或重新登录。

## 保留的备用补充

未登录网页账户时，仍可通过两次成功 `/user/balance` 快照展示：

- 余额净减少
- 余额增加
- 余额无变化

该数据明确不是官方消费明细，充值、赠送、退款也可能影响余额。网页登录汇总成功后，真实月度消费和月度 Token 优先展示。

## 本轮未修改

- 未修改 Widget XML、四卡数量和布局；
- 未接入按 API Key／模型／时间桶的明细接口；
- 未保存网页响应原文；
- 未输出 API Key、Cookie、Token；
- 未卸载、未清数据；
- 未合并到 `main`。

## 真机验收

安装后确认：

1. DeepSeek 卡的 API 余额仍正常；
2. 已有网页登录状态不丢失；
3. 刷新 Widget 后近期轮播出现“本月消费”和“本月 Token”；
4. 辅助轮播可见充值余额、赠送余额、累计消费等真实汇总；
5. 底部状态显示“网页账单已同步”；
6. Cookie 失效时仍显示 API 余额，并提示“网页登录需重连”；
7. Kimi、MiMo、爱黄牛、断网缓存不回归；
8. App 无崩溃、空白或凭据泄露。

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
