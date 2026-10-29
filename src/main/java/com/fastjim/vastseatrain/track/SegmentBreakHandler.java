package com.fastjim.vastseatrain.track;

import com.fastjim.vastseatrain.VastseaTrain;
import com.fastjim.vastseatrain.registry.VTBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 让“蓝图铺下的一段轨道”成为整体：破坏其中任意一格，整段一起消失。
 * <p>
 * 玩家破坏成员格时，取消这次单格破坏，改为移除整段，并按原版规则掉落被破坏的那一格，
 * 避免整段凭空消失。无归属的单格轨道（例如直接放置的轨道方块）不受影响。
 */
@Mod.EventBusSubscriber(modid = VastseaTrain.MOD_ID)
public final class SegmentBreakHandler {
    private SegmentBreakHandler() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = event.getState();
        if (!isTrack(state)) {
            return;
        }
        TrackSegments segments = TrackSegments.get(level);
        UUID segmentId = segments.segmentAt(event.getPos());
        if (segmentId == null) {
            return;
        }

        event.setCanceled(true);
        level.removeBlock(event.getPos(), false);
        segments.removeSegment(level, segmentId);
    }

    /** 直道与弯道格都算“轨道成员”（含切换外观后新加入的直道方块）。 */
    private static boolean isTrack(BlockState state) {
        return VTBlocks.isTrack(state.getBlock());
    }
}
