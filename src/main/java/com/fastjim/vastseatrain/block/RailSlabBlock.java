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
 * 一段轨道的左右补位格。
 * <p>
 * 轨道模型横跨 3 格，但每个方块只能渲染属于自己那一格的部分。中心格渲染完整模型，
 * 左右两格各自渲染同一份完整模型、并在 X 轴上翻转，把落在中心格里的那部分镜像到
 * 自己这一格里，拼起来正好接上。镜像只写在模型的 JSON 里，见
 * {@code models/block/rail/test.json}。由于模型每格都完整渲染，相邻格之间会有重叠，
 * 这是美术越界观感的正常结果。
 * <p>
 * 碰撞与中心格一致，按“长 1 格、宽 3 格”计算。
 */
public class RailSlabBlock extends HorizontalDirectionalBlock {
    /** 该补位格位于轨道的哪一侧（相对轨道前进方向）。 */
    public enum Half implements StringRepresentable {
        LEFT("left"),
        RIGHT("right");

        private final String name;

        Half(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Half> HALF = EnumProperty.create("half", Half.class);

    /** 与中心格相同的接触区。 */
    private static final VoxelShape CONTACT = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);

    public RailSlabBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, Half.LEFT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
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
}
