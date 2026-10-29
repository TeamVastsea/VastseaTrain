package com.fastjim.vastseatrain.client.texture;

import com.fastjim.vastseatrain.VastseaTrain;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterTextureAtlasSpriteLoadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 把 {@link RailTextureLoader} 注册进方块图集，并挂上美术目录扫描日志。
 * <p>
 * 贴图来源：{@code src/main/resources/assets/vastseatrain/models/block/rail/}（美术只改这里）。
 * 命中方式：对应图集贴图的侧车 {@code textures/block/rail/<名字>.png.mcmeta} 里写
 * {@code {"forge":{"loader":"vastseatrain:rail_art"}}}（Forge 只按 .mcmeta 选加载器，不按名字匹配）。
 */
@Mod.EventBusSubscriber(modid = VastseaTrain.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RailTextureEvents {
    private RailTextureEvents() {
    }

    @SubscribeEvent
    public static void registerSpriteLoaders(RegisterTextureAtlasSpriteLoadersEvent event) {
        // Forge 只提供 register(String, loader)，内部用 ResourceLocation 解析
        event.register(RailTextureLoader.ID.toString(), RailTextureLoader.INSTANCE);
        VastseaTrain.LOGGER.info("已注册美术贴图加载器 {}", RailTextureLoader.ID);
    }

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(RailArtReloadListener.INSTANCE);
    }
}
