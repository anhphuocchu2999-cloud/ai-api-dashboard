# AI API Dashboard｜AI 交接状态

> 最近更新：2026-07-14
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
- 正式需求补充：`f40f459fe976d84ef9c8f69028416dd36c7742ca`
- 配置页业务提交：`fe93f224390954f5d4bdc1e3bf3150a388f728e2`
- 当前状态：云端代码已推送，尚未声称编译、安装或真机验收成功
- 下一步：OperitAI 仅拉取、编译一次、覆盖安装一次并启动；随后用户本人验收

## 本轮主线

普通用户不再面对 API、Cookie、Bearer Token、Billing、数据能力表等内部概念。

每个槽位只保留两个入口：

1. 使用 API
2. 登录官网账户

Billing、Usage、余额、Token、请求次数均作为连接后的自动数据，不再作为第三种模式。

## 当前配置页结构

### 槽位选择

- 固定保留四个槽位；
- 每个槽位继续使用开关决定显示或隐藏；
- 关闭槽位不删除原配置和历史数据；
- 不增加独立的加减数量页面。

### API 模式

普通用户只看到：

- 服务类型；
- API 地址；
- API Key；
- 检测连接并选择模型；
- 检测后的模型选择列表；
- 简短的数据范围说明。

单模型自动选择，多模型弹窗选择。

### 官网账户模式

对已验证服务开放：

- MiMo；
- DeepSeek 官方；
- 爱黄牛。

普通用户只看到：

- 官网账户是否已连接；
- 登录／重新连接；
- 断开账户；
- 登录后可以读取的数据摘要。

手动 Cookie、手动 Bearer Token、保存授权和 Billing 技术探针均从普通界面移除。

Kimi / NewAPI 当前只显示 API 模式，因为尚无已验证网页登录方案。

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

1. 首页只看到四个槽位和显示开关；
2. 每个开启的槽位通过“开始配置／编辑连接”展开；
3. 普通界面不再出现手动 Cookie、手动 Bearer、保存授权、Billing 技术说明；
4. API 模式可以填写地址和 Key，并返回模型列表；
5. MiMo、DeepSeek、爱黄牛可切换到“登录官网”；
6. 现有网页登录状态没有丢失；
7. Kimi、MiMo、DeepSeek、爱黄牛 Widget 数据正常；
8. 断网兜底不回归；
9. App 无崩溃、空白、串卡或凭据泄露。

## 应用信息

- Android 包名：`com.java.myapplication.dev`
- Debug 构建：`./gradlew assembleDebug`
- APK：`app/build/outputs/apk/debug/app-debug.apk`
- 覆盖安装：`pm install -r`

严禁卸载、`pm clear` 或清除应用数据。

## OperitAI 权限边界

OperitAI 只允许：

- 拉取；
- 检查 HEAD 和工作区；
- 编译一次；
- 覆盖安装一次；
- 启动；
- 汇报。

OperitAI 不得修改代码或文档，不得自行修复，不得提交或推送，不得 reset／clean／restore，不得卸载或清数据。
