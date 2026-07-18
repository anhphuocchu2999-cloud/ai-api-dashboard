# AI API Dashboard｜AI 交接状态

> 最近更新：2026-07-18

## 当前阶段

`Stage 8F-P4.1：实验室自动检测并选择模型`

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
- Stage 8F-P2 文档闭环与 P2-T 起点：`9c734bad602756cd33d50049bf064a96ba28a753`
- Stage 8F-P2-T 业务提交与 Release 目标：`6d9dad75f5f083a49d4ec926ba995f33ac1056d3`
- Stage 8F-P2-T 文档闭环与 P2-J 起点：`91ba4d2c22dc2cf7f8705110fc157a95c1560e81`
- Stage 8F-P2-J 业务提交与 Release 目标：`d391f938b30313cf2f186d4f7e7b2bd41ed166a3`
- Stage 8F-P3 起始远端 HEAD：`dc37698e140e92cb0e79c5c9c8c14d9d02e7b9a5`
- Stage 8F-P3 业务提交：`1f1c23b9f3fd00b3b107ccfde78577d274cb4cd2`
- Stage 8F-P3 JVM 测试环境修正与 Release 目标：`28b9df9ef6333154828a87caaa3a03624f6f78c6`
- Stage 8F-P3 真机验收闭环与 Stage 8F-P4 起点：`005fe9ecf87ab0bd0a5c68c69c218747b1752f76`
- Stage 8F-P4 业务提交与 Release 目标：`74375e472c779bed56ee2b420efaa4da4da91441`
- Stage 8F-P4.1 业务提交与 Release 目标：`39646786e021ce43654b47851467c7243b06caf2`
- 当前状态：P4.1 代码、GitHub Actions、固定签名和 beta.13 发布已完成；等待用户覆盖安装并真机验收模型检测与选择

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

用户覆盖安装 beta.13 并验证：实验室只填写 API Base 与 API Key，真实获取模型列表并选择后才能打开仪表盘；地址或 Key 改动后旧选择必须立即失效。验收前不进入下一阶段。

## Stage 8F-P4.1 当前实现

- 实验首页版本：`Stage 8F-P4.1 · Prototype 20260718-005`。
- 删除可手工输入的模型名称，新增“测试连接并获取模型”、中文状态提示和模型下拉选择器。
- 主 App 与实验室共享 `ModelCatalogClient`：只请求 HTTPS `/v1/models`，Bearer 使用当前输入 Key，单次连接与读取超时均为 10 秒，不自动重试。
- 只接受标准 `data[].id`；单模型自动选中，多模型由用户明确选择；未成功检测时“打开仪表盘并登录”保持禁用。
- API Base 或 API Key 变化会清空模型列表和选择，并递增 generation；检测期间修改输入后，旧请求结果不能重新显示或用于 AI 调用。
- API Key 和模型选择只在实验 Activity 内存中存在；不写入请求配方、日志、GitHub 或 Widget 配置。
- 本机缺少 Java、Android SDK 和 ADB；XML 解析与差异检查已通过，Android 单元测试和编译由 GitHub Actions 执行。

## Stage 8F-P4.1 云端交付

- 业务提交与 Release 目标：`39646786e021ce43654b47851467c7243b06caf2`。
- GitHub Actions：`29642475652`，结论 `success`；`testDebugUnitTest + assembleDebug`、固定证书校验、Artifact 上传和 Prerelease 发布均一次通过。
- Release：`https://github.com/anhphuocchu2999-cloud/ai-api-dashboard/releases/tag/v0.1.0-beta.13`
- APK：`ai-api-dashboard-v0.1.0-beta.13-debug.apk`，大小 `12168659` 字节。
- 从公开 Release 下载后 SHA-256：`7100645467D32811FE0BBB9C3129DE98D2C92061EDA15BEFDBEBF8F211BA5166`。
- 工作流固定 Beta 证书 SHA-256 校验：`A8F816B106F23274F35E3DDC8B19C464A31F7A7BD0871E3294AA6E6922954860`，通过。
- 覆盖安装与真机模型检测验收：待用户执行，不得宣称通过。

## Stage 8F-P4 当前实现

- 实验首页版本：`Stage 8F-P4 · Prototype 20260718-004`。
- “已保存的直连配方”区域列出已启用且完整配置的模型卡片，用户必须明确选择一张卡片接入；支持解除接入，删除配方也会触发 Widget 刷新。
- 配方保存稳定 `instanceId`；`AdapterFactory` 只对绑定实例路由 `DashboardRecipeAdapter`，未绑定实例和其他卡片继续使用现有专用 Adapter。
- `BalanceWidgetProvider` 和设置页预览只传实例身份并调用 AdapterFactory，不读取配方 Cookie、不解析站点 JSON。
- 通用 Adapter 必须取得配方内全部已验证字段，才映射为 `WidgetData`；余额/额度等核心指标优先，普通用量/请求/Token 与辅助字段分区显示，不伪造百分比。
- 通用数据源使用独立缓存命名空间，避免同一实例原专用 Adapter 的历史字段被误当作仪表盘配方数据；失败结果仍由现有实例缓存策略处理。
- 配方与 Keystore 密文从多进程 `SharedPreferences` 迁移到 `noBackupFilesDir` 原子文件，主进程与独立实验进程每次读取磁盘事实；旧 beta.11 数据首次读取时自动迁移。
- 当前仍只支持 P3 已验收的同 Origin、无查询参数 GET + Cookie + JSON；未扩展 POST、GraphQL、Bearer/OAuth、localStorage 或多配方。
- 本机缺少 Android SDK 和 Java；XML 解析和 `git diff --check` 已通过，Android 编译只能由 GitHub Actions 执行。

## Stage 8F-P4 云端交付

- GitHub Actions：`29641354417`，结论 `success`；全部步骤一次通过，未修复重跑。
- `testDebugUnitTest + assembleDebug`：成功。
- 固定 Beta 证书 SHA-256 校验：`A8F816B106F23274F35E3DDC8B19C464A31F7A7BD0871E3294AA6E6922954860`，成功。
- Release：`https://github.com/anhphuocchu2999-cloud/ai-api-dashboard/releases/tag/v0.1.0-beta.12`
- APK：`ai-api-dashboard-v0.1.0-beta.12-debug.apk`，大小 `12168475` 字节。
- 从公开 Release 下载后 SHA-256：`7219F8F288F265917624AD44CB139C3D2AD8BB839BDB556123D6EF3D1BAB83FB`。
- Release 目标提交：`74375e472c779bed56ee2b420efaa4da4da91441`。
- 覆盖安装与真机 Widget 验收：待用户执行，不得宣称通过。

## Stage 8F-P3 当前实现

- 实验首页版本：`Stage 8F-P3 · Prototype 20260718-003`。
- AI 识别并本机核对后，结果页可对同 Origin、无查询参数的 GET 接口使用当前 WebView Cookie 做一次真实直连二次核对。
- 所有已验证字段必须在新响应中再次存在且类型一致，才会保存一份最小请求配方。
- 请求配方不含响应正文、样本值或 AI API Key；Cookie 映射整体经 Android Keystore 加密，加密失败不降级为明文。
- 实验首页可直接刷新最近保存的一份配方或同时删除规则与加密登录状态；直接刷新不调用 AI、不打开网页。
- 首版明确拒绝 POST、GraphQL、带查询参数、跨 Origin、Bearer/OAuth/localStorage 等协议；不接入 Widget、AdapterFactory、槽位配置或最近成功缓存。
- 本地缺少 Android SDK 和 Java，未冒充执行本地构建；GitHub Actions 是本阶段唯一测试与编译执行端。

## Stage 8F-P3 云端交付

- 首轮 GitHub Actions：`29640204414`，失败于新增 JVM 单元测试调用 Android `org.json`；未进入签名、Artifact 或 Release。
- 最小修正：只移除环境不成立的 JSON 往返 JVM 测试，业务代码未改；规则测试继续覆盖 GET、POST、查询参数和跨 Origin 边界。
- 成功 GitHub Actions：`29640399072`，结论 `success`；`testDebugUnitTest + assembleDebug`、固定签名校验、Artifact 上传和 Prerelease 发布全部成功。
- Release：`https://github.com/anhphuocchu2999-cloud/ai-api-dashboard/releases/tag/v0.1.0-beta.11`
- APK：`ai-api-dashboard-v0.1.0-beta.11-debug.apk`，大小 `12151827` 字节。
- 从公开 Release 下载后 SHA-256：`A8390E6E7FE89FE52D8FBB18AED7178C3ECF696776F65784E70693A2B7D9777A`。
- 工作流校验的固定 Beta 证书 SHA-256：`A8F816B106F23274F35E3DDC8B19C464A31F7A7BD0871E3294AA6E6922954860`。
- 覆盖安装和真机配方复测：`用户真机确认`，通过；已保存配方在关闭重开实验室后可不调用 AI 直接刷新。

## Stage 8F-P2-J 当前实现

- 实验首页版本：`Stage 8F-P2-J · Prototype 20260718-002`。
- 接受 `data.costUsage.totalCost` 这类只含普通属性、点号和数组下标的相对路径，并规范化为 `$.data.costUsage.totalCost`。
- 标准 `$` 路径继续原样使用；结果输出统一为规范化路径。
- 规范化后仍通过原有安全解析器、真实响应取值和实际类型核对；endpoint 仍必须属于本次捕获。
- 递归、通配符、过滤器、函数、脚本和反斜线表达式继续拒绝。
- 不修改 beta.9 的超时策略、捕获范围、Widget、Adapter、授权或缓存，不保存规则、不重放请求。

## Stage 8F-P2-J 云端交付

- 业务提交与 Release 目标：`d391f938b30313cf2f186d4f7e7b2bd41ed166a3`。
- GitHub Actions：`29639173092`，结论 `success`，运行时间 `2026-07-18T09:26:55Z` 至 `2026-07-18T09:28:21Z`。
- `testDebugUnitTest + assembleDebug`：成功；本阶段只触发这一轮云端测试与编译。
- Release：`https://github.com/anhphuocchu2999-cloud/ai-api-dashboard/releases/tag/v0.1.0-beta.10`
- APK：`ai-api-dashboard-v0.1.0-beta.10-debug.apk`，大小 `12134847` 字节。
- APK SHA-256：`D62EA7E062B675D35F1AF51DE29B6CF8EB810B9898B36950F134037424CFDA75`。
- 从 APK v2 签名块独立提取的证书 SHA-256：`A8F816B106F23274F35E3DDC8B19C464A31F7A7BD0871E3294AA6E6922954860`，与项目固定 Beta 证书一致。
- 覆盖安装与真机路径复测：`用户真机确认`，通过；13 个真实接口字段完成本机验证，2 个页面观察值保持隔离。

## Stage 8F-P2-T 当前实现

- 实验首页版本：`Stage 8F-P2-T · Prototype 20260718-001`。
- beta.8 最多向模型发送 12 条 × 8,000 字符响应片段和 6,000 字符页面文字；P2-T 改为最高优先级 8 条 × 4,000 字符和 4,000 字符页面文字。
- 本机仍保留最多 12 条完整脱敏候选用于验证 AI 返回的 endpoint/jsonPath，没有降低真实性核对范围。
- 连接超时保持 20 秒，读取超时由 60 秒改为 120 秒；不新增自动重试或无限等待。
- 调用期间显示通常需要 10～90 秒；超过 120 秒时提供明确中文提示并恢复再次操作能力。
- 仍不保存规则、不重放请求、不接入 Widget，不修改现有 Adapter、授权或缓存。

## Stage 8F-P2-T 云端交付

- 业务提交与 Release 目标：`6d9dad75f5f083a49d4ec926ba995f33ac1056d3`。
- GitHub Actions：`29638659078`，结论 `success`，运行时间 `2026-07-18T09:08:20Z` 至 `2026-07-18T09:09:47Z`。
- `testDebugUnitTest + assembleDebug`：成功；本阶段只触发这一轮云端测试与编译。
- Release：`https://github.com/anhphuocchu2999-cloud/ai-api-dashboard/releases/tag/v0.1.0-beta.9`
- APK：`ai-api-dashboard-v0.1.0-beta.9-debug.apk`，大小 `12134847` 字节。
- APK SHA-256：`135EF2063C22CC4F780F9B54C4172B01D1E69B9D5DB27D61F32E857DC59E3888`。
- 从 APK v2 签名块独立提取的证书 SHA-256：`A8F816B106F23274F35E3DDC8B19C464A31F7A7BD0871E3294AA6E6922954860`，与项目固定 Beta 证书一致。
- 覆盖安装与真机超时复测：待用户执行，不得宣称通过。

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
