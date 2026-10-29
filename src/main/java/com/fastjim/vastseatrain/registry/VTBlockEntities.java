package com.fastjim.vastseatrain.registry;

import com.fastjim.vastseatrain.VastseaTrain;
import com.fastjim.vastseatrain.block.RailCurveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 方块实体注册。目前只有弯道格需要保存每格的旋转角与格内偏移。 */
public final class VTBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, VastseaTrain.MOD_ID);

    public static final RegistryObject<BlockEntityType<RailCurveBlockEntity>> RAIL_CURVE =
            BLOCK_ENTITIES.register("rail_curve",
                    () -> BlockEntityType.Builder
                            .of(RailCurveBlockEntity::new, VTBlocks.RAIL_CURVE.get())
                            .build(null));

    private VTBlockEntities() {
    }
}
