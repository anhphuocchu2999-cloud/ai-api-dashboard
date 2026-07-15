# AI API Dashboard｜AI 交接状态

> 最近更新：2026-07-15

## 当前阶段

`Stage 8B-R：DeepSeek 平台账户登录闭环修复`

- 开发分支：`feature/stage-8b-simple-connection-flow`
- 当前业务提交：`a932d71bbcfb7d882f949f59a4e8236a038196ac`
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

## 本轮确认的根因

连续两版由 App 主动构造 `users/current` 或 `get_user_summary` 请求，真机均未识别已经登录的 DeepSeek。原因是网页自己的请求可能携带额外状态或请求头，App 主动请求无法完整复制。

此前“网页数据摸排”已经在同一台真机成功捕获 DeepSeek 页面自己发出的 `users/current`、`get_user_summary` 等请求，因此本轮不再猜请求格式，直接复用这条已经验证过的监听页面网络请求方案。

## 本轮修复

- 在 DeepSeek 登录页早期、重复注入 fetch/XHR 观察器；
- 只观察 DeepSeek 页面自己发出的账户接口响应，不再由 App 猜测并主动拼请求；
- 捕获 `users/current` 成功响应后确认账户已登录，并取得页面实际使用的临时访问 Token；
- 捕获 `get_user_summary` 成功响应时也可完成登录确认；
- 已经登录后直接进入用量页时，同一 WebView 标签页只自动刷新一次，让网页在观察器安装后重新请求账户数据；
- 成功后保存 Cookie（能读取时）和临时访问 Token 的内部凭据包；
- 凭据不显示、不写日志；
- 自动关闭登录页并返回配置页；
- DeepSeek Adapter 兼容旧纯 Cookie 和新内部凭据包，继续读取真实账户汇总。

## 本轮修改文件

- `WebAuthActivity.kt`
- 上一轮已经修改：`DeepSeekOfficialAdapter.kt`

未修改 Widget 布局、模型配置、MiMo／爱黄牛 Adapter 或缓存结构。

## 真机验收

1. 已登录 DeepSeek 后，打开“登录平台账户”；
2. 页面最多自动刷新一次；
3. 捕获到网页自身账户请求后应自动关闭登录页；
4. 返回配置页后应显示“平台账户已连接”；
5. 再次进入时应再次自动识别；
6. 同一 DeepSeek 登录状态应在所有 DeepSeek 槽位中一致；
7. Widget 刷新后应能出现本月消费、本月 Token 或其他网页账户数据；
8. MiMo、爱黄牛和断网兜底不得回归。

## OperitAI 权限边界

只允许拉取、检查 HEAD、编译一次、覆盖安装一次、启动和汇报。

禁止修改代码、自动修复、重复编译、提交、推送、卸载或清除应用数据。
