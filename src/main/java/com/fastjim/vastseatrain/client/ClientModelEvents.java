package com.fastjim.vastseatrain.client;

import com.fastjim.vastseatrain.VastseaTrain;
import com.fastjim.vastseatrain.client.model.TiledObjLoader;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端模型加载器注册：{@code vastseatrain:tiled_obj}。
 * <p>
 * 见 {@link TiledObjLoader}：与 {@code forge:obj} 等价，额外把越界 UV 铺成重复贴图。
 */
@Mod.EventBusSubscriber(modid = VastseaTrain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModelEvents {
    private ClientModelEvents() {
    }

    @SubscribeEvent
    public static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register("tiled_obj", new TiledObjLoader());
    }
}
