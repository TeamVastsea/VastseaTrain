# 轨道美术加载

美术资源直接放进 `assets/vastseatrain/models/block/rail/`，程序**只读不写**。保护规则见 [`docs/art-assets-policy.md`](docs/art-assets-policy.md)。

## 目录划分

```text
assets/vastseatrain/models/block/
  rail/                     ← 美术目录，程序禁止写入
    test.obj
    test.mtl
    gravel.png / concrete_silver.png / rail_sleeper_side.png /
    rail_sleeper_top.png / rail_fastener.png
  rail_core.json            ← 程序配置：加载器、材质覆盖、贴图映射、摆放
  rail_mtl.mtl              ← 材质覆盖文件（程序侧）
  rail_slab*.json / rail_filler.json
```

## 材质问题：两个独立原因

### 1. 命名空间丢失（已解决）

Forge 用正则 `(?:.*/assets/(?<ns>[a-z_-]+)/textures/)?(?<path>[a-z_/-]+)\.png` 解析 `map_Kd`。美术写的是相对文件名，路径里没有 `assets/<ns>/textures/`，命名空间捕获为空 → 解析成 `minecraft:rail_sleeper_top` 之类不存在的贴图；`gravel.png` 恰好命中原版 `minecraft:gravel`。

所以程序用 `mtl_override` 指向自己的 `rail_mtl.mtl`（材质名、参数与美术 MTL 完全一致，只把 `map_Kd` 改成 `#ballast` 等 `#` 引用），再由 `rail_core.json` 的 `textures` 段映射到美术目录里的原图。

### 2. UV 越界（花屏主因，已解决）

这是“只有钢轨材质正确、其余全乱”的真正原因。实测各分组的面顶点 UV：

| 分组 | UV 范围 | 是否在 0..1 |
| --- | --- | --- |
| `rail_left_rail` / `rail_right_rail` | u 0..1，v 0.405..0.595 | ✅ |
| `rail_full_ballast` | u −1.641..2.339，v −1.641..2.641 | ❌ |
| `rail_center_sleeper` | u −0.780..1.780，v −0.780..1.780 | ❌ |
| `rail_center_fastener` | u −1.285..2.285，v −1.299..2.299 | ❌ |

美术按“1 格 = 1 张贴图”的方式导出 UV（世界单位，靠渲染器重复采样）。Minecraft 的方块贴图是一整张图集，采样越界会取到图集里**相邻的其他方块贴图**——这就是截图里出现木板、石砖、花等原版图案的原因。钢轨 UV 正好在 0..1，所以只有钢轨正常。

程序侧解决：自定义模型加载器 `vastseatrain:tiled_obj`（`TiledObjLoader` / `TiledObjGeometry`）。它先按原样烘焙 `forge:obj`，再把每个面按 UV 跨度切成若干小格，每格独占一份精灵内 `0..1`，等价于“重复采样”。OBJ 文件一个字节都不用改。

`rail_core.json` 里把 `loader` 设成 `vastseatrain:tiled_obj` 即可；其余参数与 `forge:obj` 一致。

## 模型原点与摆放

`test.obj` 实测：X −0.801..0.801（纵向）、Y −1.000..0.750（轨面 0.750）、Z −2.141..2.141（横向）。

```json
"transform": { "translation": [-0.5, 0.5, -0.5] }
```

- `[0, 0.5, 0]` 让模型原点与方块上表面中心重合；
- `[-0.5, 0, -0.5]` 是在此基础上追加的微调（纵向回退半格、横向回退一格）。

## 占位与碰撞

占位、碰撞、铺设按 **1 格长 × 3 格宽** 计算，模型越界部分允许重叠。
