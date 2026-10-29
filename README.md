# VastseaTrain

VastseaTrain 是面向 Minecraft 1.20.1 Forge 的独立轨道交通模组。核心功能不依赖 Create（机械动力）；安装 Create 时再启用独立兼容层。

当前已实现第一个纵向切片：创造物品栏“瀚海轨交”、轨道蓝图物品、三栏参数界面、直线轨道方块与服务器端整段铺设。曲线、道岔、电气化、列车、站台调度和社区资源导入尚未实现，产品目标、架构决策和分阶段验收见下列文档。

## 已实现（0.1 原型）

- 创造模式物品栏：`瀚海轨交`（暂定模组中文名），包含轨道蓝图、轨道、轨道道床。
- 轨道蓝图：手持右键空气打开三栏界面（参数 / 道床与预览 / 摘要）；右键地面按玩家朝向铺设直线轨道。
- 铺设参数：长度 1–256 段，轨距与道床可选；参数保存在物品上并同步回服务器。
- 铺设安全：服务端一次性校验全部占位（越界、被占用、下方悬空），任一失败则整体取消，不留半截轨道。
- 美术：`test.obj` / `test.mtl` 原样加载（材质为相对路径），坐标适配只写在模型 JSON 的 `transform` 中，模型文件未做任何修改。

已知限制：曲线与道岔未实现；轨距/道床目前只影响参数与摘要，尚未切换外观；侧格与中心格共用同一张道床贴图，拼接缝尚未单独制作；所有结论来自构建与资源解析校验，尚未在游戏内实机验证。

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

## 轨道美术加载

轨道模型直接使用美术提供的 `test.obj` / `test.mtl`（`src/main/resources/assets/vastseatrain/models/block/rail/`），**模型文件不做任何修改**：

- 模型前进方向为 -X，横向（Z）3 格、纵向 1 格，轨顶 `y = 0.75`，道床向下延伸到方块下方被地面遮住。
- 材质使用相对路径（`map_Kd gravel.png` 等），贴图位于 `assets/vastseatrain/textures/block/rail/`。
- 坐标适配全部写在模型 JSON 里：`block/rail/test.json` 绕方块中心旋转 90°、抬高 0.75 格，把模型坐标系对齐到方块坐标系；`rail_core.json`（中心格）、`rail_slab_left.json` / `rail_slab_right.json`（左右道床格，旋转 180° 取对称半边）都从它继承。
- 中心格渲染完整模型，左右两格通过两套翻转后的 JSON 拼接同一段模型的另外两半；`test_bed.json` 隐藏钢轨分组 `RAIL_LEFT_b` / `RAIL_RIGHT_b`，侧格只渲染道床。

美术重新导出模型时，只要继续保持“前进方向 -X、3 格宽、轨顶 0.75”的约定，直接覆盖 `test.obj` / `test.mtl` 即可，不需要改代码或 JSON。
