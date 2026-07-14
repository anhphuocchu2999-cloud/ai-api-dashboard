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

`Stage 8B-R：简化槽位连接流程`

- 开发分支：`feature/stage-8b-simple-connection-flow`
- 起点：`ed987b6e7c009dad3d2eacaaa258ae532a0165bd`
- 第一版简化提交：`fe93f224390954f5d4bdc1e3bf3150a388f728e2`
- 用户反馈：第一版仍把服务选择和连接设置直接展开在槽位长卡片里，页面依然复杂
- 第二版业务提交：`ccf82db16cda37e1464386e528bb8ce7fb69ded6`
- 第二版规范提交：`29664c951ffe160e0a05293e93c59c7ff9670575`
- 当前状态：云端代码已推送，尚未声称编译、安装或真机验收成功
- 下一步：OperitAI 仅拉取、编译一次、覆盖安装一次并启动；随后用户本人验收

## 第二版界面结构

### 首页

首页只显示四张简洁槽位卡：

- 槽位编号；
- 模型或服务名称；
- 已连接／未配置状态；
- 显示开关；
- 设置／编辑按钮。

首页已移除：

- Build 编号；
- 服务下拉框；
- API 输入框；
- 官网登录控件；
- Billing、Cookie、Token 等技术说明。

### 单槽位编辑页

点击“设置／编辑”后进入独立页面，一次只配置一个槽位。

编辑页流程：

1. 查看当前服务，必要时点“更换”；
2. 选择“使用 API”或“登录官网”；
3. API 模式只填写 API 地址和 API Key，然后检测并选择模型；
4. 官网模式只显示账户状态和登录／重新登录按钮。

普通界面不再显示手动 Cookie、手动 Bearer Token、保存授权或独立 Billing 配置。

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
- 未新增数据库、WorkManager 或第二套配置仓库；
- 未修改 AdapterFactory 路由原则；
- 未合并到 `main`；
- 未卸载、未清数据；
- 未打印 API Key、Cookie 或 Bearer Token。

## 真机验收

安装后重点确认：

1. 首页只看到四张简洁槽位卡和开关；
2. 首页不再直接显示服务下拉框和任何输入框；
3. 点击“设置／编辑”后进入单独的槽位编辑页；
4. 编辑页只显示服务、API／官网登录二选一和当前方式需要的控件；
5. API 模式可以返回模型列表；
6. MiMo、DeepSeek、爱黄牛可进入官网登录；
7. 现有网页登录状态没有丢失；
8. Kimi、MiMo、DeepSeek、爱黄牛 Widget 数据正常；
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
