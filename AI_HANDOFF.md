# AI API Dashboard｜AI 交接状态

> 最近更新：2026-07-17

## 当前阶段

`Stage 8F-P2：更早捕获与本机字段核对`

- 开发分支：`feature/stage-8b-simple-connection-flow`
- 前置已验收版本：`38c38bf7603b1d96784313f80f77e4c8b5ebc8c9`
- 请求实例身份：`91849aef667457656d8656bdf26120c860185e5a`
- 实例缓存隔离：`33ad0b3a9fbb79ddd8e524df2c8cf08b930fc89e`
- 网络层缓存身份：`af9bc7774e647e6728480932c28345f80ebe5f51`
- 当前云端业务基线：`8f4609b8dbabe802f9bd24705c896e07954a6b81`
- Stage 8B-R 文档闭环：`e1b0b7fde43e876af58f1eb77e24777dfc8c558b`
- 本阶段起始远端 HEAD：`51c1c68e180c75da47ecaa03858a0ebe33c9ac4a`
- Stage 8E-S 业务提交：`6637b6d92d230e41deb2d15299cd201db0ca28d1`
- Stage 8E-1 业务提交：`77c25508e24cffb6d1a5019f963a99bf429816b0`
- Stage 8E-1 文档闭环与当前云端基线：`c14643ce50f643590065ebabfbb6de5a1424f2e2`
- Stage 8F-P1 起始远端 HEAD：`c14643ce50f643590065ebabfbb6de5a1424f2e2`
- Stage 8F-P1 业务提交与 Release 目标：`5cc1311eccc2465cd09eba5ed87d1e95c8c3d793`
- Stage 8F-P1 文档闭环与 Stage 8F-P2 起点：`df46595608f6de9bc9b7f3ff7568ce6c0f309165`
- Stage 8F-P2 业务提交与 Release 目标：`02aae94f2f9360cac30be0f255d156c04397b9d5`
- 当前状态：用户已用 beta.7 跑通真实站点语义识别；P2 已实现提前捕获、真实路径取值核对和已验证/页面观察分流，beta.8 云端测试、编译、固定证书校验与发布均成功，待真机复测

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
- GitHub Actions 运行 `29506968616` 已完成，`Build debug APK` 与 `Upload debug APK` 均成功。
- 首个远程安装版本固定为 `v0.1.0-beta.1`，由 GitHub Runner 自动创建标签、Prerelease 和 APK 附件。
- GitHub Actions 发布运行 `29507477102` 已完成，构建、Artifact 上传和 `Publish GitHub prerelease` 均成功。
- Release：`https://github.com/anhphuocchu2999-cloud/ai-api-dashboard/releases/tag/v0.1.0-beta.1`
- APK：`ai-api-dashboard-v0.1.0-beta.1-debug.apk`，大小 `11999506` 字节。
- APK SHA-256：`3F4236038302CDB27F2B76CA6D19D9BDB6E6AF3837B05F339D0574793E4AB347`。
- Stage 8C GitHub Actions：`29509997090`，`Build installable debug APK`、Artifact 上传和 Prerelease 发布全部成功。
- Stage 8C Release：`https://github.com/anhphuocchu2999-cloud/ai-api-dashboard/releases/tag/v0.1.0-beta.2`
- Stage 8C APK：`ai-api-dashboard-v0.1.0-beta.2-debug.apk`，大小 `11999746` 字节。
- Stage 8C APK SHA-256：`C36CDC43283FD10280FF3667A239A1B9ACA21FAEEC14D9719D3D92C27CED3B99`。

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

用户覆盖安装 `v0.1.0-beta.8`，使用 beta.7 的同一站点复测：`$.total_usage` 应进入 `verifiedMetrics`，四个缺少 endpoint/jsonPath 的页面文字指标应进入 `observations`；真机确认前不保存规则、不接入 Widget。

## Stage 8F-P2 当前实现

- 实验首页版本：`Stage 8F-P2 · Prototype 20260717-002`。
- AndroidX WebKit Document Start Script 按用户确认的精确 HTTPS Origin 提前安装；不支持时回退兼容捕获。
- AI 指出的 endpoint 必须来自本次捕获，简单 JSON 路径必须在脱敏响应中真实取值，实际类型必须一致。
- 通过字段输出 `verifiedMetrics`；页面文字、空路径、虚构接口、取值失败和类型不符输出 `observations`，附中文原因。
- 用户 beta.7 结果的预期：`$.total_usage` 有机会通过；四个空 endpoint 的余额/Token 必须成为观察信息。
- 仍不保存规则、不重放请求、不接入 Widget。

## Stage 8F-P2 云端交付

- 业务提交与 Release 目标：`02aae94f2f9360cac30be0f255d156c04397b9d5`。
- GitHub Actions：`29571350421`，结论 `success`，运行时间 `2026-07-17T09:49:44Z` 至 `2026-07-17T09:53:24Z`。
- `testDebugUnitTest + assembleDebug`：成功；本阶段只触发这一轮云端测试与编译。
- Release：`https://github.com/anhphuocchu2999-cloud/ai-api-dashboard/releases/tag/v0.1.0-beta.8`
- APK：`ai-api-dashboard-v0.1.0-beta.8-debug.apk`，大小 `12134847` 字节。
- APK SHA-256：`5BAC11C9662087920F4532915551AD9F4EBDB2F2C27508FAC8AECFC5BA9A6DD1`。
- 从 APK v2 签名块独立提取的证书 SHA-256：`A8F816B106F23274F35E3DDC8B19C464A31F7A7BD0871E3294AA6E6922954860`，与项目固定 Beta 证书一致。
- 覆盖安装与真机 P2 验收：待用户执行，不得宣称通过。

## Stage 8F-P1 实验边界

- 第二个桌面入口：`仪表盘识别实验室`，显示 `Stage 8F-P1 · Prototype 20260717-001`。
- 运行于 `:dashboard_discovery` 独立进程；Android 9+ 使用独立 WebView 数据目录，退出清理实验 Cookie、网页存储与缓存。
- 用户确认 HTTPS 页面后才捕获页面主框架的 JSON 响应；不使用原生 JavaScript Bridge，不读取请求头、请求体、Cookie 或 Web Storage 内容。
- 本机脱敏后只向用户填写的 OpenAI-Compatible API Base 发出一次模型请求；API Key 不写入实验配置。
- 模型映射只预览、不保存；endpoint 必须来自捕获集合，结果仍需用户人工核对。
- 不修改 Widget、Adapter、配置、授权和最近成功缓存。

## Stage 8F-P1 云端交付

- GitHub Actions：`29565363529`，结论 `success`。
- Release：`https://github.com/anhphuocchu2999-cloud/ai-api-dashboard/releases/tag/v0.1.0-beta.7`
- APK：`ai-api-dashboard-v0.1.0-beta.7-debug.apk`，大小 `12036378` 字节。
- APK SHA-256：`A84B1F0B0725AEB5322BD94EED642E62364CE5CA605E6632EC620C17D423F216`。
- 固定证书 SHA-256：`A8F816B106F23274F35E3DDC8B19C464A31F7A7BD0871E3294AA6E6922954860`。
- 覆盖安装与真机实验验收：待用户执行。

## Stage 8E-1 云端交付

- GitHub Actions：`29558328511`，结论 `success`。
- Release：`https://github.com/anhphuocchu2999-cloud/ai-api-dashboard/releases/tag/v0.1.0-beta.6`
- APK：`ai-api-dashboard-v0.1.0-beta.6-debug.apk`，大小 `11999850` 字节。
- APK SHA-256：`C8E81A1FC919EE63FAFA936122C44F3C7C181F3A2CA3A062FB98ED42A771AA7C`。
- 固定证书 SHA-256：`A8F816B106F23274F35E3DDC8B19C464A31F7A7BD0871E3294AA6E6922954860`。
- 覆盖安装与真机验收：待用户执行。

## Stage 8E-S 云端交付

- GitHub Actions：`29551280479`，结论 `success`。
- Release：`https://github.com/anhphuocchu2999-cloud/ai-api-dashboard/releases/tag/v0.1.0-beta.5`
- APK：`ai-api-dashboard-v0.1.0-beta.5-debug.apk`，大小 `11999794` 字节。
- APK SHA-256：`DB49CB09CF785118C2D1474BDD62915F813E3EA21F2C5750A25500A2C2E29738`。
- 固定证书 SHA-256：`A8F816B106F23274F35E3DDC8B19C464A31F7A7BD0871E3294AA6E6922954860`。
- 覆盖安装与真机验收：待用户执行。

## 本地执行边界

真机端只负责从 GitHub Release 下载并覆盖安装 APK、启动和汇报。不得卸载或清除应用数据，以免破坏现有配置和授权。

## 阶段验收

1. 固定 Beta 私钥和密码只存在本地私密目录与 GitHub Actions Secrets，不进入仓库。四项 Secrets 已写入并按名称核对。
2. Gradle 仅在四项签名环境变量完整时启用固定 Beta 签名。已实现；运行 `29514548823` 的 `assembleDebug` 成功。
3. Prerelease 工作流在运行 `29514966427` 核对实际指纹为 `A8F816B106F23274F35E3DDC8B19C464A31F7A7BD0871E3294AA6E6922954860`，通过。
4. `v0.1.0-beta.3` 已发布，APK SHA-256 为 `3A018870B2A1B01362B6F0926DD99A4B6941D5969F2317911576F02363269374`。
5. `v0.1.0-beta.4` 已发布，APK SHA-256 为 `A075EC3425FAEC1D81429292F92A0B140DC26D5B57BAE0328715E4B2827A7145`；待覆盖安装并真机验收角标位置。
6. 用户明确确认前，不合并到 `main`，不开始下一项产品功能。
