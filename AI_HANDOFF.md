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

`Stage 8B：四张配置卡统一连接方式面板` 正在纠偏，同时补齐 DeepSeek 可真实实现的近期余额变化数据。

- 当前开发分支：`feature/stage-8a-model-instances`
- Stage 8A-4 已验收基线：`f5a9507201f1bf836f2e03fbae2dd297eb2a3409`
- Stage 8B 第一版：`514967ca0fb3919c88653e0dd3f12bc39fd8ea40`
- Stage 8B 服务切换纠偏收口：`92e9c571ebfab3aa8954f84688fcefc7be7053e2`
- DeepSeek 余额变化快照：`9319f628c62ce1354717d22e928abff05147ab17`
- 当前状态：代码已推送，尚未声称编译、安装或真机通过
- 下一步：只执行拉取、一次 `assembleDebug`、一次覆盖安装和真机验收

## 主线目标

四个 Widget / 配置窗口必须是独立模型实例，不再天生等于 Kimi、MiMo、DeepSeek、OpenAI。每个窗口可以选择当前已真实接入的服务，并按该服务真实能力开放：

1. API
2. 网页授权
3. Billing
4. 余额、近期消费/用量、请求次数、Token 等数据能力

未经真实接口验证的能力不得显示为已接入，不得用 `0` 或估算值冒充官方数据。

## Stage 8B 当前实现

### 服务类型选择

四张配置卡都可以选择：

- Kimi / NewAPI
- MiMo
- DeepSeek 官方
- 爱黄牛

切换服务后更新默认 API Base、清空旧模型名并要求重新测试。Adapter、网页登录和 Billing 能力随所选服务重新计算。

### 网页授权

- MiMo：已验证 Cookie 网页授权，可放到任意窗口；
- 爱黄牛：已验证 localStorage Bearer Token，可放到任意窗口；
- DeepSeek、Kimi：没有经过验证的网页登录数据接口，不伪造入口；
- 网页凭据保存到当前目标卡片，不再固定写入历史平台槽位。

### Billing

- Kimi / NewAPI：真实 Subscription / Usage Billing 已接入；
- MiMo、DeepSeek、爱黄牛：当前无已验证 Billing 接口；
- Billing 是数据来源，不是第三份认证密码。

## DeepSeek 数据现状

DeepSeek 官方公开账户接口当前使用：

- `GET /user/balance`
- 可得到：账户是否可调用、币种、总余额、赠送余额、充值余额

本轮新增“余额变化快照”：

- 第一次成功刷新只建立基准，显示“余额变化统计中…”；
- 后续余额下降显示“过去 X 余额净减少”；
- 余额上升显示“过去 X 余额增加”，不误报为负消费；
- API Key 只用于 SHA-256 指纹区分账户，不保存原文、不写日志；
- 该数据是两次成功余额快照之间的净变化，不是 DeepSeek 官方消费明细；充值、赠送和退款也可能影响余额。

当前仍未实现：

- DeepSeek 官方历史消费明细；
- 全账户按日/按模型 Token 统计；
- DeepSeek 网页后台 Usage 私有接口。

这些能力必须通过真实网页登录与接口摸排确认后再接入。

## 明确未修改

- 未修改 Widget XML、卡片数量和布局顺序；
- 未新增配置仓库、schema 或第二套授权体系；
- 未清除 API Key、Cookie、Bearer Token、旧配置或 Widget 缓存；
- 未合并到 `main`。

## 本轮验收重点

1. App 能正常启动，四张卡服务类型选择仍可使用；
2. Kimi、MiMo、DeepSeek、爱黄牛原数据不回归；
3. DeepSeek 第一次成功刷新显示“余额变化统计中…”；
4. 第二次刷新显示余额无变化、净减少或增加中的一种；
5. DeepSeek 余额、赠送余额、充值余额仍正常；
6. 不把余额变化描述成官方 Billing 或官方消费明细；
7. 断网快速兜底仍正常；
8. 无崩溃、空白、串卡或凭据丢失。

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
