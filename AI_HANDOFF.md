# AI API Dashboard｜AI 交接状态

> 最近更新：2026-07-15
> 本文件只保存当前状态快照；历史过程以 `DEVELOPMENT_LOG.md` 和 GitHub 提交为准。

## 强制阅读顺序

1. `AGENTS.md`
2. `PROJECT.md`
3. `PROJECT_STAGE_8B_R.md`
4. `AI_HANDOFF.md`
5. 当前任务直接相关源码

## 当前阶段

`Stage 8B-R：API 基础连接 + 平台账户增强`

- 开发分支：`feature/stage-8b-simple-connection-flow`
- 起点：`ed987b6e7c009dad3d2eacaaa258ae532a0165bd`
- 最终交互业务提交：`3504d169362b1050ffe67ce2b13f9fddb81b43ad`
- 最终规范提交：`828d106848c6d18b814009b28898f551de06d786`
- 当前状态：云端代码与规范已推送，尚未声称编译、安装或真机验收成功
- 下一步：OperitAI 仅拉取、编译一次、覆盖安装一次并启动；随后用户本人验收

## 最终交互结构

### 首页

首页只显示四张简洁槽位卡：

- 槽位编号；
- 模型名称或未配置；
- API／平台账户连接状态；
- 显示开关；
- 设置／编辑按钮。

首页不显示服务商选择、API 输入框、Cookie、Token、Billing 或技术协议。

### 单槽位编辑页

点击设置后进入独立页面，一次只配置一个槽位。

页面同时显示两个互补区域，不再二选一切换：

#### API 连接

始终显示：

- API 地址；
- API Key；
- 检测并选择模型。

服务类型和技术协议根据 API 地址在后台识别。用户不需要先选择 Kimi、MiMo、DeepSeek、爱黄牛或协议类型。

#### 平台账户（可选）

页面明确说明：

> 想获得更完整的余额、消费和用量信息，可以再登录平台账户。API 连接不会受到影响。

系统根据 API 地址自动匹配已验证的网页登录方案：

- MiMo；
- DeepSeek；
- 爱黄牛。

匹配后只显示账户是否已连接、登录／重新登录、断开账户和可补充的数据摘要。没有匹配方案时，说明 API 仍可正常使用，不显示无效按钮。

## 保留的真实能力

- Kimi / NewAPI：API 模型发现与已验证 Billing；
- MiMo：Cookie 登录、余额、本月消费、Token、请求次数；
- DeepSeek：API 余额、Cookie 登录、本月消费、累计消费、Token；
- 爱黄牛：API、网页登录 Bearer、余额／用量／请求／Token；
- 四张 Widget 原布局；
- 每实例缓存与断网快速恢复；
- 现有 API Key、网页登录凭据和最近成功数据。

## 本轮未修改

- 未修改 Widget XML、四卡数量或布局；
- 未新增数据库、WorkManager 或第二套授权体系；
- 未修改 AdapterFactory 路由原则；
- 未合并到 `main`；
- 未卸载、未清数据；
- 未打印 API Key、Cookie 或 Bearer Token。

## 真机验收

安装后重点确认：

1. 首页只看到四张简洁槽位卡和开关；
2. 首页没有服务商选择、API 输入框或技术说明；
3. 点击设置后进入独立槽位编辑页；
4. API 地址和 API Key 始终直接可填；
5. 页面下方同时显示“平台账户（可选）”说明，不再与 API 二选一；
6. DeepSeek、MiMo、爱黄牛地址能自动匹配官网登录入口；
7. Kimi / NewAPI 没有无效官网登录按钮；
8. 模型列表、现有登录状态和四个平台 Widget 数据正常；
9. 断网兜底不回归；
10. App 无崩溃、空白、串卡或凭据泄露。

## 应用信息

- Android 包名：`com.java.myapplication.dev`
- Debug 构建：`./gradlew assembleDebug`
- APK：`app/build/outputs/apk/debug/app-debug.apk`
- 覆盖安装：`pm install -r`

严禁卸载、`pm clear` 或清除应用数据。

## OperitAI 权限边界

OperitAI 只允许：拉取、检查 HEAD 和工作区、编译一次、覆盖安装一次、启动、汇报。

OperitAI 不得修改代码或文档，不得自行修复，不得提交或推送，不得 reset／clean／restore，不得卸载或清数据。
