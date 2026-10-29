package com.fastjim.vastseatrain.registry;

import com.fastjim.vastseatrain.VastseaTrain;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class VTTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VastseaTrain.MOD_ID);

    /** 暂定模组中文名“瀚海轨交”，物品栏与其同名。 */
    public static final RegistryObject<CreativeModeTab> MAIN = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + VastseaTrain.MOD_ID))
                    .icon(() -> new ItemStack(VTItems.TRACK_BLUEPRINT.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(VTItems.TRACK_BLUEPRINT.get());
                        output.accept(VTItems.RAIL.get());
                        output.accept(VTItems.RAIL_SLAB.get());
                                    })
                    .build());

    private VTTabs() {
    }
}
