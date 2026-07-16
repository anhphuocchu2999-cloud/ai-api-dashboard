# AI API Dashboard｜AI 交接状态

> 最近更新：2026-07-17

## 当前阶段

`Stage 8D-S：固定 Beta 签名与持续覆盖升级`

- 开发分支：`feature/stage-8b-simple-connection-flow`
- 前置已验收版本：`38c38bf7603b1d96784313f80f77e4c8b5ebc8c9`
- 请求实例身份：`91849aef667457656d8656bdf26120c860185e5a`
- 实例缓存隔离：`33ad0b3a9fbb79ddd8e524df2c8cf08b930fc89e`
- 网络层缓存身份：`af9bc7774e647e6728480932c28345f80ebe5f51`
- 当前云端业务基线：`8f4609b8dbabe802f9bd24705c896e07954a6b81`
- Stage 8B-R 文档闭环：`e1b0b7fde43e876af58f1eb77e24777dfc8c558b`
- 当前状态：`v0.1.0-beta.3` 已由运行 `29514966427` 成功构建、核对固定证书并发布；用户真机截图确认同步角标与辅助指标重叠，当前只修复角标布局并准备 `v0.1.0-beta.4`

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

修复四张 Widget 卡片的 `😂` 同步角标定位：角标与辅助指标放在同一底部横向行并独占最右侧宽度，不修改刷新和数据逻辑；随后构建并发布 `v0.1.0-beta.4`。

## 本地执行边界

真机端只负责从 GitHub Release 下载并覆盖安装 APK、启动和汇报。不得卸载或清除应用数据，以免破坏现有配置和授权。

## 阶段验收

1. 固定 Beta 私钥和密码只存在本地私密目录与 GitHub Actions Secrets，不进入仓库。四项 Secrets 已写入并按名称核对。
2. Gradle 仅在四项签名环境变量完整时启用固定 Beta 签名。已实现；运行 `29514548823` 的 `assembleDebug` 成功。
3. Prerelease 工作流在运行 `29514966427` 核对实际指纹为 `A8F816B106F23274F35E3DDC8B19C464A31F7A7BD0871E3294AA6E6922954860`，通过。
4. `v0.1.0-beta.3` 已发布，APK SHA-256 为 `3A018870B2A1B01362B6F0926DD99A4B6941D5969F2317911576F02363269374`。
5. `v0.1.0-beta.4` 待构建、覆盖安装并真机验收角标位置。
6. 用户明确确认前，不合并到 `main`，不开始下一项产品功能。
