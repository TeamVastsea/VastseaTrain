# VastseaTrain

VastseaTrain 是面向 Minecraft 1.20.1 Forge 的独立轨道交通模组。核心功能不依赖 Create（机械动力）；安装 Create 时再启用独立兼容层。

当前已实现第一个纵向切片：创造物品栏“瀚海轨交”、轨道蓝图物品、三栏参数界面、直线轨道方块与服务器端整段铺设。曲线、道岔、电气化、列车、站台调度和社区资源导入尚未实现，产品目标、架构决策和分阶段验收见下列文档。

## 已实现（0.1 原型）

- 创造模式物品栏：`瀚海轨交`（暂定模组中文名），包含轨道蓝图、轨道、轨道道床。
- 轨道蓝图：手持右键空气打开三栏界面（参数 / 道床与预览 / 摘要）；右键地面按玩家朝向铺设直线轨道。
- 铺设参数：长度 1–256 段，轨距与道床可选；参数保存在物品上并同步回服务器。
- 铺设安全：服务端一次性校验全部占位（越界、被占用、下方悬空），任一失败则整体取消，不留半截轨道。
- 美术：`b1.obj` 经 `tools/convert_rail_obj.ps1` 换算到方块空间后随包发布，原始文件不参与构建。

已知限制：曲线与道岔未实现；轨距/道床目前只影响参数与摘要，尚未切换外观；侧向道床每格之间有 0.25 格接缝，等待美术提供专门的肩部件；所有结论来自构建与资源解析校验，尚未在游戏内实机验证。

## 项目文档

- [项目大纲](docs/project-outline.md)：定位、原则、系统边界和阶段目标。
- [开发方案与过程](docs/development-plan.md)：架构、依赖隔离、开发工作流和验收门槛。
- [美术对接文档](docs/art-handoff.md)：模型、贴图、动画、目录和交付检查项。
- [社区模型包规范](docs/community-content-spec.md)：无代码资源导入、manifest、校验和错误隔离。
- [模型清单 JSON Schema](schemas/community-model-manifest.schema.json)：社区模型 manifest v0.1 草案。
- [最小模型清单模板](examples/community-models/minimal/manifest.json)：可复制的最小示例。

## 开发环境

- Minecraft：1.20.1
- Forge：47.4.28
- Create：6.0.8-291（开发/兼容依赖，可选）
- Java：17
- Gradle：使用仓库内的 Gradle Wrapper 8.8

请在 IDEA 中打开项目根目录，并将 Gradle JVM 设置为 JDK 17。首次构建会自动下载 Forge、Minecraft 和开发依赖。发布包不内置 Create；`mods.toml` 将 Create 声明为可选依赖。

## 常用命令

```powershell
.\gradlew.bat build
.\gradlew.bat genIntellijRuns
```

游戏客户端由开发者在 IDEA 中手动启动；本项目不会在构建时启动游戏。提交前执行 `build`；构建成功后立即提交并推送，GitHub Actions 会对 `main` 和 PR 自动构建。

`reference/` 仅用于本地查阅第三方源码（如 Immersive Railroading），已加入 `.gitignore`，不会被提交。重新获取：

```powershell
git clone --depth 1 --branch master https://github.com/TeamOpenIndustry/ImmersiveRailroading.git reference/ImmersiveRailroading
```

## 轨道美术换算

美术提供的 `b1.obj` 以模型中心为原点、Y 向上、沿 +Z 延伸。项目内 `tools/convert_rail_obj.ps1` 把它换算到 Minecraft 方块空间：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\convert_rail_obj.ps1
```

换算规则：按目标段长缩放（默认 2 格），X/Z 居中到方块中心，并把道床顶面（原始 `Y=0.285666`）对齐到方块底面。结果是宽 0.748 格、高 0.818 格、长 2 格，道床向地下延伸约 0.6 格（被地面遮住），轨顶高出地面约 0.217 格。修改段长后重新运行脚本即可，不需要改模型 JSON 的 `transform`。

> 说明：美术模型的宽长比（1.6 : 4.28）与“3 格宽、1 格长”的口径不一致。当前按“一段跨 2 格、无重叠拼接”处理，实际轨距约 0.47 格。若需要 3 格宽的实心轨道，需要美术提供更宽的道床/肩部件或确认缩放口径。

