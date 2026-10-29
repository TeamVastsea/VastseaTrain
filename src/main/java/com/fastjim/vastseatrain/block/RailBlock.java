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
     * 方块形状：用于选择框与实体的形状查询。
     * <p>
     * {@code getShape} <b>不能</b>返回超格形状：Minecraft 会把它当作整格遮挡形状参与相邻方块的面剔除，
     * 返回“3 格宽”的形状后，周围一圈格子的顶面会被当成被遮住而删掉，看起来就是地面破面/穿模。
     * 轨道格在网格上就按整格处理，遮挡与剔除全部交给原版的整格逻辑；只有碰撞箱保留 3 格宽。
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    /** 3 格宽的接触面：南北朝向时沿 X 方向铺开，东西朝向时沿 Z 方向铺开。 */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        boolean alongZ = facing.getAxis() == Direction.Axis.Z;
        return alongZ ? SHAPE_Z : SHAPE_X;
    }

    /**
     * 视觉形状：只用于阴影与部分渲染判定，返回整格，避免超格形状把相邻方块的剔除搞乱。
     */
    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    /**
     * 遮挡形状：返回空形状。
     * <p>
     * 轨道模型不是整格实心方块，若让它遮挡相邻方块的面，紧贴轨道的方块顶面会被剃掉，
     * 在轨道与地面交界处出现缺口（游戏里看起来像“陷进地面”）。返回空形状后原版照常画地面。
     */
    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    /** 轨道格透明，不开启 AO：AO 会把整格当成不透明方块，在轨道周围压出一圈黑边。 */
    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return false;
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
