package com.fastjim.vastseatrain.client;

import com.fastjim.vastseatrain.VastseaTrain;
import com.fastjim.vastseatrain.client.model.RailObjLoader;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端模型加载器注册：{@code vastseatrain:rail_obj}。
 * <p>
 * 见 {@link RailObjLoader}：解析美术 OBJ，UV 按整数格重复、平移在朝向旋转之后叠加。
 */
@Mod.EventBusSubscriber(modid = VastseaTrain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModelEvents {
    private ClientModelEvents() {
    }

    @SubscribeEvent
    public static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register("rail_obj", new RailObjLoader());
    }
}
