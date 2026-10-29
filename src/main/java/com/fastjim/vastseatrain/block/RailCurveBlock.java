package com.fastjim.vastseatrain.block;

import com.fastjim.vastseatrain.registry.VTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 弯道上的一格轨道。
 * <p>
 * 形状、碰撞箱、朝向、阴影规则与直道 {@link RailBlock} 完全一致，区别只有两点：
 * <ul>
 *     <li>每格带一个 {@link RailCurveBlockEntity}，保存这条模型的旋转角与格内偏移；</li>
 *     <li>渲染走 {@code rail_curve_core} 模型（{@code vastseatrain:rail_curve_obj} 加载器），
 *     由客户端按方块实体的数据把整份模型转到切线方向。</li>
 * </ul>
 * 方块状态的 {@code facing} 只表示“模型大致朝向”，用于选择 3 格宽的形状，不参与几何。
 */
public class RailCurveBlock extends RailBlock implements EntityBlock {
    public RailCurveBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RailCurveBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        return null;
    }
}
