# AI API Dashboard｜AI 交接状态

> 最近更新：2026-07-15

## 当前阶段

`Stage 8B-R：DeepSeek 平台账户登录闭环修复`

- 开发分支：`feature/stage-8b-simple-connection-flow`
- 登录检测提交：`c5722dca470575f04f70e7e5e79fc1e4092cdbd9`
- DeepSeek 数据适配提交：`c11f880f4a952bd6a5888e641303831e56f89bbf`
- 当前状态：云端代码已推送，尚未声称编译、安装或真机验收成功
- 下一步：OperitAI 只拉取、编译一次、覆盖安装一次并启动

## 用户确认的交互

四个槽位使用完全一致的页面：

1. API 地址；
2. API Key；
3. 检测并选择模型；
4. 平台账户登录（可选增强）。

用户不选择服务商或技术协议。系统只根据 API 地址自动匹配网页登录入口。

## 本轮根因

上一版先请求 `get_user_summary` 再判断登录，而且在读取到 Cookie 前就阻断验证。DeepSeek 的登录会话可能依赖 HttpOnly Cookie 与网页返回的临时访问 Token，导致用户已经登录，App 仍无法保存授权。

## 本轮修复

- DeepSeek 登录检测改为直接调用当前账户接口 `users/current`；
- 验证在同一个已登录 WebView 会话内执行；
- 不再要求 Android 先读到 Cookie 才开始验证；
- 每秒自动重试，并设置验证超时后继续下一轮；
- 登录有效时保存内部凭据包：Cookie（能读取时）和临时访问 Token；
- 凭据只写入授权存储，不显示、不写日志；
- DeepSeek Adapter 兼容旧纯 Cookie 凭据和新的内部凭据包；
- Widget 刷新时优先用 Cookie 获取新的临时 Token，再读取账户汇总；
- 登录成功后自动关闭登录页，配置页由平台公共授权键同步为“已连接”；
- 不再显示“未检测到登录状态，已取消授权”的假提示。

## 本轮修改文件

- `WebAuthActivity.kt`
- `DeepSeekOfficialAdapter.kt`

未修改 Widget 布局、模型配置、其他平台 Adapter 或缓存结构。

## 真机验收

1. 已登录 DeepSeek 后应自动关闭登录页；
2. 返回配置页后应显示“平台账户已连接”；
3. 再次进入时应直接识别现有登录状态；
4. 同一 DeepSeek 登录状态应在所有使用 DeepSeek API 地址的槽位中一致；
5. Widget 刷新后应能出现本月消费、本月 Token 或其他网页账户数据；
6. MiMo、爱黄牛原有授权不得丢失；
7. 四个平台 Widget 数据和断网兜底不得回归。

## OperitAI 权限边界

只允许拉取、检查 HEAD、编译一次、覆盖安装一次、启动和汇报。

禁止修改代码、自动修复、重复编译、提交、推送、卸载或清除应用数据。
