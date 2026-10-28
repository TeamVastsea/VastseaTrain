package com.fastjim.vastseatrain.client;

import com.fastjim.vastseatrain.VastseaTrain;
import com.fastjim.vastseatrain.gui.TrackBlueprintScreen;
import com.fastjim.vastseatrain.item.TrackBlueprintItem;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = VastseaTrain.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        TrackBlueprintItem.setScreenOpener(stack -> Minecraft.getInstance().setScreen(new TrackBlueprintScreen(stack)));
    }
}
