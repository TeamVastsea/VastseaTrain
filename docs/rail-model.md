# 轨道美术加载

美术资源直接放进 `assets/vastseatrain/models/block/rail/`，程序**只读不写**。保护规则见 [`docs/art-assets-policy.md`](docs/art-assets-policy.md)。

## 目录划分

```text
assets/vastseatrain/models/block/
  rail/                     ← 美术目录，程序禁止写入
    test.obj / test.mtl     ← 默认外观（标准轨道）
    test2.obj / test2.mtl   ← 木枕路基外观
    *.png / *.jpg           ← 美术导出的贴图
  rail_core.json            ← 默认外观：直道配置（加载器、贴图映射、摆放）
  rail_curve_core.json      ← 默认外观：弯道配置
  rail_slab_core.json       ← 木枕路基：直道配置
  rail_slab_curve_core.json ← 木枕路基：弯道配置
  rail_mtl.mtl              ← 默认外观的材质覆盖文件（程序侧）
  rail_slab_mtl.mtl         ← 木枕路基的材质覆盖文件（程序侧）
assets/vastseatrain/textures/block/rail/
  *.png                     ← 图集占位/尺寸参照：尺寸由同名 PNG 的 .png.mcmeta 决定
  *.png.mcmeta              ← 声明 {"forge":{"loader":"vastseatrain:rail_art"}}，让图集从美术目录取图
```

美术贴图在运行时就地读取：图集加载 `textures/block/rail/<名字>.png` 时，如果
`models/block/rail/<名字>.png|jpg|jpeg` 存在，就用它解码、缩放到 `.mcmeta` 里写的尺寸后替换，
贴图名与 UV 全都不变。所以美术只改 `rail/` 目录 + 按 F3+T 即可。
注意 Forge 只按 `.mcmeta` 选择加载器（不按贴图名匹配），新增贴图时要补一个同尺寸的 `.png` 与 `.png.mcmeta`。

美术 MTL 里的 `map_Kd` 是相对文件名，直接交给加载器会被解析成 `minecraft:` 命名空间，
所以每套外观都在程序侧配一份 `*_mtl.mtl`，把 `map_Kd` 换成 `#名称`，再由根模型的
`textures` 块指到 `vastseatrain:block/rail/...`。

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

- **每段 1 个方块**：蓝图沿玩家视线方向逐格前推，每格只放一个直道方块（外观决定是 `rail` 还是 `rail_slab`），**不再并排铺 3 条轨道**。
- **模型自身横跨 3 格宽**（Z 方向 4.28 格，略微越界属正常观感）。
- **碰撞箱 3 格宽 × 1 格长 × 1/16 高**：由 `RailBlock#getCollisionShape` 按朝向给出越界形状（南北向沿 X 铺开、东西向沿 Z 铺开）。
- **整段破坏**：一次铺设登记为一段（`TrackSegments`），破坏任意一格即整段移除。

## 90° 弯道

弯道**不改动美术模型**，也不生成新的模型文件，只把同一份模型沿弧线重复摆放。

### 排布方式

**每一格只放一份完整模型**，只做平移 + 绕 Y 轴旋转（转到该处的切线方向）：

- 从入口格中心（`s = 0`，与直道完全重合）出发，沿中心线按约 1 格一步步推进；
  每一步的落点必须落在**尚未占用的格子**内部，且离格边至少 0.04 格。
- 落点所在格的先后顺序就是模型顺序，两个相邻落点之间的弧段就是那一格负责的范围，
  模型原点取这段弧的中点。接缝误差平均摊在每格自己身上，不会跑到邻格。
- 按固定角度（例如每 7.5°）摆放会让小半径时一格叠上好几份模型，看起来就是“碎块”；
  每格一份后，方块数 = 覆盖格数，R=2 的左弯只有 15 格。
- 落点搜索的范围是 1 ± 0.3 格（先找最接近 1 格的），所以小半径时也能一路走下去，
  不会出现“某格空着、某格两份”。
- 道床比中心线宽得多：最外侧那一圈格子（沿弧线每 3 cm 采样、向两侧扩到 1.45 格得到）
  也各补一份“最近弧点”的模型，否则弯道外缘会缺一块。

### 半径与轨距

- 圆心 = 入口格中心 + `R` × 弯心方向。`s = 0` 就是入口格中心、切线就是入口方向，与直道严丝合缝。
- 轨距半宽 `RailCurve.HALF_GAUGE = 0.645` 格（由美术模型钢轨位置 `|x| ≈ 0.5..0.79` 实测）。
- 左右转与出口方向都用坐标旋转计算，不用 `getClockWise` / `getCounterClockWise`
  （1.20.1 里这两个方法的**名字与方向相反**，按名字用会把左弯画成右弯）。
- 半径范围在 `VTConfig` 里：`minRadius 10` / `maxRadius 1024`，超过 `warnRadius 256` 时界面会提示方块数。

### 每格的数据

`rail_curve` 方块每格带一个 `RailCurveBlockEntity`：

| 字段 | 含义 |
| --- | --- |
| `Angle` | 绕 Y 轴旋转角（弧度），把模型局部 −X 转到该处切线 |
| `OriginX` / `OriginZ` | 模型原点在方块局部坐标里的位置（0..1） |
| `Style` | 这一格用的外观（`standard` / `slab`），客户端据此选弯道根模型 |
| `Radius` / `Left` | 参考信息，供提示与调试，不参与渲染 |

客户端 `RailCurveBakedModel` 在**区块烘焙时**（不是逐帧）读取方块实体，按下面的公式生成顶点，
结果缓存进区块网格，所以每格绘制成本与直道相同：

```text
x' = cosθ · p.x − sinθ · p.z + originX
y' = p.y + 0.5
z' = sinθ · p.x + cosθ · p.z + originZ
```

（`originX/originZ` 是该格模型原点在方块局部坐标里的位置，`θ` 是绕 Y 轴旋转角。）

> 为什么在 `getModelData` 里直接读方块实体、而不走 `ModelDataManager`：后者需要方块实体主动调用刷新，
> 存档重载或客户端刚收到方块数据时容易晚一拍，会让轨道先渲染成直道再跳变。

## 外观切换

蓝图里的“轨道模型”可以选择外观，**只影响之后新铺设的轨道**，已铺好的格子不改写。

- 每种外观有自己的直道方块（`rail` / `rail_slab`）：方块模型按状态静态烘焙，同一个方块无法在不同格上显示两套模型。
- 弯道方块 `rail_curve` 所有外观共用，外观名记在方块实体的 `Style` 上。
  `RailCurveObjLoader` 烘焙时会把**每一套外观**的弯道 JSON 都读进来，
  用直道加载器解析同一份 OBJ/MTL 得到通用面数据，渲染时按 `Style` 取用。
- 外观表见 `track/RailStyle.java`。新增一套外观：美术文件放进 `rail/`，
  补齐 `textures/block/rail/` 下的 PNG 副本、两个根模型 JSON 与 `*_mtl.mtl`，
  在 `RailStyle` 加一个枚举项、在 `VTBlocks` 注册一个直道方块即可，界面与网络不用改。

## 界面里的 3D 预览

`gui/TrackStyleScreen` 是蓝图界面的二级界面：左列是外观列表，右侧是 3D 预览。
左键拖动旋转、滚轮缩放、右键拖动平移（拖向右 → 模型向右、拖向下 → 模型向下）。

界面布局全部按当前窗口的可视尺寸（`Width`/`Height`，即游戏界面缩放之后的尺寸）现算，
不再对整块界面做第二次缩放，所以无论界面缩放设为自动/小/中/大，控件、文字与点击区域都对得上。

预览不复用烘焙好的方块模型（那里已经没有 UV），而是由 `client/render/RailPreviewGeometry`
直接读根模型 JSON + OBJ + MTL，按与游戏内相同的“UV 整数格重复”规则展开成三角面，
投影到屏幕后用 `RailPreviewRenderer` 直接画，不需要额外的渲染目标。