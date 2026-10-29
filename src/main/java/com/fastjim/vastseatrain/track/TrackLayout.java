package com.fastjim.vastseatrain.track;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 轨道占位规则，严格按项目约定：
 * <ul>
 *     <li>一次铺设 = 沿前进方向逐格排列的若干个“段”，每段就是 1 个方块、1 格长；</li>
 *     <li>每个方块只渲染一份完整模型，模型自身横跨 3 格宽，<b>不再并排铺 3 条轨道</b>；</li>
 *     <li>碰撞箱按 3 格宽计算（见 {@code RailBlock#getCollisionShape}），模型越界部分允许重叠。</li>
 * </ul>
 */
public final class TrackLayout {
    /** 一段轨道沿前进方向占用的方块数。 */
    public static final int BLOCK_SPAN = 1;
    /** 轨道总宽度（格），用于碰撞箱与摘要显示。 */
    public static final int WIDTH_BLOCKS = 3;

    private TrackLayout() {
    }

    /**
     * 逐段计算轨道占用的方块：沿 {@code dir} 从 {@code origin} 起连续 {@code length} 格。
     *
     * @param origin 轨道起点方块（第一段所在格）
     * @param dir    轨道前进方向
     * @param length 段数
     */
    public static BlockPos[] footprint(BlockPos origin, Direction dir, int length) {
        BlockPos[] positions = new BlockPos[length * BLOCK_SPAN];
        for (int step = 0; step < positions.length; step++) {
            positions[step] = origin.relative(dir, step);
        }
        return positions;
    }
}
