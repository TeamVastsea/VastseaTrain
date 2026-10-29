package com.fastjim.vastseatrain.registry;

import com.fastjim.vastseatrain.VastseaTrain;
import com.fastjim.vastseatrain.block.RailBlock;
import com.fastjim.vastseatrain.block.RailCurveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class VTBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, VastseaTrain.MOD_ID);

    /**
     * 轨道主体方块：一个方块承载“一段”轨道，段长 1 格；模型自身横跨 3 格宽，
     * 碰撞箱也按 3 格宽计算（见 {@link RailBlock}）。
     * 模型按“长度轴 X、前进方向 −X”制作，朝向由 blockstate 的 y 旋转表达。
     * <p>
     * 这里<b>不能</b>调用 {@code noOcclusion()}：那会让方块完全不参与光照遮挡，
     * 表现为轨道在地面/相邻方块上没有任何阴影。轨道是低矮形状，
     * 由 {@link RailBlock#getShape} 返回 3 格宽道床形状即可正确遮挡。
     */
    public static final RegistryObject<Block> RAIL = BLOCKS.register("rail",
            () -> new RailBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.WOOD)));

    /**
     * 弯道格：每格一个方块实体，保存这份模型的旋转角与格内偏移（见 {@link RailCurveBlock}）。
     * 形状与阴影规则与直道一致，同样<b>不能</b>调用 {@code noOcclusion()}。
     */
    public static final RegistryObject<Block> RAIL_CURVE = BLOCKS.register("rail_curve",
            () -> new RailCurveBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.WOOD)));

    private VTBlocks() {
    }
}
