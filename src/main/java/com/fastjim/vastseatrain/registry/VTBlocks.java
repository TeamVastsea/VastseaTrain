package com.fastjim.vastseatrain.registry;

import com.fastjim.vastseatrain.VastseaTrain;
import com.fastjim.vastseatrain.block.RailBlock;
import com.fastjim.vastseatrain.block.RailSlabBlock;
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
     * 轨道主体方块：一个方块承载“一段”轨道，段长按 IR 约定为方块中心间距的 2 倍。
     * 默认无碰撞，玩家可以踩在轨道模型上；模型自带的道床已延伸到地面以下。
     */
    public static final RegistryObject<Block> RAIL = BLOCKS.register("rail",
            () -> new RailBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)));

    /** 轨道两侧宽度占位方块：让 3 格宽的轨道在碰撞和实体放置上有实际支撑体。 */
    public static final RegistryObject<Block> RAIL_SLAB = BLOCKS.register("rail_slab",
            () -> new RailSlabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.STONE)
                    .noOcclusion()));

    private VTBlocks() {
    }
}
