package com.fastjim.vastseatrain.track;

import com.fastjim.vastseatrain.block.RailBlock;
import com.fastjim.vastseatrain.block.RailCurveBlock;
import com.fastjim.vastseatrain.block.RailCurveBlockEntity;
import com.fastjim.vastseatrain.registry.VTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 服务器端铺轨。世界修改只在这里发生；全部校验通过后才开始写入，避免留下半截轨道。
 * <p>
 * 每格只放一个轨道方块（模型自身横跨 3 格宽，不再并排铺 3 条轨道）：
 * <ul>
 *     <li>直道：所选外观对应的直道方块，沿方向逐格前推；</li>
 *     <li>弯道：{@code rail_curve} 方块，位置由 {@link CurvePlan} 沿弧线算出，
 *     每格的旋转角、格内偏移与外观写进方块实体，客户端按此摆放整份模型。</li>
 * </ul>
 * 直道与弯道在入口处共用同一个点（入口格中心），所以连续铺设时首尾相接。
 * <p>
 * 外观只对<b>本次铺设</b>生效：已经铺好的格子不会被改写。
 */
public final class TrackPlacer {
    private TrackPlacer() {
    }

    /**
     * 从 {@code origin} 开始沿 {@code dir} 方向铺设。
     * {@code origin} 是入口格：直道的第 1 段 / 弯道弧长 0 所在的格。
     *
     * @return null 表示成功，否则返回失败原因（已本地化）
     */
    public static Component place(ServerLevel level, BlockPos origin, Direction dir, BlueprintSettings settings) {
        if (settings.isCurve()) {
            return placeCurve(level, origin, dir, settings);
        }
        return placeStraight(level, origin, dir, settings);
    }

    /** 直线轨道。 */
    private static Component placeStraight(ServerLevel level, BlockPos origin, Direction dir,
                                           BlueprintSettings settings) {
        if (!level.isInWorldBounds(origin)) {
            return Component.translatable("message.vastseatrain.out_of_world", origin.getX(), origin.getZ());
        }

        BlockPos[] positions = TrackLayout.footprint(origin, dir, settings.length());

        for (BlockPos pos : positions) {
            Component failure = checkPlaceable(level, pos);
            if (failure != null) {
                return failure;
            }
        }

        Block railBlock = VTBlocks.byName(settings.style().blockName());
        BlockState rail = railBlock.defaultBlockState().setValue(RailBlock.FACING, dir);

        List<BlockPos> placed = new ArrayList<>(positions.length);
        for (BlockPos pos : positions) {
            // 已有轨道格保持原样：外观切换只影响新铺的格子
            if (!VTBlocks.isTrack(level.getBlockState(pos).getBlock())) {
                level.setBlock(pos, rail, Block.UPDATE_ALL);
            }
            placed.add(pos);
        }
        // 整段登记进存档：后续破坏任意一格都会带动整段移除
        TrackSegments.get(level).addSegment(UUID.randomUUID(), placed);
        return null;
    }

    /**
     * 90° 弯道：沿弧线逐份摆放整份模型，每格一个 {@code rail_curve} 方块。
     * <p>
     * 一格可能叠多份模型（急弯处补缝），所以先按格子汇总，再一次性写进方块实体。
     */
    private static Component placeCurve(ServerLevel level, BlockPos origin, Direction dir,
                                        BlueprintSettings settings) {
        CurvePlan plan = CurvePlan.of(origin, dir, settings.curveLeft(), settings.curveRadius());
        List<CurvePlan.Piece> pieces = plan.pieces();

        for (BlockPos pos : plan.positions()) {
            Component failure = checkPlaceable(level, pos);
            if (failure != null) {
                return failure;
            }
        }

        Map<BlockPos, List<RailCurveBlockEntity.Entry>> byPos = new LinkedHashMap<>();
        for (CurvePlan.Piece piece : pieces) {
            byPos.computeIfAbsent(piece.pos(), key -> new ArrayList<>())
                    .add(new RailCurveBlockEntity.Entry(
                            (float) piece.angle(), (float) piece.offsetX(), (float) piece.offsetZ()));
        }

        List<BlockPos> placed = new ArrayList<>(byPos.size());
        for (Map.Entry<BlockPos, List<RailCurveBlockEntity.Entry>> entry : byPos.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = VTBlocks.RAIL_CURVE.get().defaultBlockState()
                    .setValue(RailCurveBlock.FACING, plan.facingAt(pos));
            level.setBlock(pos, state, Block.UPDATE_ALL);
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof RailCurveBlockEntity curve) {
                curve.setPlacement(entry.getValue(), plan.radius(), plan.left(), settings.style());
            }
            placed.add(pos);
        }
        TrackSegments.get(level).addSegment(UUID.randomUUID(), placed);
        return null;
    }

    /**
     * 单格能否铺轨：世界里已有轨道格的直接跳过，其他非可替换方块与下方悬空都算失败。
     *
     * @return null 表示可以铺
     */
    private static Component checkPlaceable(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos)) {
            return Component.translatable("message.vastseatrain.out_of_world", pos.getX(), pos.getZ());
        }
        BlockState existing = level.getBlockState(pos);
        if (VTBlocks.isTrack(existing.getBlock())) {
            return null;
        }
        if (!existing.canBeReplaced()) {
            return Component.translatable("message.vastseatrain.blocked", pos.getX(), pos.getY(), pos.getZ());
        }
        if (level.getBlockState(pos.below()).isAir()) {
            return Component.translatable("message.vastseatrain.no_ground_at", pos.getX(), pos.getY(), pos.getZ());
        }
        return null;
    }
}
