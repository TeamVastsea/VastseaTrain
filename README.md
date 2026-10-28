# Vastsea Train

Vastsea Train 是一个基于 Create（机械动力）的 Minecraft 铁路模组开发项目。

## 开发环境

- Minecraft：1.20.1
- Forge：47.4.28
- Create：6.0.8-291
- Java：17
- Gradle：使用仓库内的 Gradle Wrapper 8.8

请在 IDEA 中打开项目根目录，并将 Gradle JVM 设置为 JDK 17。首次构建会自动下载 Forge、Minecraft、Create 及其开发依赖。

## 常用命令

```powershell
.\gradlew.bat build
.\gradlew.bat genIntellijRuns
```

游戏客户端由开发者在 IDEA 中手动启动；本项目不会在构建时启动游戏。
