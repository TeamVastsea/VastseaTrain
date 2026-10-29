package com.fastjim.vastseatrain.track;

import com.fastjim.vastseatrain.block.RailBlock;
import com.fastjim.vastseatrain.registry.VTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 服务器端铺轨。世界修改只在这里发生；全部校验通过后才开始写入，避免留下半截轨道。
 * <p>
 * 每格只放一个 {@code rail} 方块（模型自身横跨 3 格宽，不再并排铺 3 条轨道），
 * 沿方向逐格前推，相邻段首尾相接。
 */
public final class TrackPlacer {
    private TrackPlacer() {
    }

    /**
     * 从 {@code origin} 开始沿 {@code dir} 方向铺设直线轨道，每段 1 格长。
     *
     * @return null 表示成功，否则返回失败原因（已本地化）
     */
    public static Component place(ServerLevel level, BlockPos origin, Direction dir, BlueprintSettings settings) {
        if (!level.isInWorldBounds(origin)) {
            return Component.translatable("message.vastseatrain.out_of_world", origin.getX(), origin.getZ());
        }

        BlockPos[] positions = TrackLayout.footprint(origin, dir, settings.length());

        for (BlockPos pos : positions) {
            if (!level.isInWorldBounds(pos)) {
                return Component.translatable("message.vastseatrain.out_of_world", pos.getX(), pos.getZ());
            }
            BlockState existing = level.getBlockState(pos);
            if (existing.is(VTBlocks.RAIL.get())) {
                continue;
            }
            if (!existing.canBeReplaced()) {
                return Component.translatable("message.vastseatrain.blocked", pos.getX(), pos.getY(), pos.getZ());
            }
            if (level.getBlockState(pos.below()).isAir()) {
                return Component.translatable("message.vastseatrain.no_ground_at", pos.getX(), pos.getY(), pos.getZ());
            }
        }

        BlockState rail = VTBlocks.RAIL.get().defaultBlockState().setValue(RailBlock.FACING, dir);

        List<BlockPos> placed = new ArrayList<>(positions.length);
        for (BlockPos pos : positions) {
            if (!level.getBlockState(pos).is(VTBlocks.RAIL.get())) {
                level.setBlock(pos, rail, Block.UPDATE_ALL);
            }
            placed.add(pos);
        }
        // 整段登记进存档：后续破坏任意一格都会带动整段移除
        TrackSegments.get(level).addSegment(UUID.randomUUID(), placed);
        return null;
    }
}
