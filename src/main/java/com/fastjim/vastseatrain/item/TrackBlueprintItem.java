package com.fastjim.vastseatrain.item;

import com.fastjim.vastseatrain.track.BlueprintSettings;
import com.fastjim.vastseatrain.track.DiagonalSolver;
import com.fastjim.vastseatrain.track.TrackPlacer;
import com.fastjim.vastseatrain.registry.VTBlocks;
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
 * 客户端不额外发包，避免同一次右键被铺设两遍。
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

        BlueprintSettings settings = BlueprintSettings.from(stack);
        BlockPos clickedPos = context.getClickedPos();
        BlockPos origin = VTBlocks.isTrack(level.getBlockState(clickedPos).getBlock())
                ? clickedPos : clickedPos.above();
        Direction dir;
        if (!settings.isCurve()) {
            net.minecraft.world.phys.Vec3 look = player.getLookAngle();
            dir = DiagonalSolver.facingFromLook(look.x, look.z, settings.lineAngle(),
                    context.getHorizontalDirection());
        } else {
            // 弯道继续使用原有的四向朝向判定。
            dir = context.getHorizontalDirection();
        }

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
            Component success = settings.isCurve()
                    ? Component.translatable("message.vastseatrain.placed_curve",
                            settings.curveAngle(),
                            settings.curveRadius(),
                            Component.translatable(settings.curveLeft()
                                    ? "message.vastseatrain.curve_left" : "message.vastseatrain.curve_right"),
                            settings.estimatedCurvePieces())
                    : settings.lineAngle() != 0
                    ? Component.translatable("message.vastseatrain.placed_line",
                            settings.lineAngle(),
                            String.format(java.util.Locale.ROOT, "%.2f", settings.lineSolution().actualDegrees()),
                            settings.lineMultiplier(),
                            settings.lineGridSpan(),
                            String.format(java.util.Locale.ROOT, "%.2f", settings.lineLength()),
                            settings.estimatedLinePieces())
                    : Component.translatable("message.vastseatrain.placed", settings.lineMultiplier(),
                            settings.style().getDisplayName());
            serverPlayer.displayClientMessage(success.copy().withStyle(ChatFormatting.GREEN), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        BlueprintSettings settings = BlueprintSettings.from(stack);
        if (settings.isCurve()) {
            tooltip.add(Component.translatable("tooltip.vastseatrain.curve_angle", settings.curveAngle())
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.vastseatrain.curve_radius", settings.curveRadius())
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(settings.curveLeft()
                            ? "message.vastseatrain.curve_left" : "message.vastseatrain.curve_right")
                    .withStyle(ChatFormatting.GRAY));
        } else if (settings.lineAngle() != 0) {
            tooltip.add(Component.translatable("tooltip.vastseatrain.line",
                    settings.lineAngle(),
                    String.format(java.util.Locale.ROOT, "%.2f", settings.lineSolution().actualDegrees()),
                    settings.lineMultiplier(),
                    settings.lineGridSpan(),
                    String.format(java.util.Locale.ROOT, "%.2f", settings.lineLength()))
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.vastseatrain.length", settings.lineMultiplier())
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.vastseatrain.gauge", settings.gauge().getDisplayName()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.vastseatrain.bed", settings.bed().getDisplayName()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.vastseatrain.style", settings.style().getDisplayName()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.vastseatrain.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
