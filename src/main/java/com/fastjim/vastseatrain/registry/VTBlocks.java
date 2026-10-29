package com.fastjim.vastseatrain.registry;

import com.fastjim.vastseatrain.VastseaTrain;
import com.fastjim.vastseatrain.block.RailBlock;
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
     */
    public static final RegistryObject<Block> RAIL = BLOCKS.register("rail",
            () -> new RailBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)));

    private VTBlocks() {
    }
}
