package com.fastjim.vastseatrain.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 一段轨道（中心格）。
 * <p>
 * 模型来自美术提供的 {@code test.obj}，原样使用、顶点不做任何改动。模型的约定是：
 * 前进方向 -X、横向 3 格、沿前进方向 1 格，原点在方块上表面中心，轨顶 {@code y = 0.75}，
 * 道床向下延伸到方块下方被地面遮住。把模型坐标系摆进方块坐标系的旋转只写在
 * {@code models/block/rail/test.json} 里，与方块状态无关，因此方块不再带朝向属性——
 * 换一个朝向只需改 JSON 里的一条 rotation。
 * <p>
 * 轨道不提供碰撞，玩家仍站在地面上。
 */
public class RailBlock extends Block {
    public RailBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }
}
