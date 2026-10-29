# 美术资源保护约定

## 铁律

`src/main/resources/assets/vastseatrain/models/block/rail/` 是**美术目录**。

美术直接把导出的文件放进去（当前为 `test.obj`、`test.mtl` 和 5 张贴图）。**程序永不写这个目录**：不新增、不修改、不删除、不改名、不挪动任何文件，**包括不往里面放 JSON 配置**。

## 程序侧文件都在目录外

```text
assets/vastseatrain/
  models/block/rail/          ← 美术目录，只读
    test.obj
    test.mtl
    gravel.png / concrete_silver.png / rail_sleeper_side.png /
    rail_sleeper_top.png / rail_fastener.png
  models/block/rail_core.json        ← OBJ 加载、材质覆盖、贴图映射、摆放
  models/block/rail_mtl.mtl          ← 材质覆盖文件（程序侧）
  models/block/rail_slab*.json
  blockstates/rail.json / rail_slab.json
```

## 为什么必须用 `mtl_override`（材质错乱的根因）

Forge 解析 MTL 里的 `map_Kd` 用的是这段正则（`UnbakedGeometryHelper.FILESYSTEM_PATH_TO_RESLOC`）：

```java
Pattern.compile("(?:.*[\\\\/]assets[\\\\/](?<namespace>[a-z_-]+)[\\\\/]textures[\\\\/])?(?<path>[a-z_\\\\/-]+)\\.png");
```

关键点：

1. **命名空间只能从路径里的 `/assets/<ns>/textures/` 捕获**。美术写的是相对文件名 `rail_sleeper_side.png`，路径里没有 `assets/<ns>/textures/`，于是 `namespace == null`，结果解析成 **`minecraft:rail_sleeper_side`** —— 原版根本没有这张贴图，所以渲染成紫黑棋盘格。
2. `gravel.png` 恰好解析成 `minecraft:gravel`，原版有这张图，所以**只有道砟"看起来是对的"（其实用的是原版砂砾）**，其余材质全是丢失贴图。这就是"材质混乱"。
3. JSON 里的 `textures` 段**不会**被用来解析 `map_Kd`。只有 `map_Kd` 以 `#` 开头时，Forge 才会走 `owner.getMaterial("#name")` 查表。

因此程序侧这样做（不碰美术文件）：

- `rail_core.json` 写 `"mtl_override": "vastseatrain:models/block/rail_mtl.mtl"`，`mtl_override` 是 ResourceLocation，**要带完整路径和 `.mtl` 后缀**（`ObjLoader.loadMaterialLibrary` 直接 `manager.getResource(location)`，不会自动补后缀）。
- `rail_mtl.mtl` 复制美术的材质名（`Material_#80`…`#84`）与参数，只把 `map_Kd` 改成 `#ballast`、`#sleeper_side` 等 `#` 引用。
- `rail_core.json` 的 `textures` 段把 `#name` 映射到 `vastseatrain:block/rail/xxx.png`，指向美术目录里的原图。

美术新增贴图或改材质名时，程序只改 `rail_mtl.mtl` 和 `rail_core.json` 两个程序侧文件。

## MTL 必须是纯 ASCII

`ObjTokenizer` 用 `InputStreamReader(stream, StandardCharsets.US_ASCII)` 读取 MTL，遇到非 ASCII 字节直接抛异常，整份材质库加载失败 → 全部紫黑。美术导出的 MTL 是纯英文，安全；**程序侧的 `rail_mtl.mtl` 也不得写中文**。

## 模型原点与摆放

`test.obj` 实测范围：X −0.801..0.801（纵向）、Y −1.000..0.750（轨面 0.750，道砟底 −1.000）、Z −2.141..2.141（横向）。

按"模型原点与方块上表面中心重合"的要求，`rail_core.json` 写：

```json
"transform": { "translation": [-0.5, 0.5, 0.5] }
```

`[0, 0.5, 0]` 让模型原点落在方块上表面中心；`[-0.5, 0, 0.5]` 是美术确认的额外微调量。

## 校验

```powershell
# 美术目录应只包含美术文件
Get-ChildItem src\main\resources\assets\vastseatrain\models\block\rail
```
