package com.fastjim.vastseatrain;

import com.fastjim.vastseatrain.network.NetworkHandler;
import com.fastjim.vastseatrain.registry.VTBlocks;
import com.fastjim.vastseatrain.registry.VTItems;
import com.fastjim.vastseatrain.registry.VTTabs;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(VastseaTrain.MOD_ID)
public class VastseaTrain {
    public static final String MOD_ID = "vastseatrain";

    public VastseaTrain(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        VTTabs.CREATIVE_TABS.register(modBus);
        VTBlocks.BLOCKS.register(modBus);
        VTItems.ITEMS.register(modBus);
        NetworkHandler.register();
    }
}
