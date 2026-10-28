package com.fastjim.vastseatrain.network;

import com.fastjim.vastseatrain.item.TrackBlueprintItem;
import com.fastjim.vastseatrain.track.BlueprintSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 蓝图参数回写（客户端 -> 服务器）。客户端界面点“完成”时发出，
 * 服务端写入持有蓝图的手，保证之后的铺设使用同一套参数。
 */
public record SaveBlueprintMessage(BlueprintSettings settings) {
    public void encode(FriendlyByteBuf buf) {
        settings.encode(buf);
    }

    public static SaveBlueprintMessage decode(FriendlyByteBuf buf) {
        return new SaveBlueprintMessage(BlueprintSettings.decode(buf));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            if (ctx.getSender() == null) {
                return;
            }
            Player player = ctx.getSender();
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof TrackBlueprintItem) {
                    settings.write(stack);
                    return;
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}
