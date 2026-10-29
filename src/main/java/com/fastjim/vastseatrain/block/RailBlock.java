package com.fastjim.vastseatrain.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 一段轨道的中心格，同时负责整段的朝向。
 * <p>
 * 模型来自美术提供的 {@code test.obj}，原样使用、顶点不做任何改动。模型约定：前进方向 -X、
 * 原点在轨道段中心，轨顶 {@code y = 0.75}。把模型摆进方块坐标系只需要绕 Y 轴旋转，
 * 旋转写在 {@code models/block/rail/test.json} 的 {@code transform} 里（旋转 90°，
 * 使 -X 对齐 {@link #FACING} 对应的世界方向）。
 * <p>
 * 碰撞与物理尺寸按“长 1 格、宽 3 格”计算，与模型实际外形无关（美术会为了观感略微越界）。
 */
public class RailBlock extends HorizontalDirectionalBlock {
    /** 一段轨道在平面上的长度（格）。 */
    public static final int LENGTH_BLOCKS = 1;
    /** 一段轨道在平面上的宽度（格），中心格 + 左右各一格。 */
    public static final int WIDTH_BLOCKS = 3;

    /** 轨面接触区：整格上方 1/16 高，玩家站在轨道上时不悬空。 */
    private static final VoxelShape CONTACT = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);

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

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CONTACT;
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

    /** 该位置是否是一段轨道的中心格。 */
    public static boolean isCenter(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof RailBlock;
    }
}
