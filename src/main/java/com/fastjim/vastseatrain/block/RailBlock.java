package com.fastjim.vastseatrain.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 一段轨道的方块：1 个方块承载 1 格长的轨道，模型自身横跨 3 格宽。
 * <p>
 * 模型来自美术提供的 {@code test.obj}，原样使用、顶点不做任何改动。约定：模型长度轴为 X、
 * 前进方向 −X、原点在段中心。朝向只通过 {@code blockstates/rail.json} 的 {@code y} 旋转表达：
 * {@code y=90} 让长度轴（X）对齐南北，{@code y=180}/{@code y=0} 对齐东西。
 * <p>
 * 碰撞箱按“长 1 格、宽 3 格”计算，与模型实际外形无关（模型为了观感会略微越界）。
 */
public class RailBlock extends HorizontalDirectionalBlock {
    /** 一段轨道在平面上的长度（格）。 */
    public static final int LENGTH_BLOCKS = 1;
    /** 一段轨道在平面上的宽度（格），以本方块为中心向两侧各延伸 1 格。 */
    public static final int WIDTH_BLOCKS = 3;
    /**
     * 形状顶面在“方块坐标”下的高度。
     * <p>
     * {@link Block#box} 不会把越界坐标夹回 0..16，但方块形状在网格对齐时会被当作平面处理：
     * 高度的几分之几决定它能否与邻居拼成整面。用整数 1 格，轨道格才会被当作完整的实心方块
     * 参与天空光衰减与面剔除，从而在地面上投出阴影。
     */
    private static final double BED_HEIGHT = 2.0D;
    /** 3 格宽的形状：南北朝向时沿 X 铺开，东西朝向时沿 Z 铺开。 */
    private static final VoxelShape SHAPE_X = Block.box(0.0D, 0.0D, -16.0D, 16.0D, BED_HEIGHT, 32.0D);
    private static final VoxelShape SHAPE_Z = Block.box(-16.0D, 0.0D, 0.0D, 32.0D, BED_HEIGHT, 16.0D);

    public RailBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * 方块形状：用于遮挡天空光、下落阴影与实体碰撞采样。
     * <p>
     * 轨道模型本身很低（道床 + 钢轨），但形状必须返回“整格实心”，而不能是低矮形状：
     * Minecraft 只在形状为整格时把该格视为不透明方块，参与天空光衰减与相邻面剔除。
     * 返回低矮形状时，这一格对光照完全透明，轨道就永远投不出阴影。
     * <p>
     * 返回 3 格宽的超格形状（南北向沿 X 铺开、东西向沿 Z 铺开），与模型观感一致。
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        boolean alongZ = facing.getAxis() == Direction.Axis.Z;
        return alongZ ? SHAPE_Z : SHAPE_X;
    }

    /** 3 格宽的接触面：南北朝向时沿 X 方向铺开，东西朝向时沿 Z 方向铺开。 */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    /**
     * 视觉形状与碰撞箱一致。
     */
    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    /**
     * 是否遮挡天空光。
     * <p>
     * 原版默认实现依赖“形状是否整格”的缓存判断，形状稍有差池就会让整格对光照透明。
     * 这里直接返回 {@code true}：轨道格不向下透光，下方与相邻方块才会出现阴影和明暗过渡。
     * 轨道模型本身不封顶，但游戏阴影来自方块光照而非模型，所以这样不会让画面变黑。
     */
    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // ---- 整段轨道的朝向与位置工具 ----

    /** 前进方向。 */
    public static Direction forward(BlockState center) {
        return center.getValue(FACING);
    }

    /** 面向前进方向的左侧。 */
    public static Direction left(BlockState center) {
        return forward(center).getCounterClockWise();
    }

    /** 面向前进方向的右侧。 */
    public static Direction right(BlockState center) {
        return forward(center).getClockWise();
    }

    /** 该位置是否是一段轨道的成员格。 */
    public static boolean isCenter(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof RailBlock;
    }
}
