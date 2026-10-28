# 社区模型包规范（草案）

## 1. 版本与目标

规范版本：`0.1.0-draft`。本文件和 `schemas/community-model-manifest.schema.json` 先确定字段契约；加载器、GUI 注册表、离线校验器及导入向导尚未实现。发布稳定 API 前允许调整草案，但调整时必须同步 Schema、模板和迁移说明。

目标是让普通作者仅通过资源包/数据声明交付模型，也让 Java 模组可注册更复杂行为。数据内容不可包含脚本、动态类名、反射入口或任意代码。

## 2. 包目录与装载

```text
assets/<namespace>/community/<type>/<id>/manifest.json
assets/<namespace>/models/community/<type>/<id>/...
assets/<namespace>/textures/community/<type>/<id>/...
assets/<namespace>/animations/community/<type>/<id>/...
assets/<namespace>/previews/community/<type>/<id>/...
```

资源包负责 Minecraft 资源（模型、纹理、动画和预览）；数据包可提供被白名单 Schema 接受的配方/样式/线路配置，但不能单独向客户端资源图集注入模型和贴图。若游戏版本不支持在数据包中放置某类文件，必须经 ResourceManager 扩展或打包为资源包，不宣称纯数据包可加载任意几何。

扫描时从已启用资源栈读取 manifest。重复 `namespace:id` 按明确优先级处理并报告冲突；单个条目解析失败只禁用该条目，不能终止整次扫描。资源重载时生成新注册快照，成功后原子替换。

## 3. Manifest 字段

JSON Schema 是机器校验的权威格式。顶层必需字段：

| 字段 | 含义 |
| --- | --- |
| `id` | namespace 内唯一小写标识 |
| `namespace` | 资源命名空间，须与资源归属一致 |
| `type` | `track`、`track_part`、`train`、`catenary`、`platform`、`signal`、`schedule` 或 `custom` |
| `displayName` | GUI 显示名，可本地化前先提供默认文本 |
| `author` | 作者/组织名，必填 |
| `license` | SPDX 标识或清楚的自定义许可说明，必填 |
| `version` | 模型包语义化版本 |
| `apiVersion` | 本规范兼容版本 |
| `supportedModVersions` | 支持的 VastseaTrain 版本范围 |
| `dependencies` | 其他社区模型 ID 或可选模组依赖 |
| `model` | 主模型资源 ID |
| `textures` | 贴图资源 ID 列表 |
| `animations` | 动画资源 ID 列表，可为空 |
| `preview` | 预览资源 ID |
| `tags` | 搜索、分类和收藏旁的可筛选标签 |
| `parameters` | 对应 `type` 的专用参数 |

命名约束：namespace 与 id 使用小写字母、数字、下划线、点和连字符；资源路径另允许 `/`。所有资源 ID 必须指向声明 namespace 或显式声明的依赖 namespace。禁止绝对路径、路径回退和包外文件引用。

## 4. 类型参数约定

- `track`：长度（m）、轨距（mm）、端点、曲线半径/角度、道岔路线、可动部件、护轨、超高、碰撞/占位。
- `track_part`：部件角色、重复间距、安装偏移、适用轨型。
- `train`：车辆尺寸、前向、转向架位置、车钩/连接点、车门/受电弓挂点、碰撞箱、动画状态、建议物理参数和声音挂点。
- `catenary`：高度、跨距、悬挂/导线连接点、支柱/绝缘子挂点、张力/下垂参数。
- `platform`、`signal`、`schedule`：安装朝向、交互点、显示面、场景标签和数据接口标识。
- `custom`：必须提供可忽略未知字段时仍安全的声明式参数；核心不解释未知行为。

类型参数要有默认值、单位、范围和版本兼容说明。Schema 先校验通用形状，游戏侧再执行类型/跨字段校验。

## 5. 导入与注册工作流

### 无代码资源包路径

作者复制模板、放入资源、填写 manifest，经离线校验后放进资源包。重载资源时扫描、校验并更新注册表；GUI 按类型/namespace/标签/作者展示可用条目。

### 数据包路径

只承载被允许的数据定义、样式预设和参数配置，并引用已加载的资源 ID。不能执行代码或声明任意 Java 类。

### 游戏内导入/预览

目标向导需显示发现的包、必填字段、缺失资源、版本/依赖问题、预览和最终注册状态。错误提示必须包含 namespace、模型 ID、资源位置和可操作修复建议。该向导尚未实现。

### Java API 路径

复杂行为由编译期依赖 VastseaTrain API 的模组实现。API 注册器必须在统一校验/命名空间规则内发布模型和行为；API 版本采用语义化版本，重大破坏变更提供迁移层。具体接口待首批数据模型稳定后另行文档化。

## 6. 校验、错误隔离与安全

建议校验顺序：JSON 语法 -> Schema -> ID/路径安全 -> 版本范围 -> 依赖闭包 -> 资源存在性/格式 -> 类型参数 -> 性能预算 -> 预览可用性。

- 校验结果按模型条目记录；一条失败不影响同包其他合法条目，除非包 manifest 本身不可枚举。
- 日志包含错误代码、namespace、ID、字段 JSON Pointer、资源位置和根异常。
- 防止 ZIP 炸弹、无限递归 JSON、超深结构、超大纹理、巨型几何和过多动画；先限制输入，再解码。
- 用安全路径解析，拒绝 URL、本地绝对路径、`..`、NUL 和大小写冲突。
- 模型不可信；不从模型包执行脚本/字节码，不依 manifest 类名做反射加载。
- 性能上限由实测决定并可配置；每项上限必须在 UI/文档中可解释。

## 7. 版本兼容和分发

模型包必须声明 `apiVersion`、自身 `version` 和 `supportedModVersions`。不支持的主 API 版本禁用该条目并说明原因；较旧但可迁移的字段提供明确迁移提示。可从资源包 zip、模组 jar 或社区索引分发，分发页保留作者和许可信息。

`license` 可填写 SPDX 标识（如 `CC-BY-4.0`）或明确的自定义许可名；不能用“未知”“待定”通过正式发布校验。社区索引是可选目录，不影响离线包使用。

## 8. 示例与模板

- 最小示例：`examples/community-models/minimal/manifest.json`。
- 规范 Schema：`schemas/community-model-manifest.schema.json`。
- 后续补充：直轨+枕木+道砟组合、接触网完整示例、站台/信号示例及列车示例。

当前示例只表达字段格式，不包含可在游戏中加载的模型资产；不能把它作为已实现导入器的证明。
