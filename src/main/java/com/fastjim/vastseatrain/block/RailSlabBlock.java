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
 * 轨道左右两侧的道床格，负责把轨道占位补足到 3 格宽。
 * <p>
 * 美术模型是“1 格长 × 3 格宽”的完整一段，因此两侧格不能直接渲染同一份模型，
 * 需要沿水平轴翻转后各取一半：{@link Half#LEFT} 取前进方向左侧那一半，
 * {@link Half#RIGHT} 取右侧那一半，两者拼起来正好与中心格对齐。
 * 翻转通过两套模型 JSON 的 {@code transform} 完成，模型文件本身不做改动。
 * 道床顶部在方块内，且不提供碰撞，玩家仍站在下方地面上。
 */
public class RailSlabBlock extends HorizontalDirectionalBlock {
    /** 该道床格相对于轨道前进方向位于哪一侧。 */
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

    public RailSlabBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.WEST)
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
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }
}
