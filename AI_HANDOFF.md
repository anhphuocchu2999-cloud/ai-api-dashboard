# AI API Dashboard｜AI 交接状态

> 最近更新：2026-07-15

## 当前阶段

`Stage 8B-R：统一槽位交互与平台账户登录闭环修复`

- 开发分支：`feature/stage-8b-simple-connection-flow`
- 上一版 HEAD：`e351a040d7ec8425a7d7705371eafc67c2328f30`
- 本轮业务提交：`1e028b4b15d65664990baaa4a9b3ba7d5b8df70b`
- 当前状态：云端代码已推送，尚未声称编译、安装或真机验收成功
- 下一步：OperitAI 只拉取、编译一次、覆盖安装一次并启动

## 用户确认的交互

四个槽位使用完全一致的页面：

1. API 地址；
2. API Key；
3. 检测并选择模型；
4. 平台账户登录（可选增强）。

用户不选择服务商或技术协议。系统只根据 API 地址自动匹配网页登录入口。

## 本轮修复

### MiMo 识别

MiMo API 地址 `api.xiaomimimo.com` 已映射到 MiMo 官网账户页面，不再要求 API 域名与官网登录域名完全一致。

### DeepSeek 登录检测

上一版仍在 Android 的独立 `HttpURLConnection` 中验证 DeepSeek Cookie。DeepSeek 网页会话中的账户 Token 和 Cookie 由网页共同维护，独立请求可能无法复现网页真实登录状态，因此会错误提示“未检测到登录状态”。

新逻辑：

- 在已经登录的同一个 WebView 会话内验证 `get_user_summary`；
- 先使用网页 Cookie 直接请求；
- 如网站需要账户 Token，只在网页内部读取 `users/current` 返回的 Token 后重试；
- Token 不传回 Android、不保存、不显示、不写日志；
- 验证成功后只保存用于后续账户数据请求的 Cookie；
- 登录检测每秒重试，支持网页通过 AJAX 完成登录而不发生整页跳转；
- 已删除 Activity 销毁时的“未检测到登录状态，已取消授权”假提示；
- 登录成功后保存到平台公共授权键，返回配置页由现有同步逻辑更新所有同平台槽位。

## 本轮修改文件

- `WebAuthActivity.kt`
- 上一轮已修改：`WebAuthProfile.kt`、`WebAuthProfileRegistry.kt`、`BackgroundAuthRepository.kt`

未修改 Widget 布局、Adapter 路由、模型配置或缓存结构。

## 真机验收

1. MiMo API 地址应出现可用的“登录平台账户”按钮；
2. DeepSeek 登录后应自动返回配置页并显示“平台账户已连接”；
3. 已登录 DeepSeek 再次进入时，应能直接识别现有网页会话；
4. 退出登录页时不得再出现“已取消授权”假提示；
5. 同一 DeepSeek 登录状态应在所有使用 DeepSeek API 地址的槽位中一致；
6. MiMo、爱黄牛原有授权不得丢失；
7. 四个平台 Widget 数据和断网兜底不得回归。

## OperitAI 权限边界

只允许拉取、检查 HEAD、编译一次、覆盖安装一次、启动和汇报。

禁止修改代码、自动修复、重复编译、提交、推送、卸载或清除应用数据。
