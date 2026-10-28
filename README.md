# VastseaTrain

VastseaTrain 是面向 Minecraft 1.20.1 Forge 的独立轨道交通模组。核心功能不依赖 Create（机械动力）；安装 Create 时再启用独立兼容层。

当前仓库是开发环境与项目基线，蓝图、GUI、轨道系统、电气化和社区资源导入尚未实现。产品目标、架构决策和分阶段验收见下列文档。

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
