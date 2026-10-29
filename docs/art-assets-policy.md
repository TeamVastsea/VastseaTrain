# 美术资源保护约定

## 铁律

`src/main/resources/assets/vastseatrain/models/block/rail/` 这个目录**属于美术**，其中的所有文件一律**禁止修改、禁止新增、禁止删除、禁止改名、禁止挪动**：

- `test.obj`、`test.mtl` 以及同目录的全部贴图（`gravel.png`、`concrete_silver.png`、`rail_sleeper_side.png`、`rail_sleeper_top.png`、`rail_fastener.png` 等）。

美术直接把导出的文件放进这个目录即可，程序侧不需要任何配合。**程序不得在该目录写入任何文件，包括 JSON 配置。**

## 程序侧配置放在哪里

所有程序配置都写在 `rail/` 目录**之外**：

```text
assets/vastseatrain/
  models/block/rail/          ← 纯美术资源，只读
  models/block/rail_core.json ← 程序配置：OBJ 加载、贴图映射、摆放变换
  models/block/rail_slab*.json
  blockstates/rail.json
  blockstates/rail_slab.json
```

`rail_core.json` 里：

- `model` 指向 `vastseatrain:models/block/rail/test.obj`；
- **不写 `mtl_override`**，交给 OBJ 自带的 `mtllib test.mtl` 相对解析；
- `textures` 段把 MTL 里的每个贴图文件名映射到实际资源路径。

## 兼容美术文件的具体方式

| 美术写法 | 程序如何兼容 |
| --- | --- |
| `mtllib test.mtl` | Forge 自动解析同目录的 MTL，程序不干预 |
| `map_Kd rail_sleeper_top.png` 等相对文件名 | Forge 解析成 `vastseatrain:rail_sleeper_top.png`，因此 `textures` 段逐项映射到 `vastseatrain:block/rail/rail_sleeper_top.png` |
| 贴图与 MTL 同目录 | 程序不搬动贴图，映射直接写 `block/rail/xxx.png` |
| 分组名（如 `rail_center_sleeper`） | 程序按美术原名引用，不做任何重命名 |

**美术新增或重命名贴图时**：美术把文件放好，程序只在 `rail_core.json` 的 `textures` 段补一行/改一行映射。这是程序侧文件，不触碰美术目录。

## 为什么不能往 MTL 里加内容

Forge 的 `ObjModel` 用 `InputStreamReader(stream, StandardCharsets.US_ASCII)` 读取 MTL。美术导出的纯英文 MTL 完全没问题；一旦有人往 MTL 里写中文注释，整份材质库解析失败，所有面变成紫黑棋盘格。所以 MTL 必须保持美术原样。

## 模型原点与摆放

`test.obj` 实测范围：

| 轴 | 范围 | 含义 |
| --- | --- | --- |
| X | −0.801 .. 0.801 | 纵向（钢轨方向） |
| Y | −1.000 .. 0.750 | 轨面 0.750，道砟底 −1.000 |
| Z | −2.141 .. 2.141 | 横向 |

程序在 `rail_core.json` 里只写一条 `translation: [0, 0.625, 0]`，让轨面落在方块顶面。Forge 不做坐标轴交换，南北朝向由 blockstate 的 `y` 旋转处理。

## 校验

```powershell
# 美术目录里除美术文件外不应出现任何程序文件
Get-ChildItem src\main\resources\assets\vastseatrain\models\block\rail
```
