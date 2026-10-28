package com.fastjim.vastseatrain.item;

import com.fastjim.vastseatrain.track.BlueprintSettings;
import com.fastjim.vastseatrain.track.TrackPlacer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * 轨道蓝图。沿用 Immersive Railroading 的操作习惯：
 * <ul>
 *     <li>手持右键空气：打开三栏参数界面；</li>
 *     <li>右键地面：按当前参数沿玩家朝向铺设直线轨道。</li>
 * </ul>
 * 交互链路只有一条：世界修改只在本物品的 {@link #useOn} 服务端分支执行，
 * 客户端不额外发包，避免同一次右键被铺设两遍。首版只实现直线。
 */
public class TrackBlueprintItem extends Item {
    private static Consumer<ItemStack> screenOpener = stack -> {
    };

    public TrackBlueprintItem(Properties properties) {
        super(properties);
    }

    /** 由客户端初始化时注入真实界面，避免服务端加载客户端类。 */
    public static void setScreenOpener(Consumer<ItemStack> opener) {
        screenOpener = opener;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            screenOpener.accept(stack);
        }
        return new InteractionResultHolder<>(InteractionResult.sidedSuccess(level.isClientSide), stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(context.getHand());
        if (!(stack.getItem() instanceof TrackBlueprintItem)) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.vastseatrain.bed_sample",
                        level.getBlockState(context.getClickedPos()).getBlock().getName()), true);
            }
            return InteractionResult.SUCCESS;
        }

        BlockPos origin = context.getClickedPos().above();
        Direction dir = context.getHorizontalDirection().getOpposite();
        BlueprintSettings settings = BlueprintSettings.from(stack);

        if (level.isClientSide) {
            // 客户端只做动画反馈；世界修改完全由服务端分支处理
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            Component failure = TrackPlacer.place(serverPlayer.serverLevel(), origin, dir, settings);
            if (failure != null) {
                serverPlayer.displayClientMessage(failure.copy().withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
            serverPlayer.displayClientMessage(Component.translatable("message.vastseatrain.placed",
                    settings.length()).withStyle(ChatFormatting.GREEN), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        BlueprintSettings settings = BlueprintSettings.from(stack);
        tooltip.add(Component.translatable("tooltip.vastseatrain.length", settings.length()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.vastseatrain.gauge", settings.gauge().getDisplayName()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.vastseatrain.bed", settings.bed().getDisplayName()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.vastseatrain.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
