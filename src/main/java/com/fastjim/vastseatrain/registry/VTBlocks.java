package com.fastjim.vastseatrain.registry;

import com.fastjim.vastseatrain.VastseaTrain;
import com.fastjim.vastseatrain.block.RailBlock;
import com.fastjim.vastseatrain.block.RailCurveBlock;
import com.fastjim.vastseatrain.track.RailStyle;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;

public final class VTBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, VastseaTrain.MOD_ID);

    /** 直道方块的注册对象：{@link RailStyle} 按名字取用（枚举先于注册表初始化，不能提前拿实例）。 */
    private static final Map<String, RegistryObject<Block>> RAILS = new HashMap<>();

    /**
     * 轨道主体方块（默认外观）：一个方块承载“一段”轨道，段长 1 格；模型自身横跨 3 格宽，
     * 碰撞箱也按 3 格宽计算（见 {@link RailBlock}）。
     * 模型按“长度轴 X、前进方向 −X”制作，朝向由 blockstate 的 y 旋转表达。
     * <p>
     * 这里<b>不能</b>调用 {@code noOcclusion()}：那会让方块完全不参与光照遮挡，
     * 表现为轨道在地面/相邻方块上没有任何阴影。轨道是低矮形状，
     * 由 {@link RailBlock#getShape} 返回 3 格宽道床形状即可正确遮挡。
     */
    public static final RegistryObject<Block> RAIL = registerRail("rail");

    /** 第二种外观（美术 {@code test2.obj}）的直道方块，形状、阴影、碰撞箱与默认外观一致。 */
    public static final RegistryObject<Block> RAIL_SLAB = registerRail("rail_slab");

    /**
     * 弯道格：每格一个方块实体，保存这份模型的旋转角、格内偏移与外观名（见 {@link RailCurveBlock}）。
     * 形状与阴影规则与直道一致，同样<b>不能</b>调用 {@code noOcclusion()}。
     */
    public static final RegistryObject<Block> RAIL_CURVE = BLOCKS.register(RailStyle.CURVE_BLOCK,
            () -> new RailCurveBlock(trackProperties()));

    private static RegistryObject<Block> registerRail(String name) {
        RegistryObject<Block> object = BLOCKS.register(name, () -> new RailBlock(trackProperties()));
        RAILS.put(name, object);
        return object;
    }

    private static BlockBehaviour.Properties trackProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.0F, 6.0F)
                .sound(SoundType.WOOD);
    }

    /** 按注册名取直道方块；没有这个名字时退回默认方块。只在方块注册之后调用。 */
    public static Block byName(String name) {
        RegistryObject<Block> object = RAILS.get(name);
        if (object != null) {
            Block block = object.get();
            if (block != null) {
                return block;
            }
        }
        return RAIL.get();
    }

    /** 这个方块是不是本模组的轨道（任意外观的直道 + 弯道）。 */
    public static boolean isTrack(Block block) {
        return block instanceof RailBlock || block instanceof RailCurveBlock;
    }

    private VTBlocks() {
    }
}
