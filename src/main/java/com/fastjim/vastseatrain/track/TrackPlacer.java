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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

        BlockState rail = VTBlocks.RAIL.get().defaultBlockState().setValue(RailBlock.FACING, dir);
        BlockState left = VTBlocks.RAIL_SLAB.get().defaultBlockState()
                .setValue(RailSlabBlock.FACING, dir)
                .setValue(RailSlabBlock.HALF, halfFor(dir, true));
        BlockState right = left.setValue(RailSlabBlock.HALF, halfFor(dir, false));

        List<BlockPos> placed = new ArrayList<>(rows.length * TrackLayout.WIDTH_BLOCKS);
        for (BlockPos[] row : rows) {
            if (!level.getBlockState(row[1]).is(VTBlocks.RAIL.get())) {
                level.setBlock(row[1], rail, Block.UPDATE_ALL);
            }
            placed.add(row[1]);
            if (!level.getBlockState(row[0]).is(VTBlocks.RAIL_SLAB.get())) {
                level.setBlock(row[0], left, Block.UPDATE_ALL);
            }
            placed.add(row[0]);
            if (!level.getBlockState(row[2]).is(VTBlocks.RAIL_SLAB.get())) {
                level.setBlock(row[2], right, Block.UPDATE_ALL);
            }
            placed.add(row[2]);
        }
        // 整段登记进存档：后续破坏任意一格都会带动整段移除
        TrackSegments.get(level).addSegment(UUID.randomUUID(), placed);
        return null;
    }

    /**
     * 补位格需要哪种半侧模型。
     * <p>
     * 补位模型是把完整模型沿轨道横轴翻转得到的，而方块状态的 {@code y} 旋转对左格
     * （局部 Z = +1）和右格（局部 Z = -1）作用方向不同：朝向为北/西时应当翻转另一侧。
     */
    private static RailSlabBlock.Half halfFor(Direction forward, boolean leftSide) {
        boolean mirrored = forward == Direction.NORTH || forward == Direction.WEST;
        return leftSide == mirrored ? RailSlabBlock.Half.RIGHT : RailSlabBlock.Half.LEFT;
    }
}
