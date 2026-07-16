# AI API Dashboard｜AI 交接状态

> 最近更新：2026-07-16

## 当前阶段

`Stage 8B-R：GitHub 云端构建与远程安装交付`

- 开发分支：`feature/stage-8b-simple-connection-flow`
- 前置已验收版本：`38c38bf7603b1d96784313f80f77e4c8b5ebc8c9`
- 请求实例身份：`91849aef667457656d8656bdf26120c860185e5a`
- 实例缓存隔离：`33ad0b3a9fbb79ddd8e524df2c8cf08b930fc89e`
- 网络层缓存身份：`af9bc7774e647e6728480932c28345f80ebe5f51`
- 当前云端基线：`25620bce72bf4502d45d45bafe2405ca62edefd0`
- 当前状态：首次 GitHub Actions 已真实执行，但 Gradle Wrapper 指向 Operit 本地文件路径而失败；工作流专用修复待重新构建验证

## 云端状态

- 仓库：`anhphuocchu2999-cloud/ai-api-dashboard`
- 可见性：Public
- 唯一开发基线：GitHub 远端 `feature/stage-8b-simple-connection-flow`
- 本地旧副本不得覆盖远端；继续开发必须从远端最新 HEAD 建立干净工作区。
- `2f67e57806406d90cb0046d87df8da994834335e` 的构建曾因 Debug `resValue` 未启用而失败。
- `b92c4b667f50191320b57d3d18a7aa79c0968f19` 已加入 `resValues = true`。
- `25620bce72bf4502d45d45bafe2405ca62edefd0` 已加入云端 APK 构建与 Prerelease 工作流。
- GitHub Actions 运行 `29506642960` 失败于 `file:///root/gradle/gradle-9.1.0-bin.zip` 权限错误，尚未进入 Android 编译。
- 当前修复只在 GitHub Runner 内把 Wrapper 地址临时替换为 Gradle 官方 HTTPS 地址，不修改 Operit 本地 Wrapper 配置。

## 已进入云端的主要修复

- 配置离开页面或进入后台时保存草稿，并刷新 Widget。
- 未完整配置的槽位不再请求 Adapter 或显示残留平台数据。
- Widget 与设置页预览请求均绑定实例身份。
- 八连点刷新按 `appWidgetId` 独立计数。
- 服务授权与当前 API Base/Profile 绑定，不再读取无关旧授权。
- 模型检测返回前核对当前地址与 Key，拒绝旧请求覆盖新输入。
- 残缺成功结果不得降级覆盖字段更完整的最近成功缓存。
- VPN 网络只要具备 Internet 能力即可尝试真实请求。
- 已启用槽位参与紧凑布局，卡片 Logo 按真实服务映射。
- Debug 包具有独立应用 ID 后缀和应用名称。
- Android 系统备份已关闭，避免凭据进入系统备份。

以上是源码和 Git 提交状态，不等同于当前 HEAD 已完成 Android 编译或真机验收。

## 当前唯一任务

让 GitHub Actions 对云端最新提交完成一次真实 `assembleDebug`，生成可下载 APK，并通过 GitHub Prerelease 向用户提供远程安装链接。当前阶段通过前不继续修改业务功能。

## 本地执行边界

真机端只负责从 GitHub Release 下载并覆盖安装 APK、启动和汇报。不得卸载或清除应用数据，以免破坏现有配置和授权。

## 阶段验收

1. GitHub Actions 对远端精确提交执行 `assembleDebug` 成功。
2. GitHub Release 附带与标签和提交对应的 Debug APK。
3. 手机覆盖安装后核对应用名称、阶段和版本。
4. 真机回归配置保存、模型映射、八连点刷新、授权绑定和缓存隔离。
5. 用户明确确认前，不合并到 `main`，不开始下一项业务 Bug。
