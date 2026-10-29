package com.fastjim.vastseatrain.track;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 轨道占位规则，严格按项目约定：
 * <ul>
 *     <li>一段轨道 = 沿前进方向 1 格、横向 3 格；</li>
 *     <li>横向 3 格由“中心格 + 左右各 1 格”组成，中心格承载完整模型，两侧格承载道床外观；</li>
 *     <li>模型的轨距为 1 格、段长 1 格，因此相邻段直接首尾相接，不需要搭接。</li>
 * </ul>
 */
public final class TrackLayout {
    /** 一段轨道沿前进方向占用的方块数。 */
    public static final int BLOCK_SPAN = 1;
    /** 轨道总宽度（含两侧道床格）。 */
    public static final int WIDTH_BLOCKS = 3;

    private TrackLayout() {
    }

    /**
     * 逐格计算轨道占用的方块。返回数组行数等于段数，每行 3 列：
     * 左侧道床格、中心轨道格、右侧道床格。
     *
     * @param origin 轨道起点方块
     * @param dir    轨道前进方向
     * @param length 段数
     */
    public static BlockPos[][] footprint(BlockPos origin, Direction dir, int length) {
        Direction side = dir.getClockWise();
        BlockPos[][] rows = new BlockPos[length * BLOCK_SPAN][WIDTH_BLOCKS];
        for (int step = 0; step < rows.length; step++) {
            BlockPos center = origin.relative(dir, step);
            rows[step][0] = center.relative(side.getOpposite());
            rows[step][1] = center;
            rows[step][2] = center.relative(side);
        }
        return rows;
    }
}
