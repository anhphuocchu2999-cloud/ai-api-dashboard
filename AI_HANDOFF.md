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

`Stage 8B-R：统一槽位交互与平台账户授权归属修复`

- 开发分支：`feature/stage-8b-simple-connection-flow`
- 本轮业务提交：`0b882a508d0ff6551950c81220d21ccd4ea54cdd`
- 本轮规范提交：`b89adf65d91d375365907c75fd57004c4bfadc62`
- 当前状态：云端代码与规范已推送，尚未声称编译、安装或真机验收成功
- 下一步：OperitAI 仅拉取、编译一次、覆盖安装一次并启动；随后用户本人验收

## 用户确认的最终交互

四个槽位使用完全一致的页面结构：

### API 连接

始终直接显示：

- API 地址；
- API Key；
- 检测并选择模型。

用户不选择服务商或技术协议，系统根据 API 地址在后台识别。

### 平台账户（可选）

始终显示相同说明：

> 想获得更完整的余额、消费和用量信息，可以再登录平台账户。API 连接不会受到影响。

用户不选择 DeepSeek、MiMo、爱黄牛。系统根据 API 地址自动匹配；点击按钮后直接打开对应官网。未匹配到已验证登录方案时，按钮禁用，API 仍正常使用。

## 本轮修复的核心问题

旧实现把网页授权按历史槽位名保存和读取，导致同一 DeepSeek 登录状态可能在一个槽位显示已连接、另一个槽位显示未连接。

新逻辑：

1. 网页授权归属于平台公共 Key，而不是历史槽位；
2. 旧授权只存在于历史槽位时，自动复制迁移到平台公共 Key；
3. 平台公共授权同步到所有当前绑定同一平台的槽位，兼容 Widget 仍按固定槽位读取；
4. 登录入口传入平台公共 Key，DeepSeek 登录不再保存到当前历史槽位名；
5. 断开账户会清理平台公共授权及所有绑定槽位的兼容副本；
6. 普通界面不显示服务商选择、Cookie、Bearer Token、Billing 或技术协议。

## 真机验收

1. 四个槽位进入后页面结构完全一致；
2. 所有槽位都同时显示 API 连接和平台账户（可选）；
3. 页面没有服务商选择按钮或弹窗；
4. DeepSeek API 地址自动出现可用的“登录平台账户”按钮；
5. 已有 DeepSeek 登录状态在所有使用 DeepSeek 地址的槽位中一致显示；
6. 从任意 DeepSeek 槽位重新登录后，不再出现另一个槽位已登录、当前槽位未登录；
7. 退出登录页时，已有有效授权不得被误报为取消；
8. MiMo、爱黄牛的已有授权不丢失；
9. 四个平台 Widget 数据与断网兜底不回归；
10. App 无崩溃、空白、串卡或凭据泄露。

## 本轮未修改

- 未修改 Widget XML、四卡数量或布局；
- 未新增数据库、WorkManager 或第二套授权体系；
- 未修改 AdapterFactory 路由原则；
- 未合并到 `main`；
- 未卸载、未清数据；
- 未打印 API Key、Cookie 或 Bearer Token。

## 应用信息

- Android 包名：`com.java.myapplication.dev`
- Debug 构建：`./gradlew assembleDebug`
- APK：`app/build/outputs/apk/debug/app-debug.apk`
- 覆盖安装：`pm install -r`

严禁卸载、`pm clear` 或清除应用数据。

## OperitAI 权限边界

OperitAI 只允许：拉取、检查 HEAD 和工作区、编译一次、覆盖安装一次、启动、汇报。

OperitAI 不得修改代码或文档，不得自行修复，不得提交或推送，不得 reset／clean／restore，不得卸载或清数据。
