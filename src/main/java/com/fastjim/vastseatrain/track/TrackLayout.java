package com.fastjim.vastseatrain.track;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 轨道占位规则。
 * <p>
 * 美术模型沿 Z 轴宽 1.6 格、长约 4.28 格，一段以方块中心为原点时覆盖前后各约 1 格，
 * 因此“一段”的中线跨越 2 个方块中心间距；每格再左右各留 1 格支撑块，合计 3 格宽。
 * 模型只在每段的第一个方块渲染，第二个方块仅渲染道床支撑，避免整张长模型重复绘制。
 */
public final class TrackLayout {
    /** 一段轨道沿延伸方向跨越的方块数。 */
    public static final int BLOCK_SPAN = 2;
    /** 轨道总宽度（含两侧支撑块）。 */
    public static final int WIDTH_BLOCKS = 3;

    private TrackLayout() {
    }

    /**
     * 逐格计算轨道占用的方块。返回数组的行数等于 {@code length * BLOCK_SPAN}，
     * 每行 3 列：左侧支撑块、轨道主体块、右侧支撑块。
     *
     * @param origin 轨道起点方块（玩家点击的方块）
     * @param dir    轨道延伸方向
     * @param length 段数
     */
    public static BlockPos[][] footprint(BlockPos origin, Direction dir, int length) {
        Direction side = dir.getClockWise();
        int steps = length * BLOCK_SPAN;
        BlockPos[][] rows = new BlockPos[steps][WIDTH_BLOCKS];
        for (int step = 0; step < steps; step++) {
            BlockPos center = origin.relative(dir, step);
            rows[step][0] = center.relative(side.getOpposite());
            rows[step][1] = center;
            rows[step][2] = center.relative(side);
        }
        return rows;
    }

    /** 该行在整条轨道中的序号是否属于某一段的起始行（模型渲染位置）。 */
    public static boolean isSegmentStart(int row) {
        return row % BLOCK_SPAN == 0;
    }
}
