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
import net.minecraft.world.phys.shapes.Shapes;
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
    /** 碰撞箱高度：方块顶面上方 1/16。 */
    private static final double HEIGHT = 1.0D;
    /** 半宽：3 格宽围绕中心格，边界为 ±1.5。 */
    private static final double HALF = WIDTH_BLOCKS / 2.0D;
    /** 碰撞箱沿轨道方向占据本格满长。 */
    private static final VoxelShape CONTACT_X = Block.box(0.0D, 0.0D, -16.0D, 16.0D, HEIGHT, 32.0D);
    private static final VoxelShape CONTACT_Z = Block.box(-16.0D, 0.0D, 0.0D, 32.0D, HEIGHT, 16.0D);

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
     * 3 格宽的接触面：南北朝向时沿 X 方向铺开，东西朝向时沿 Z 方向铺开。
     * 形状允许越出本方块边界，这是“一个方块承载 3 格宽轨道”的必要做法。
     */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        boolean alongZ = facing.getAxis() == Direction.Axis.Z;
        return alongZ ? CONTACT_Z : CONTACT_X;
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
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
