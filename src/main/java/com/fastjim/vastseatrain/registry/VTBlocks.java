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
     * 轨道主体方块：一个方块承载“一段”轨道，段长 1 格（见 {@code TrackLayout.BLOCK_SPAN}）。
     * 模型按“前进方向 = -X”制作，模型配置文件负责旋转与高度适配，方块本身不再参与朝向。
     * 默认无碰撞，玩家站在地面；模型自带道床延伸到方块下方被地面遮住。
     */
    public static final RegistryObject<Block> RAIL = BLOCKS.register("rail",
            () -> new RailBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)));

    /** 轨道两侧宽度占位方块：把 3 格宽的轨道补足成实际方块占位。 */
    public static final RegistryObject<Block> RAIL_SLAB = BLOCKS.register("rail_slab",
            () -> new RailSlabBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.STONE)
                    .noOcclusion()));

    private VTBlocks() {
    }
}
