package com.fastjim.vastseatrain.track;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

import java.util.Arrays;
import java.util.List;

/**
 * 可选的轨道外观（美术模型）。玩家在轨道蓝图里挑一套，之后<b>新铺设</b>的轨道就用这一套；
 * 已经铺好的轨道保持原样。
 * <p>
 * 每套外观对应三份 JSON：
 * <ul>
 *     <li>{@code blockstates/<block>.json}：直道方块的状态 → 直道根模型；</li>
 *     <li>{@code models/block/<straightModel>.json}：直道（加载器 {@code vastseatrain:rail_obj}）；</li>
 *     <li>{@code models/block/<curveModel>.json}：弯道（加载器 {@code vastseatrain:rail_curve_obj}）。</li>
 * </ul>
 * 弯道方块 {@link #CURVE_BLOCK} 所有外观共用：几何来自方块实体，外观名也记在里面，
 * 客户端再按名字找到对应的弯道根模型。
 * <p>
 * 新增一套外观：把美术文件放进 {@code models/block/rail/}，补齐上面的 JSON 与贴图，
 * 再在这里加一个枚举项、在 {@code VTBlocks} 注册一个直道方块即可，界面与网络无需改动。
 * <p>
 * 这里<b>不</b>持有方块实例：枚举在方块注册之前就会初始化。
 */
public enum RailStyle implements StringRepresentable {
    /** 双轨碎石道床（美术 {@code test.obj}）。 */
    STANDARD("standard", "rail_core", "rail_curve_core", "block/rail/gravel", "rail"),
    /** 单轨木枕碎石路基（美术 {@code test2.obj}），路基约 5 格宽。 */
    SLAB("slab", "rail_slab_core", "rail_slab_curve_core", "block/rail/medium2", "rail_slab");

    /** 弯道方块的注册名（所有外观共用）。 */
    public static final String CURVE_BLOCK = "rail_curve";

    private final String name;
    private final String straightModel;
    private final String curveModel;
    private final String particle;
    private final String blockName;

    RailStyle(String name, String straightModel, String curveModel, String particle, String blockName) {
        this.name = name;
        this.straightModel = straightModel;
        this.curveModel = curveModel;
        this.particle = particle;
        this.blockName = blockName;
    }

    public Component getDisplayName() {
        return Component.translatable("style.vastseatrain." + name);
    }

    /**
     * 直道方块对应的注册名。
     * <p>
     * 每种外观都有自己的方块：方块模型是按方块状态静态烘焙的，同一个方块无法在不同轨道格上
     * 显示两套模型，所以“选定外观”最终落到“放下哪一种方块”上。
     */
    public String blockName() {
        return blockName;
    }

    /** 直道根模型的资源路径（不含命名空间与 {@code .json}）。 */
    public String straightModel() {
        return straightModel;
    }

    /** 弯道根模型的资源路径（不含命名空间与 {@code .json}）。 */
    public String curveModel() {
        return curveModel;
    }

    /** 粒子贴图（破坏方块时飞出的碎屑），不含命名空间。 */
    public String particle() {
        return particle;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public RailStyle next() {
        RailStyle[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static RailStyle byName(String name) {
        for (RailStyle style : values()) {
            if (style.name.equals(name)) {
                return style;
            }
        }
        return STANDARD;
    }

    /**
     * 按方块注册名反查外观；{@code rail_curve} 属于默认外观，不认识的名字也退回默认外观。
     */
    public static RailStyle byBlockName(String path) {
        for (RailStyle style : values()) {
            if (style.blockName.equals(path) || (style == STANDARD && CURVE_BLOCK.equals(path))) {
                return style;
            }
        }
        return STANDARD;
    }

    /** 该方块名是不是本模组的轨道（直道或弯道）。 */
    public static boolean isTrack(String path) {
        for (RailStyle style : values()) {
            if (style.blockName.equals(path)) {
                return true;
            }
        }
        return CURVE_BLOCK.equals(path);
    }

    /** 全部直道方块注册名（按枚举顺序）。 */
    public static List<String> straightBlockNames() {
        return Arrays.stream(values()).map(RailStyle::blockName).toList();
    }
}
