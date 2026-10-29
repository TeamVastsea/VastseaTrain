# 轨道美术加载

美术资源直接放进 `assets/vastseatrain/models/block/rail/`，程序**只读不写**。保护规则与原理见 [`docs/art-assets-policy.md`](docs/art-assets-policy.md)。

## 目录划分

```text
assets/vastseatrain/models/block/
  rail/                     ← 美术目录，程序禁止写入
    test.obj
    test.mtl
    gravel.png
    concrete_silver.png
    rail_sleeper_side.png
    rail_sleeper_top.png
    rail_fastener.png
  rail_core.json            ← OBJ 加载 + 材质覆盖 + 贴图映射 + 摆放
  rail_mtl.mtl              ← 材质覆盖文件（程序侧）
  rail_slab*.json / rail_filler.json
```

## 材质为什么不能直接用美术的 MTL

Forge 用正则 `(?:.*/assets/(?<ns>[a-z_-]+)/textures/)?(?<path>[a-z_/-]+)\.png` 解析 `map_Kd`：

- 美术写的是相对文件名（如 `rail_sleeper_top.png`），路径里没有 `assets/<ns>/textures/`，命名空间捕获为空 → 解析成 `minecraft:rail_sleeper_top`，原版没有 → 紫黑棋盘格；
- `gravel.png` 正好解析成 `minecraft:gravel`，原版有，所以只有道砟"看似正常"；
- JSON 的 `textures` 段只在 `map_Kd` 以 `#` 开头时才生效。

所以程序提供一个 `rail_mtl.mtl` 覆盖文件（复制美术的材质名与参数，`map_Kd` 改成 `#ballast`、`#sleeper_side`…），再用 `rail_core.json` 的 `textures` 段把这些名字映射到美术目录里的原图。

`mtl_override` 需要写完整的 ResourceLocation（带 `models/block/` 前缀和 `.mtl` 后缀），Forge 不会自动补。

## 模型原点与摆放

`test.obj` 实测：X −0.801..0.801（纵向）、Y −1.000..0.750（轨面 0.750）、Z −2.141..2.141（横向）。

```json
"transform": { "translation": [-0.5, 0.5, 0.5] }
```

- `[0, 0.5, 0]` 让模型原点与方块上表面中心重合；
- `[-0.5, 0, 0.5]` 是额外微调。

## 占位与碰撞

占位、碰撞、铺设按 **1 格长 × 3 格宽** 计算，模型越界部分允许重叠。
