# AI API Dashboard

## 一、产品定位

开发一款高品质的 Android AI 服务监控与账户状态仪表盘。

产品名称暂定：

AI API Dashboard

产品形态：

- Android 原生 App
- Android 桌面 Widget
- Local-first（本地优先）
- 用户设备直接连接目标 AI 服务
- 默认不经过开发者自建服务器

产品最终定位不是单纯的：

"AI 余额查询工具"

而是：

"AI 模型、API 服务、账户额度、用量和运行状态的统一 Dashboard"。

---

## 二、最终目标

长期目标：

- 支持多个 AI 官方平台
- 支持多个第三方 AI API 平台
- 支持多个 API Base
- 支持多个模型实例
- 支持 OpenAI-Compatible 服务
- 支持多种认证方式
- 支持余额、额度、Token、调用次数、用量等不同数据
- 支持 Widget 响应式布局
- 支持用户按需选择模型和显示内容
- 支持后续快速扩展新的 Provider
- 保持成熟、统一、美观的产品体验

---

## 三、V1 功能范围

V1 当前优先支持：

- Kimi
- MiMo
- DeepSeek
- OpenAI

基础能力：

- API Base 配置
- API Key 配置
- Test Connection
- 自动请求 `/v1/models`
- 单模型自动选择
- 多模型用户选择
- 模型配置保存
- 手动刷新
- Widget 数据展示

V1 不要求所有平台拥有完全相同的数据能力。

---

## 四、核心对象：模型实例

Widget 的每一张卡片代表一个：

Model Instance（模型实例）

而不是：

- 平台
- 公司
- 中转站
- API 网站

一个模型实例可以包含：

- API Base
- API Key
- Model
- 认证方式
- 数据来源
- 用户自定义名称
- Widget 显示配置

例如：

API Base：

https://code.coolyeah.net

Model：

kimi-k2.6

Widget 主标题应显示：

kimi-k2.6

而不是：

蜜音AI
Kimi 中转站

中转站或服务来源可以作为：

- 配置备注
- 数据来源
- 详情信息

但默认不得替代真实模型名称。

---

## 五、模型名称显示规则

Widget 显示优先级：

第一优先：
真实模型名称（Model）

例如：

- kimi-k2.6
- mimo-v2.5-pro
- deepseek-v4-pro
- gpt-5.4

第二优先：
用户自定义名称

第三优先：
已配置

第四优先：
未配置

Widget 显示标题不得作为 Adapter 选择依据。

---

## 六、真实性原则

不同 AI 服务允许提供完全不同的数据。

例如：

- Kimi：剩余调用次数
- MiMo：账户余额
- DeepSeek：余额、Token、Usage
- OpenAI：余额、消费、使用量
- Claude：额度、重置时间
- Gemini：套餐或使用额度

不得为了统一 UI：

- 伪造数据
- 将无法获取的数据显示为 0
- 将估算数据冒充真实数据
- 使用错误字段代替真实字段
- 强制所有模型显示相同内容

统一的是：

- 数据结构
- 视觉语言
- 用户体验

不是：

"所有模型必须显示同一种指标"。

---

## 七、数据能力模型

Provider 可以根据实际能力提供不同数据。

统一能力概念包括：

- Model
- Connection Status
- Balance
- Usage
- Quota
- Tokens
- Requests
- Subscription
- Reset Time
- History

并非所有 Provider 都必须实现全部能力。

例如：

DeepSeek：

- Model
- Balance
- Usage

MiMo：

- Model
- Balance（Web Account）

Kimi：

- Model
- Remaining Requests

Claude：

- Usage
- Quota
- Reset Time

Widget 根据真实 Capability 决定显示内容。

---

## 八、数据来源

数据可以来自：

- 官方 API
- API Key 账户接口
- Web 账户接口
- Cookie / Session
- OAuth
- 第三方平台接口
- App 本地统计
- App 本地计算

必须能够区分：

- 服务端真实数据
- 本地统计数据
- 本地计算数据
- 估算数据

不得混淆数据来源。

---

## 九、Local-first 原则

本项目默认采用：

Local-first Architecture

数据流：

用户手机
   │
   ├── AI 官方 API
   ├── 第三方 API 平台
   ├── Web 账户接口
   └── OAuth / Web 登录

默认不存在：

用户手机
   ↓
开发者服务器
   ↓
AI 平台

项目原则：

- 不建立用于收集用户 API Key 的服务器
- 不建立用于收集用户 Cookie 的服务器
- 不上传用户登录 Session
- 不上传用户 API Key
- 用户请求直接从用户设备发送到目标服务
- API Key、Cookie、Session 默认保存在本机

注意：

App 为访问目标 AI 服务本身需要 Android 网络权限。

Local-first 指：

"不经过开发者自建服务器进行数据中转"。

---

## 十、认证方式

项目不得假设所有平台只能通过 API Key 认证。

长期支持：

- API Key
- WebView Login
- Cookie / Session
- OAuth
- Device Flow
- Manual Cookie Import

不同 Provider 可以支持一种或多种认证方式。

---

### API Key

主要用于：

- 模型调用
- `/v1/models`
- 官方余额接口
- 官方 Usage 接口

API Key 是当前基础认证方式。

增加 Web 登录能力后，也不得删除 API Key 能力。

---

### WebView Login

适用于：

- API Key 无法获取余额
- 账户数据只存在于网页控制台
- 需要网页登录 Session

标准流程：

用户点击"连接账户"
        ↓
App 内打开官方网页登录页
        ↓
用户主动完成登录
        ↓
WebView 获得 Cookie / Session
        ↓
本机保存登录状态
        ↓
访问账户数据接口
        ↓
获取余额 / 用量 / 套餐

---

### Cookie / Session

同一个模型实例可以同时使用：

API Key
+
Web Account Session

例如 MiMo：

API Key：
用于模型识别和 API 调用。

Web Session：
用于获取 API Key 无法返回的账户余额。

两种数据源可以互相补充。

---

### OAuth

平台如果提供标准 OAuth，应优先支持官方授权方式。

不得为了统一结构强行转换成 API Key 或 Cookie。

---

### Manual Cookie Import

可以作为高级用户备用方式。

普通用户体验优先采用：

WebView 登录
→ 自动保持登录状态

不得要求普通用户必须手动查找和复制 Cookie。

---

## 十一、网页登录状态

产品不得使用：

- 永久 Cookie
- 永久授权

等不准确表述。

统一使用：

- 连接账户
- 网页授权
- 已连接
- 保持登录状态
- 登录已失效
- 重新连接

Cookie / Session 的有效期由目标平台决定。

登录失效时：

- 不删除历史成功数据
- 不清空已有缓存
- 显示明确状态
- 提供重新连接入口

---

## 十二、平台与协议扩展体系

项目目标不是提前写死所有平台。

长期支持范围分为三层。

---

### 第一层：通用协议

优先支持：

OpenAI-Compatible

例如：

GET /v1/models

POST /v1/chat/completions

对于兼容 OpenAI API 的服务，至少可以实现：

- API Base 配置
- API Key 配置
- 模型发现
- 模型选择
- Test Connection
- 服务状态检测

即使暂时无法查询余额，也不得判定该服务"不支持"。

---

### 第二层：平台家族

未来支持识别共享同一种后台架构的平台。

例如：

- New API
- One API
- Sub2API
- LiteLLM
- 其他通用中转系统

原则：

一个 Adapter 应尽可能适配一种协议或平台家族，而不是只适配一个域名。

例如：

站点 A
站点 B
站点 C

如果都属于 New API：

只需要：

NewApiAdapter

不得分别创建：

SiteAAdapter
SiteBAdapter
SiteCAdapter

---

### 第三层：特殊 Provider

无法通过通用协议或平台家族适配时，可以增加专用 Provider。

例如：

- MiMo
- DeepSeek
- Claude
- Gemini
- OpenAI
- Kimi
- 豆包
- MiniMax
- Cursor
- Copilot

特殊 Provider 只负责特殊能力。

不得破坏通用协议层。

---

## 十三、Adapter 选择原则

Adapter 可以依据：

- API Base
- API 协议
- 特征接口
- 返回格式
- Provider 类型
- 用户明确选择的平台类型

禁止依据：

- Widget 卡片名称
- 用户自定义名称
- 显示标题
- 单纯的平台 Logo

同一种协议允许多个模型共用同一个 Adapter。

例如同一个 OpenAI-Compatible Base：

可以同时提供：

- kimi-k2.6
- deepseek-v4-pro
- qwen-max
- glm-4.5

无需因为模型名称不同创建新的 Adapter。

---

## 十四、Widget 产品定位

Widget 是本产品的核心体验之一。

Widget 不应只是一个固定尺寸的静态面板。

长期目标：

Responsive Adaptive Widget

即：

用户调整 Widget 尺寸时：

- 布局变化
- 信息密度变化
- 模型数量变化
- 显示字段变化

不得只是简单整体放大或缩小。

---

## 十五、Widget V1 布局

V1 第一阶段支持：

### 4×2

显示：

2 个模型

特点：

- 信息更加完整
- 每个模型拥有较大的显示空间

---

### 4×3

显示：

4 个模型

特点：

- 多模型总览
- 充分利用桌面空间

---

Widget 必须充分利用可用空间。

禁止出现明显无意义的大面积空白。

---

## 十六、Widget 长期响应式方向

未来逐步支持更多尺寸，例如：

### 1×2
单模型 Focus 模式。

### 2×2
单模型详情或双模型简洁模式。

### 4×2
多模型 Dashboard。

### 4×3
Expanded Dashboard。

实际代码不得完全依赖 Launcher 的固定"格数"。

应根据：

- 实际可用宽度
- 实际可用高度
- Widget Options
- 尺寸断点

决定最终布局。

---

## 十七、Widget 自定义方向

长期支持用户选择：

- 显示哪些模型
- 模型排列顺序
- 不同尺寸显示哪些模型
- 优先显示哪些字段

例如：

1×2：
只显示 DeepSeek。

2×2：
显示 DeepSeek + MiMo。

4×3：
显示四个模型。

可选字段必须来自 Provider 的真实 Capability。

不得允许用户选择平台实际不存在的虚假数据。

---

## 十八、Widget 显示模式

长期预留：

### Smart

系统根据 Widget 尺寸自动决定内容。

### Fixed Models

用户指定显示哪些模型。

### Custom Fields

用户指定优先显示哪些字段。

这些能力逐步实现。

不得要求 V1 一次性全部完成。

---

## 十九、用户体验原则

产品风格：

- 专业
- 友好
- 有温度
- 清晰
- 克制

所有普通用户提示默认使用中文。

每条错误提示尽量回答：

① 发生了什么

② 为什么

③ 怎么解决

例如：

🌐 无法连接服务器

可能原因：
API Base 地址当前无法访问。

建议：
请检查地址后重新尝试。

默认隐藏：

- Java Exception
- StackTrace
- 原始英文异常
- 大段服务器返回内容

用户主动查看技术详情时，再显示：

- HTTP 状态码
- 返回内容
- 异常信息

---

## 二十、状态文案原则

成功：

- 已连接
- 数据已同步
- 账户状态正常

加载：

- 正在同步…
- 正在连接…
- 正在获取账户信息…

失效：

- 登录状态已失效
- 请重新连接账户

暂不支持：

- 当前服务暂未提供此项数据

禁止使用：

余额：0

代替：

无法获取余额

---

## 二十一、UI 目标

产品最终目标不是：

工程 Demo

而是：

成熟、精致、可以正式发布的消费级产品。

整体视觉方向：

- 高级
- 克制
- 现代
- 科技感
- 清晰
- 长期耐看

---

### V1

重点：

- 信息优先
- 简洁清晰
- 结构正确
- 功能稳定

---

### V2

逐步增加：

- 半透明
- 大圆角
- 高质量图标
- 更统一的卡片体系
- 更成熟的页面结构
- 自然的状态反馈

---

### V3

目标：

Apple Glass / 高级玻璃风格

但必须：

- 在 Android 能力范围内实现
- 不牺牲性能
- 不牺牲 Widget 稳定性
- 不为了视觉效果伪造系统能力

---

## 二十二、动画方向

产品允许使用动画，但必须：

克制、自然、有意义。

推荐：

- 数据更新反馈
- 数字变化
- 内容淡入
- 进度变化
- App 内卡片重排
- 页面切换
- 状态切换
- 点击反馈

禁止：

- 高频闪烁
- 大量持续动画
- 影响桌面续航
- 为炫技牺牲性能
- 将 Android Widget 无法实现的效果宣称为已支持

---

## 二十三、产品成熟度目标

项目最终必须同时满足：

第一层：
能运行

第二层：
功能正确

第三层：
容易使用

第四层：
视觉成熟

最终目标是：

数据能力
+
扩展能力
+
使用体验
+
视觉品质

共同达到成熟产品标准。

---

## 二十四、长期核心原则

项目始终坚持：

- 不为了统一而伪造数据
- 不把模型和平台混为一谈
- 不把一个中转站等同于一个 Adapter
- 同协议优先复用
- 特殊能力按扩展方式增加
- 用户凭据默认保存在本机
- Widget 根据真实数据能力展示内容
- 功能逐步实现，不要求一次完成长期架构
- 最终做成一个真正漂亮、成熟、可长期扩展的 AI Dashboard

---

## Stage 7A-1：Adapter 凭据输入统一

本阶段不新建第二套后端，不改变现有 Adapter 的职责。

现有 Adapter 继续作为平台差异统一层，负责：

- HTTP 请求
- 平台协议
- JSON 解析
- 错误转换
- WidgetData 标准化

本阶段只解决一个问题：

`PlatformAdapter.fetchData()` 不再把 API Key、Bearer Token、Cookie 混在同一个 `apiKey` 参数中。

统一输入对象：

```text
AdapterRequest
├─ apiBase
├─ modelApiKey
├─ modelName
├─ backgroundAuthType
└─ backgroundCredential
```

认证规则：

- 模型 API Key：用于 `/v1/models`、模型调用、模型用量等接口。
- Bearer Token：通过网页登录获得，用于账户后台余额、套餐、Profile 等接口。
- Cookie：通过网页登录获得，用于账户后台余额、套餐、Profile 等接口。
- Billing、Usage、Balance、Profile 属于数据接口类型，不属于新的认证方式。

平台组合示例：

- Kimi / New API：模型 API Key。
- DeepSeek 官方：模型 API Key。
- MiMo：模型 API Key + Cookie。
- 爱黄牛：模型 API Key + 后台 Bearer Token。

兼容要求：

- 保留现有 API Key、Cookie、Bearer Token 的存储格式。
- 覆盖安装后不得要求 MiMo 重新登录。
- 不改变现有余额、用量、缓存和 Widget 展示结果。
- 不新增平台，不修改 UI，不修改 Widget 布局。
- Adapter 仍统一通过 AdapterFactory 路由。

---

## Stage 7A-2：后台授权凭据仓库统一

本阶段继续沿用现有 Adapter、AdapterRequest 和 BackgroundAuthConfig，不新建第二套后端。

本阶段只解决一个问题：

后台 Cookie / Bearer Token 的 JSON 保存和读取不再分散在 Widget Provider 与各网页登录 Activity 中，统一通过 BackgroundAuthRepository 处理。

统一入口：

```text
BackgroundAuthRepository
├─ load(prefs, instanceKey)
├─ save(prefs, instanceKey, config)
└─ clear(prefs, instanceKey)
```

存储兼容规则：

- SharedPreferences 名称继续由调用方决定。
- 授权键名继续使用 `${instanceKey}_auth`。
- MiMo 继续使用现有 `api_config / MiMo_auth`。
- JSON 字段继续使用 `authType`、`authValue`、`enabled`、`updatedAt`。
- 覆盖安装后不得要求 MiMo 重新登录。

本阶段不做：

- 不新增平台。
- 不修改 AdapterRequest。
- 不修改任何 Adapter 的数据接口。
- 不修改配置页面。
- 不修改 Widget 布局、缓存和响应式逻辑。
- 不改变 Cookie / Bearer Token 的获取方式。

### Stage 7A-2 补充修复：配置页统一使用授权仓库

源码复核发现配置页仍保留独立的后台授权 JSON 读写函数，未完全经过 `BackgroundAuthRepository`。

本补充修复只完成：

- 配置页读取后台授权时调用 `BackgroundAuthRepository.load()`。
- 配置页保存后台授权时调用 `BackgroundAuthRepository.save()`。
- 配置页清除后台授权时调用 `BackgroundAuthRepository.clear()`。
- 删除配置页内部重复的 JSON 序列化和反序列化函数。

不改变现有授权键名、JSON 格式、登录方式、Adapter、数据接口、Widget 布局和缓存逻辑。

---

## Documentation Baseline：开发日志与 AI 交接基线

本阶段不修改业务代码，只建立长期可回溯的开发记录和 AI 接手机制。

正式文档职责：

- `PROJECT.md`：产品目标、架构规则、阶段设计和确认范围。
- `AGENTS.md`：开发纪律、验证纪律、日志纪律和安全边界。
- `DEVELOPMENT_LOG.md`：按时间追加的开发流水和证据记录，原则上只追加不覆盖。
- `AI_HANDOFF.md`：当前状态快照，每个阶段完成时同步更新。

完成标准：

- 已回填可由 Git 提交、源码和用户验收记录支持的关键阶段。
- 无法证明的历史明确标记为“待核实”。
- 后续所有阶段必须同时维护开发日志和交接快照。
- 另一个 AI 不阅读历史聊天，也能定位当前基线、核心架构、已完成能力、风险和下一项任务。
- 本阶段未改业务代码，因此不要求编译或安装。
