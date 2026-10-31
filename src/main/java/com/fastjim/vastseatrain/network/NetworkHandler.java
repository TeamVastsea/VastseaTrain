package com.fastjim.vastseatrain.network;

import com.fastjim.vastseatrain.VastseaTrain;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 模组网络通道。注册必须发生在模组初始化期间（构造器），不能放到客户端事件里，
 * 否则专用服务器上通道会缺失，登录时的版本握手失败。
 */
public final class NetworkHandler {
    private static final String PROTOCOL = "4";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(VastseaTrain.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private NetworkHandler() {
    }

    public static void register() {
        CHANNEL.messageBuilder(SaveBlueprintMessage.class, 0)
                .encoder(SaveBlueprintMessage::encode)
                .decoder(SaveBlueprintMessage::decode)
                .consumerMainThread(SaveBlueprintMessage::handle)
                .add();
    }
}
