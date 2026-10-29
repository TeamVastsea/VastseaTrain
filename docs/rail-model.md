# 轨道美术加载

轨道美术资源由美术直接放进 `assets/vastseatrain/models/block/rail/`，程序**只读、不写**。保护规则见 [`docs/art-assets-policy.md`](docs/art-assets-policy.md)。

## 目录划分

```text
assets/vastseatrain/models/block/
  rail/                     ← 纯美术资源，程序禁止写入
    test.obj
    test.mtl
    gravel.png
    concrete_silver.png
    rail_sleeper_side.png
    rail_sleeper_top.png
    rail_fastener.png
  rail_core.json            ← 程序配置：OBJ 加载、贴图映射、摆放变换
  rail_slab_left.json
  rail_slab_right.json
  rail_slab.json
  rail_filler.json
```

## 程序如何兼容美术文件

- 不写 `mtl_override`，交给 OBJ 自带的 `mtllib test.mtl` 相对解析。
- `map_Kd rail_sleeper_top.png` 这类相对文件名会被 Forge 解析成 `vastseatrain:rail_sleeper_top.png`，所以 `rail_core.json` 的 `textures` 段逐个映射：

```json
"textures": {
  "gravel": "vastseatrain:block/rail/gravel.png",
  "concrete_silver": "vastseatrain:block/rail/concrete_silver.png",
  "rail_sleeper_side": "vastseatrain:block/rail/rail_sleeper_side.png",
  "rail_sleeper_top": "vastseatrain:block/rail/rail_sleeper_top.png",
  "rail_fastener": "vastseatrain:block/rail/rail_fastener.png"
}
```

美术新增贴图时，程序只在 `rail_core.json` 补一行映射。

## 模型原点与摆放

`test.obj` 实测范围：

| 轴 | 范围 | 含义 |
| --- | --- | --- |
| X | −0.801 .. 0.801 | 纵向（钢轨方向），约 1.6 格 |
| Y | −1.000 .. 0.750 | 轨面 0.750，道砟底 −1.000 |
| Z | −2.141 .. 2.141 | 横向，约 4.28 格 |

`rail_core.json` 里只写 `translation: [0, 0.625, 0]`，让轨面落在方块顶面 `y = 1.875`。Forge 不做坐标轴交换，南北朝向由 blockstate 的 `y: 0/90/180/270` 处理。

## 占位与碰撞

代码不缩放：占位、碰撞、铺设按 **1 格长 × 3 格宽** 计算，模型越界部分允许重叠。

## 分组命名

分组名以美术导出的 OBJ 为准，程序只读取、不改名。见 [`docs/rail-group-naming.md`](docs/rail-group-naming.md)。
