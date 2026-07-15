# AI API Dashboard｜AI 交接状态

> 最近更新：2026-07-15

## 当前阶段

`Stage 8B-R：统一槽位交互与平台账户识别修复`

- 开发分支：`feature/stage-8b-simple-connection-flow`
- 上一版 HEAD：`25e50c63c15c20de5cef4b03542c4f42f71c2c12`
- 本轮最新 HEAD：`dc9b7e50b38a53a505bf7b2a68db6d195304c903`
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

旧逻辑错误地要求 MiMo API 地址包含官网登录域名，导致 `api.xiaomimimo.com` 被判断为不支持登录。

新逻辑同时识别：

- `api.xiaomimimo.com`
- `platform.xiaomimimo.com`

然后直接打开 MiMo 官网账户页面。

### DeepSeek 登录状态

旧逻辑的平台公共授权键与历史固定槽位键发生冲突，导致同一登录状态可能在一个槽位显示已连接、另一个槽位显示未连接。

新逻辑：

- MiMo、DeepSeek、爱黄牛分别使用独立的平台公共授权键；
- 旧固定槽位授权自动复制迁移到平台公共键；
- 平台公共授权再同步到所有当前使用该平台的槽位；
- 登录页读取平台公共授权，因此已有登录不会再被当前槽位误判为不存在；
- 用户明确断开时才清理公共授权及兼容副本。

## 本轮修改文件

- `WebAuthProfile.kt`
- `WebAuthProfileRegistry.kt`
- `BackgroundAuthRepository.kt`

未修改 Widget 布局、Adapter 路由、模型配置或缓存结构。

## 真机验收

1. MiMo API 地址应出现可用的“登录平台账户”按钮；
2. DeepSeek 已有登录状态应在所有 DeepSeek 槽位中一致显示；
3. 从任一 DeepSeek 槽位进入登录页后，不得再误报“已取消授权”；
4. MiMo、DeepSeek、爱黄牛原有授权不得丢失；
5. 四个平台 Widget 数据和断网兜底不得回归。

## OperitAI 权限边界

只允许拉取、检查 HEAD、编译一次、覆盖安装一次、启动和汇报。

禁止修改代码、自动修复、重复编译、提交、推送、卸载或清除应用数据。
