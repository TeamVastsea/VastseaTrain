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
  rail_core.json            ← 程序配置：加载器、贴图映射、摆放
  rail_mtl.mtl              ← 材质覆盖文件（程序侧）
```

## 为什么需要自定义加载器 `vastseatrain:rail_obj`

`forge:obj` 在 1.20.1 上有两个坑，都会让美术模型在游戏里“看起来不对”。程序侧全部在烘焙阶段处理，**美术文件一个字节都不用改**。

### 1. UV 越界 → 花屏（“只有钢轨材质正确”的真因）

实测各分组的 UV 范围：

| 分组 | UV 范围 | 是否在 0..1 |
| --- | --- | --- |
| `rail_left_rail` / `rail_right_rail` | u 0..1，v 0.405..0.595 | ✅ |
| `rail_full_ballast` | u −1.641..2.339，v −1.641..2.641 | ❌ |
| `rail_center_sleeper` | u −0.780..1.780，v −0.780..1.780 | ❌ |
| `rail_center_fastener` | u −1.285..2.285，v −1.299..2.299 | ❌ |

美术是按“1 个单位 = 1 张贴图”的世界投影导出的（道砟实测 `u = z + 0.5`、`v = y + 0.78`），靠渲染器的**重复采样**。Minecraft 的方块贴图是一整张图集，采样越界就会取到图集里相邻的其他方块贴图——这就是游戏里出现木板、石砖、花等原版图案的原因。钢轨 UV 恰好整个落在 0..1，所以只有钢轨正常。

程序侧解决：`RailObjGeometry` 把每个三角面按 UV 整数格**裁剪切块**，每块独立映射一整张贴图。等价于渲染器的 UV 重复采样，形状零损失。

### 2. 平移被朝向旋转 → 只有朝北时偏移正确

`forge:obj` 的做法是 `modelTransform.getRotation().compose(rootTransform)`，即 JSON 的 `transform.translation` 被放在方块朝向旋转**之内**。推导结果是：

```text
pos = Rot( p + t − (1,1,1) ) + (1,1,1)
```

朝北（Rot=I）时 `pos = p + t` 恰好正确，一旦朝向变化，0.5 的补偿会被转到别的轴上，轨道立刻偏半格到一格。

本加载器改为：

```text
pos = Rot(朝向) · p + translation
```

平移在旋转之后叠加，四个朝向完全一致。`translation` 的含义就是“模型原点落在方块局部坐标的哪个位置”。

## 模型数据与摆放

`test.obj` 实测包围盒：X −0.801..0.801（长度轴）、Y −1.000..0.750（轨面 0.750）、Z −2.141..2.141（宽度轴）。

```json
"transform": { "translation": [0.5, 0.5, 0.5] }
```

`[0.5, 0.5, 0.5]` = 模型原点落在方块中心。道砟底（Y −1.000）因此落到方块下表面，轨面（0.750）在方块顶面上方 0.25 格，与美术在 Outliner 里看到的比例一致。

## 朝向对照

模型长度轴是 X、前进方向 −X。`blockstates/rail.json` 用 `y` 旋转把长度轴对齐到前进方向：

| facing | y | 效果 |
| --- | --- | --- |
| north | 90 | 长度轴转到 Z（南北） |
| east | 180 | 长度轴保持 X（东西） |
| south | 270 | 长度轴转到 Z（南北） |
| west | 0 | 长度轴保持 X（东西） |

（旧配置是 0 / 270 / 180 / 90，整体差 90°，这也是“旋转偏移 90 度”的原因。）

## 占位、碰撞与铺设

- **每段 1 个方块**：蓝图沿玩家视线方向逐格前推，每格只放一个 `rail` 方块，**不再并排铺 3 条轨道**。
- **模型自身横跨 3 格宽**（Z 方向 4.28 格，略微越界属正常观感）。
- **碰撞箱 3 格宽 × 1 格长 × 1/16 高**：由 `RailBlock#getCollisionShape` 按朝向给出越界形状（南北向沿 X 铺开、东西向沿 Z 铺开）。
- **整段破坏**：一次铺设登记为一段（`TrackSegments`），破坏任意一格即整段移除。
