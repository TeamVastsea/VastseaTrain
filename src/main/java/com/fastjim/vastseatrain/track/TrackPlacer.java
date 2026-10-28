package com.fastjim.vastseatrain.track;

import com.fastjim.vastseatrain.block.RailBlock;
import com.fastjim.vastseatrain.block.RailSlabBlock;
import com.fastjim.vastseatrain.registry.VTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 服务器端铺轨。世界修改只在这里发生；平面校验通过后才开始写入，避免留下半截轨道。
 */
public final class TrackPlacer {
    private TrackPlacer() {
    }

    /**
     * 从 {@code origin} 开始沿 {@code dir} 方向铺设直线轨道。
     *
     * @return null 表示成功，否则返回失败原因（已本地化）
     */
    public static Component place(ServerLevel level, BlockPos origin, Direction dir, BlueprintSettings settings) {
        if (!level.isInWorldBounds(origin)) {
            return Component.translatable("message.vastseatrain.out_of_world", origin.getX(), origin.getZ());
        }

        BlockPos[][] rows = TrackLayout.footprint(origin, dir, settings.length());

        // 整体校验：任何一格被占用或下方悬空，都直接取消
        for (BlockPos[] row : rows) {
            for (BlockPos pos : row) {
                if (!level.isInWorldBounds(pos)) {
                    return Component.translatable("message.vastseatrain.out_of_world", pos.getX(), pos.getZ());
                }
                BlockState existing = level.getBlockState(pos);
                if (existing.is(VTBlocks.RAIL.get()) || existing.is(VTBlocks.RAIL_SLAB.get())) {
                    continue;
                }
                if (!existing.canBeReplaced()) {
                    return Component.translatable("message.vastseatrain.blocked", pos.getX(), pos.getY(), pos.getZ());
                }
                if (level.getBlockState(pos.below()).isAir()) {
                    return Component.translatable("message.vastseatrain.no_ground_at", pos.getX(), pos.getY(), pos.getZ());
                }
            }
        }

        for (int row = 0; row < rows.length; row++) {
            boolean segmentStart = TrackLayout.isSegmentStart(row);
            BlockState rail = VTBlocks.RAIL.get().defaultBlockState()
                    .setValue(RailBlock.FACING, dir)
                    .setValue(RailBlock.MODEL, segmentStart);
            BlockState slab = VTBlocks.RAIL_SLAB.get().defaultBlockState()
                    .setValue(RailSlabBlock.FACING, dir)
                    .setValue(RailSlabBlock.SEGMENT_START, segmentStart);

            BlockPos[] step = rows[row];
            if (!level.getBlockState(step[1]).is(VTBlocks.RAIL.get())) {
                level.setBlock(step[1], rail, Block.UPDATE_ALL);
            }
            if (!level.getBlockState(step[0]).is(VTBlocks.RAIL_SLAB.get())) {
                level.setBlock(step[0], slab, Block.UPDATE_ALL);
            }
            if (!level.getBlockState(step[2]).is(VTBlocks.RAIL_SLAB.get())) {
                level.setBlock(step[2], slab, Block.UPDATE_ALL);
            }
        }
        return null;
    }
}
