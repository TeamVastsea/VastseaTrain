package com.fastjim.vastseatrain.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 轨道左右两侧的道床补位格。
 * <p>
 * 一段轨道的模型本身就横跨 3 格，但方块只能渲染方块空间里属于自己那一格的部分。
 * 两侧格因此各用一份“只保留道床、再把道床平移一格”的模型：
 * {@link Half#LEFT} 平移到前进方向左侧，{@link Half#RIGHT} 平移到右侧，
 * 拼起来正好与中心格的完整模型对齐。平移写在
 * {@code models/block/rail_slab_left.json} / {@code rail_slab_right.json} 里。
 * <p>
 * 道床同样不提供碰撞，玩家站在地面上。
 */
public class RailSlabBlock extends Block {
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

    public RailSlabBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HALF, Half.LEFT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }
}
