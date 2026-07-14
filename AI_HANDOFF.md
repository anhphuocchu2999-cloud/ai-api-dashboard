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

`Stage 8B：四张配置卡统一展示 API、网页授权、Billing` 已完成云端代码，等待一次本地编译、覆盖安装和用户真机验收。

- 当前开发分支：`feature/stage-8a-model-instances`
- Stage 8A-4 已验收基线：`f5a9507201f1bf836f2e03fbae2dd297eb2a3409`
- Stage 8B 业务提交：`514967ca0fb3919c88653e0dd3f12bc39fd8ea40`
- 当前状态：代码已推送，尚未声称编译或真机通过
- 下一步：只执行拉取、一次 `assembleDebug`、一次覆盖安装和真机验收

## 当前进度

- Stage 8A-1：方案摸排与文档确认——完成
- Stage 8A-2 / 2B / 2C：ModelInstance 与仓库迁移加固——完成
- Stage 8A-3：授权与持久化缓存 Key 迁移到 instanceId——完成并验收
- Stage 8A-3F：断网刷新快速兜底——完成并验收
- Stage 8A-4：固定窗口绑定稳定 instanceId、按 serviceType 路由——完成并验收
- Stage 8B：统一连接方式面板——云端代码完成，待验收

## Stage 8B 实现结果

四张配置卡现在固定显示三个区域，不再把能力藏在“高级设置”中：

1. `API`
2. `网页授权`
3. `Billing`

### API

- 四张卡都保留 API Base、API Key、模型名称和测试连接；
- 单模型自动选择，多模型继续弹窗选择；
- 显示未配置、待测试、正在测试、已连接、连接失败状态；
- API Key 使用密码样式，不在日志中输出。

### 网页授权

- MiMo：显示 Cookie 网页授权，保留“连接账户 / 重新连接”和手动 Cookie 备用输入；
- 爱黄牛：显示 Bearer Token 网页授权，保留网页登录自动读取 `auth_token` 和手动 Token 备用输入；
- Kimi、DeepSeek：明确显示“当前服务暂未接入”，不再允许任意选择无效 Cookie / Bearer Token；
- 继续复用 `BackgroundAuthRepository`、`WebAuthProfileRegistry` 和 `WebAuthActivity`；
- 返回配置页时自动重新读取授权状态。

### Billing

- Kimi / NewAPI：显示“已接入”，说明自动复用模型 API Key，继续使用真实 Subscription / Usage Billing；
- MiMo、DeepSeek、爱黄牛：显示“当前服务暂未接入”；
- Billing 明确作为数据来源，不描述为第三份认证凭据；
- 不伪造额度、套餐、余额或用量。

### 页面结构

- 移除所有卡片都能随意选择 NONE / COOKIE / BEARER_TOKEN 的误导式单选项；
- 连接方式由当前 Adapter 的 `ProviderCapabilityProfile` 与真实 `WebAuthProfile` 决定；
- App 首页版本显示更新为 `Stage: 8B`；
- 配置读取继续经过 `ConfigRepository`，保存后同步刷新 Widget。

## Stage 8B 明确未修改

- 未修改 Widget XML、四卡数量、布局顺序和视觉外观；
- 未新增或删除模型实例；
- 未修改任何平台 HTTP 协议或 JSON 解析；
- 未新增配置仓库、schema、缓存体系或第二套授权体系；
- 未清除旧 API Key、Cookie、Bearer Token 或 Widget 缓存；
- 未合并到 `main`。

## 验收重点

安装后必须确认：

1. 四张配置卡都显示 API、网页授权、Billing；
2. Kimi：API 和 Billing 可用，网页授权显示未接入；
3. MiMo：API 和 Cookie 网页授权可用，Billing 显示未接入；
4. DeepSeek：仅 API 可用；
5. 爱黄牛：API 和 Bearer Token 网页授权可用；
6. MiMo、爱黄牛无需重新登录；
7. 不支持的入口无法误操作；
8. 四个平台原数据、Widget、缓存和断网兜底不回归；
9. App 无崩溃、空白或配置串卡。

## 应用信息

- Android 包名：`com.java.myapplication.dev`
- Debug 构建：`./gradlew assembleDebug`
- APK：`app/build/outputs/apk/debug/app-debug.apk`
- 覆盖安装：`pm install -r`

严禁卸载、`pm clear` 或清除应用数据，因为会破坏现有配置和网页登录授权。

## 严禁操作

- 不得修改 `main`；
- 不得未经用户确认合并分支；
- 不得卸载 App、`pm clear` 或清数据；
- 不得打印或暴露 API Key、Cookie、Bearer Token；
- 不得绕过 `AdapterFactory`；
- 不得让 Provider 解析平台协议或授权内容；
- 每次只推进一个可独立验收的目标；
- OperitAI 仅在用户明确授权时执行拉取、编译、覆盖安装和启动，不参与代码修改、文档修改、提交或推送。
