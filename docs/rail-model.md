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

### 加载器的三个硬性约束

- **注册名只写 path**：`RegisterTextureAtlasSpriteLoadersEvent#register(String, loader)` 内部是
  `new ResourceLocation(当前模组命名空间, name)`，会自己补上 `vastseatrain:`。
  因此只能传 `"rail_art"`；传 `"vastseatrain:rail_art"` 会拼成
  `vastseatrain:vastseatrain:rail_art`，启动时直接抛 `ResourceLocationException` 崩游戏。
- **扫描目录不能传带结尾 `/` 的路径**：1.20.1 的 `MultiPackResourceManager`
  （`listResources` / `listResourceStacks` 都会先调）有 `checkTrailingDirectoryPath`，
  遇到 `models/block/rail/` 直接抛 `IllegalArgumentException: Trailing slash in path`，
  而且是在资源重载时抛，表现为“卡在加载界面然后崩”。
  代码里因此用不带斜杠的 `models/block/rail` 做前缀匹配，再用带斜杠的前缀过滤
  （否则会顺带命中 `rail_core.json` 这类同前缀文件）。
- **占位 PNG 的宽高比必须和美术图一致**：`.mcmeta` 里只有 `animation.width/height`（帧尺寸），
  加载器把美术图缩放到占位图的**整张**尺寸，图集 UV 也只认这个尺寸。
  比如 `rail_sleeper_top` 美术图是 128×128，占位图与 `.mcmeta` 也都是 128×128；
  若占位图是 16×16 而 `.mcmeta` 写 16×16，美术图会被压扁。
  `src/test/.../RailArtProbe` 会把“占位尺寸 / 美术尺寸 / 宽高比”逐张列出来。

### mipmap 由原版重算，不要自己调

加载器把像素写进原版交来的 `spriteImage` 后直接返回，**不调** `increaseMipLevel`。
`SpriteLoader.stitch` 拼完图会对每张贴图调 `increaseMipLevel(图集的 mip 级别)`，
生成源就是 `byMipLevel[0]`（即同一张、已被替换像素的 `spriteImage`），所以 mipmap 自动基于新像素。
自己调 `increaseMipLevel(Integer.MAX_VALUE)` 反而会因 `MAX_VALUE + 1` 溢出成负数、
原版判断 `mipLevel + 1 > length` 为 false 而直接返回旧数组，等于什么都没做。

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
"transform": { "translation": [0.5, 0.0, 0.5] }
```

`[0.5, 0.0, 0.5]` = 直道模型原点的水平位置在方块中心，垂直方向不额外平移。四个直道/弯道根模型 JSON 都保留此值。
弯道实例的水平原点由 `CurvePlan` 按弧线算出，不能再叠加根 JSON 的 X/Z 平移；垂直顶点仍使用 OBJ 原始 Y 坐标。

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
- **选择与碰撞形状 3 格宽 × 1 格长 × 1/4 高**：由 `RailBlock#getShape` 和 `getCollisionShape` 按朝向给出越界形状（南北向沿 X 铺开、东西向沿 Z 铺开）；遮挡形状为空，避免地面侧面被错误剔除。
- **整段破坏**：一次铺设登记为一段（`TrackSegments`），破坏任意一格即整段移除。

## 自定义弯道

弯道**不改动美术模型**，也不生成新的模型文件，只把同一份模型沿弧线重复摆放。

### 排布方式

沿中心线等弧长采样摆放模型，角度为 1° 至 90°，单次弧长最多 1024 格，半径最多 32768 格。实例间距不大于 1 格，首尾实例都落在拟合后的弧线端点；不沿道床宽度复制整份模型。界面估算的是模型实例数，不是方块数。每份先按轨距中心切线放置，再独立变形左右钢轨：

- 采样间隔为 `弧长 / ceil(弧长)`，所以端点包含在内且间距均匀。
- 两根钢轨分别沿各自的内外侧弧长延展；同一角度增量下，钢轨弧长按其半径与中心线半径的比例变化。道床仍复用原模型，不额外增加模型实例。
- 端点相对入口格中心吸附到最近方块中心，拟合偏移以 smoothstep 沿全弯分布，入口和出口处的修正导数为零；容差超限时服务端拒绝铺设。
- 急弯时多个模型实例可能落入同一方块；方块实体按格汇总实例数据，不丢弃重叠格中的实例。
- 按固定角度（例如每 7.5°）摆放会让小半径时一格叠上好几份模型，看起来就是“碎块”；
- 方块占位仅沿中心线路径确定，不再为覆盖道床宽度额外扩展占位格。

### 半径与轨距

- 圆心 = 入口格中心 + `R` × 弯心方向。`s = 0` 就是入口格中心、切线就是入口方向，与直道严丝合缝。
- 轨距半宽 `RailCurve.HALF_GAUGE = 0.645` 格（由美术模型钢轨位置 `|x| ≈ 0.5..0.79` 实测）。
- 左右转与出口方向都按世界 `Z` 向南的坐标约定计算：面向东时左转朝北；不依赖 `getClockWise` / `getCounterClockWise`
  （1.20.1 里这两个方法的**名字与方向相反**，按名字用会把左弯画成右弯）。
- 铺设上限与弯道范围在 `VTConfig` 里：`curve.maxRadius` 限制直道段数，`curve.curveMaxRadius` 将弯道半径限制到 32768 格以内，`curve.minRadius 10` 限制弯道最小半径；模型实例达到 256 和 512 时界面会提示性能风险。

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
y' = p.y
z' = sinθ · p.x + cosθ · p.z + originZ
```

（`originX/originZ` 是弧线采样点相对所在方块的局部位置；根 JSON 的 X/Z 平移已由弧线原点取代，Y 方向不额外平移。）

> 为什么在 `getModelData` 里直接读方块实体、而不走 `ModelDataManager`：后者需要方块实体主动调用刷新，
> 存档重载或客户端刚收到方块数据时容易晚一拍，会让轨道先渲染成直道再跳变。

## 外观切换

蓝图里的“轨道模型”可以选择外观，**只影响之后新铺设的轨道**，已铺好的格子不改写。

- 每种外观有自己的直道方块（`rail` / `rail_slab`）：方块模型按状态静态烘焙，同一个方块无法在不同格上显示两套模型。
- 弯道方块 `rail_curve` 所有外观共用，外观名记在方块实体的 `Style` 上。
  `RailCurveObjLoader` 烘焙时会把**每一套外观**的弯道 JSON 都读进来，
  用直道加载器解析同一份 OBJ/MTL 得到通用面数据，渲染时按 `Style` 取用。
- 外观表见 `track/RailStyle.java`。下面列出新增一套外观实际涉及的美术资源、资源配置和注册代码。

### 新增一套外观文件清单（以 `test3` 为例）

下面假设新外观的直道方块注册名为 `rail_test3`，程序侧直道/弯道根模型名分别为 `rail_test3_core`、`rail_test3_curve_core`。这些名称由程序统一确定；美术交付 `test3.obj`、`test3.mtl` 和贴图即可，不要把程序配置放进美术目录。

```text
assets/vastseatrain/
  models/block/rail/                         # 美术只读目录
    test3.obj                                # 美术模型
    test3.mtl                                # 美术导出的材质列表
    <贴图名>.png|jpg|jpeg                    # MTL 引用的原图
  models/block/rail_test3_core.json          # 程序：直道加载器、OBJ、材质与贴图映射
  models/block/rail_test3_curve_core.json    # 程序：弯道加载器；也是本外观的独立配置
  models/block/rail_test3_mtl.mtl            # 程序：与 test3.mtl 材质名对应的覆盖文件
  blockstates/rail_test3.json                # 程序：直道方块四个朝向指向 rail_test3_core
  blockstates/rail_curve.json                # 已有共享文件；通常不为新外观新增方块状态文件
  textures/block/rail/<新贴图名>.png          # 仅新增图集贴图时需要：图集占位图
  textures/block/rail/<新贴图名>.png.mcmeta   # 仅新增图集贴图时需要：rail_art 加载器声明
  lang/zh_cn.json                            # 程序：方块、物品、外观显示名
  lang/en_us.json                            # 程序：方块、物品、外观显示名
  models/item/rail_test3.json                # 可拿取方块物品的物品模型（若提供物品）
  textures/item/rail_test3.png               # 可拿取方块物品的图标（若提供物品）
```

#### 必需的模型资源配置

- **两份根模型 JSON**：直道使用 `vastseatrain:rail_obj`，弯道使用 `vastseatrain:rail_curve_obj`；两者都指向 `models/block/rail/test3.obj` 和程序侧 `rail_test3_mtl.mtl`。各 JSON 的 `textures` 将 `#名称` 映射到 `vastseatrain:block/rail/<贴图名>`。四份既有根模型的 `transform.translation` 约定仍为 `[0.5, 0.0, 0.5]`；新增配置沿用同一约定，不改 OBJ 来补偿坐标。
- **材质覆盖 MTL**：逐项复制美术 MTL 的 `newmtl` 名称及 OBJ `usemtl` 对应关系，把相对路径 `map_Kd file.png` 改成 `map_Kd #texture_name`。MTL 必须是 ASCII；不要让 Forge 直接用美术 MTL 的相对 `map_Kd` 路径。
- **直道 blockstate**：新增方块要有自己的 `blockstates/rail_test3.json`，把 north/east/south/west 映射到新直道根模型并设置与其他直道相同的旋转。
- **弯道 blockstate**：所有外观共用 `blockstates/rail_curve.json` 和 `rail_curve` 方块。新外观仍需独立的弯道根模型 JSON，并在 `RailStyle` 中提供它的资源名，供弯道模型加载器按外观读取。

#### 程序注册与可选的物品资源

要让外观出现在蓝图列表并参与铺设，程序需在 `track/RailStyle.java` 增加枚举项（外观 ID、直道/弯道根模型名、粒子贴图 ID、直道方块名），并在 `registry/VTBlocks.java` 注册对应直道方块。GUI 列表和网络使用外观枚举自动生成，不需要单独改 GUI 或网络代码。新增外观的语言键为 `style.vastseatrain.<外观ID>`，需要同时写入 `lang/zh_cn.json` 和 `lang/en_us.json`；方块的 `block.*` 名称也应补齐。

蓝图铺设使用的直道方块不一定非要作为独立物品提供。若还要让玩家从创造物品栏单独拿取该方块，则还需在 `registry/VTItems.java` 注册 `BlockItem`，在 `registry/VTTabs.java` 加入物品，并增加 `models/item/<方块名>.json`、`textures/item/<方块名>.png` 和 `item.*` 语言键。曲线仍使用共用的 `rail_curve` 方块，不需为每种外观再注册一个曲线物品。

#### 新贴图和 `test3` 当前用图

OBJ 的材质通过程序侧 MTL 覆盖文件映射到纹理图集。若贴图名已经存在于 `textures/block/rail/` 的登记中，可以在根模型 JSON 复用对应的 `block/rail/<贴图名>` ID，无需重复建立占位 PNG 或 `.mcmeta`。若名称是新的，则需增加同名占位 PNG 与 `.png.mcmeta`，并在根模型 JSON 的 `textures` 中登记；`.mcmeta` 使用现有尺寸规则并声明 `forge.loader` 为 `vastseatrain:rail_art`。美术原图仍放在 `models/block/rail/`，PNG/JPG/JPEG 均可，加载器按 PNG、JPG、JPEG 顺序选择同名文件。

当前 `test3.mtl` 引用 `concrete_silver.png`、`gray3.jpg`、`gray1.jpg`、`rail_sleeper_top.png` 和 `rail_fastener.png`。这些贴图名已在项目现有轨道贴图集中使用；接入时可复用相应的图集 ID，不必为 `test3` 再复制一组不同名字的占位资源。`test3` 仍需要自己的程序侧 MTL 覆盖和根模型 JSON 映射，因为 MTL 材质名称及其贴图分配属于该模型配置。

简要分工：美术维护 `models/block/rail/` 内的 OBJ、MTL、贴图原图；程序维护该目录外的根模型 JSON、MTL 覆盖、blockstate、注册代码、语言、图集占位资源以及（若提供物品）物品图标和模型。资源重载（F3+T）可以检查贴图替换，但新增 blockstate、模型路径或注册项需要重新启动/重新构建以完整验证。

## 界面里的 3D 预览

`gui/TrackStyleScreen` 是蓝图界面的二级界面：左列是外观列表，右侧是 3D 预览。
左键拖动旋转、滚轮缩放、右键拖动平移（拖向右 → 模型向右、拖向下 → 模型向下）。

界面布局按当前 GUI 视口现算；视口无法容纳最小布局时，对整套布局和鼠标坐标同步等比缩放，
因此小窗口下控件、文字、点击区域与 3D 预览保持对齐。

预览不复用烘焙好的方块模型（那里已经没有 UV），而是由 `client/render/RailPreviewGeometry`
直接读根模型 JSON + OBJ + MTL，按与游戏内相同的“UV 整数格重复”规则展开成三角面，
投影到屏幕后用 `RailPreviewRenderer` 直接画，不需要额外的渲染目标。
