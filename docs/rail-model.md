# 轨道美术加载

轨道美术来自 `C:\Users\MCTV\Documents\b1`（`b1.obj` / `b1.mtl` 与贴图），放进模组时**顶点几何不做任何改动**，只改材质指向与分组名：

- 资源目录：`src/main/resources/assets/vastseatrain/models/block/rail/`（`test.obj`、`test.mtl`、`test.json`、`test_bed.json`）。
- 贴图目录：`src/main/resources/assets/vastseatrain/textures/block/rail/`。

## 模型坐标约定

美术交付时的实际约定（以 `test.obj` 为准）：

| 项 | 值 |
| --- | --- |
| 前进方向 | `-X` |
| 原点 | 轨道段中心（**不是**方块上表面中心），轨顶 `y = 0.75` |
| 纵向（钢轨方向） | 约 1.6 格 |
| 横向（Z） | 约 4.28 格 |

因此模型在纵、横两个方向都约为“1 格 × 3 格”的两倍，属于美术为了观感的刻意外扩。**代码不做缩放**：占位、碰撞、铺设一律按 `1 × 3` 格计算，模型允许多出并互相重叠。

## 摆放与朝向

模型摆进方块坐标系只需要两条：

```json
"transform": {
  "origin": "center",
  "translation": [0, 0.5, 0],
  "rotation": [0, 90, 0]
}
```

- `origin: center`：GUI 编辑器的变换原点设在方块中心。
- `rotation: [0, 90, 0]`：把模型的 `-X` 转到方块的 `-Z`，即 `facing=north`。
- `translation: [0, 0.5, 0]`：Forge 按方块**角点**（`0..1`）应用变换，而模型按**段中心**建模，所以要抬高半格把原点对回段中心。

其余朝向由方块状态的 `facing` 乘 `y` 旋转（北 0°、东 270°、南 180°、西 90°）得到，不需要额外模型。

## 三格宽如何拼合

方块只能渲染属于自己那一格的部分，所以：

- 中心格（`rail`）渲染完整模型。
- 左格（`rail_slab`，`half=left`）与右格（`half=right`）渲染同一份完整模型，在 JSON 里沿轨道横轴翻转（`rail_slab_left.json` / `rail_slab_right.json` 的 `transform.rotation.z = 180`），把落在中心格里的部分镜像到各自格内。
- 补位半侧的选择与朝向有关：面板 `y` 旋转对左格（局部 Z = +1）和右格（局部 Z = −1）作用方向相反，`TrackPlacer` 按 `facing` 决定是否互换 `left` / `right`。

因为每格都渲染完整模型，相邻格之间必然有重叠，这是“模型越界”的正常结果。

## 材质

`test.mtl` 沿用美术的材质参数，只把 `map_Kd` 换成带命名空间的资源路径。两条硬性约束：

1. **MTL 必须是纯 ASCII**。Forge 的 `ObjModel` 用 ASCII 逐行读取，注释里出现中文等非 ASCII 字节会导致材质解析失败、整片紫黑棋盘格。
2. **贴图文件名只能用 `[a-z_/-]`**，不能带数字。`UnbakedGeometryHelper` 的正则不含数字，`1.png` 之类会解析失败。

当前映射：

| MTL 材质 | 用途 | 贴图 |
| --- | --- | --- |
| `Material_#80` | 道砟 | `gravel` |
| `Material_#81` | 轨枕 | `concrete_silver` |
| `Material_#82` | 扣件 | `rail_fastener` |
| `Material_#83` / `#84` | 钢轨 | `rail_sleeper_side` / `rail_sleeper_top` |

UV：模型的 `vt` 坐标超出 `0..1`（按世界尺寸而不是 UV 空间导出），平铺依赖贴图采样器的 `Repeat` 寻址，因此 `flip_v` 保持 `false`、`automatic_culling` 保持 `false`（面朝内时仍然可见）。

## 分组命名

见 [`docs/rail-group-naming.md`](docs/rail-group-naming.md)：道砟 `rail_full_ballast`、轨枕 `rail_center_sleeper`、扣件 `rail_center_fastener`、钢轨 `rail_left_rail` / `rail_right_rail`。

美术重新导出时，只要保持上面的坐标约定、纯 ASCII 的 MTL 和字母贴图名，覆盖 `test.obj` / `test.mtl` 即可，不需要改代码或 JSON。
