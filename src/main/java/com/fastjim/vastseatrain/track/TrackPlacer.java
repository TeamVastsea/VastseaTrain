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
 * 服务器端铺轨。世界修改只在这里发生；全部平面校验通过后才开始写入，避免留下半截轨道。
 */
public final class TrackPlacer {
    private TrackPlacer() {
    }

    /**
     * 从 {@code origin} 开始沿 {@code dir} 方向铺设直线轨道，每段 1 格长、3 格宽。
     *
     * @return null 表示成功，否则返回失败原因（已本地化）
     */
    public static Component place(ServerLevel level, BlockPos origin, Direction dir, BlueprintSettings settings) {
        if (!level.isInWorldBounds(origin)) {
            return Component.translatable("message.vastseatrain.out_of_world", origin.getX(), origin.getZ());
        }

        BlockPos[][] rows = TrackLayout.footprint(origin, dir, settings.length());

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

        BlockState rail = VTBlocks.RAIL.get().defaultBlockState()
                .setValue(RailBlock.FACING, dir)
                .setValue(RailBlock.CURVED, false);
        BlockState left = VTBlocks.RAIL_SLAB.get().defaultBlockState()
                .setValue(RailSlabBlock.FACING, dir)
                .setValue(RailSlabBlock.HALF, RailSlabBlock.Half.LEFT);
        BlockState right = left.setValue(RailSlabBlock.HALF, RailSlabBlock.Half.RIGHT);

        for (BlockPos[] row : rows) {
            if (!level.getBlockState(row[1]).is(VTBlocks.RAIL.get())) {
                level.setBlock(row[1], rail, Block.UPDATE_ALL);
            }
            if (!level.getBlockState(row[0]).is(VTBlocks.RAIL_SLAB.get())) {
                level.setBlock(row[0], left, Block.UPDATE_ALL);
            }
            if (!level.getBlockState(row[2]).is(VTBlocks.RAIL_SLAB.get())) {
                level.setBlock(row[2], right, Block.UPDATE_ALL);
            }
        }
        return null;
    }
}
