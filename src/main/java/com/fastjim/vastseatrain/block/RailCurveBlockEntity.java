package com.fastjim.vastseatrain.block;

import com.fastjim.vastseatrain.registry.VTBlockEntities;
import com.fastjim.vastseatrain.track.RailCurve;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 弯道上一个方块实体：只保存“这一格里的那份轨道模型怎么摆”。
 * <p>
 * 为什么需要方块实体：90° 弯里每条模型的旋转角是连续的（几十上百条，不可能用 blockstate 的 4 个朝向表达），
 * 而方块模型只在区块烘焙时生成一次。所以把每格的旋转角与格内位置写进方块实体，客户端
 * {@code RailCurveBakedModel} 在区块烘焙时取用（一次烘焙后缓存进区块网格，逐帧不再重算）。
 * <p>
 * 数据全部由服务端铺设时算好（见 {@code CurvePlan}），客户端不解算曲线、不需要额外网络包。
 */
public class RailCurveBlockEntity extends BlockEntity {
    /** 模型绕 Y 轴的旋转角（弧度，世界坐标）。 */
    private float angle;
    /** 模型原点在方块内的位置（0..1）。 */
    private float originX = 0.5F;
    private float originZ = 0.5F;
    /** 模型原点的径向微调（格，负值朝圆心）。 */
    private float radial;
    /** 参考信息：半径与左右，供提示/调试使用，不参与渲染。 */
    private int radius;
    private boolean left;

    public RailCurveBlockEntity(BlockPos pos, BlockState state) {
        super(VTBlockEntities.RAIL_CURVE.get(), pos, state);
    }

    /** 设置这一格模型的摆放。 */
    public void setPlacement(double angle, double originX, double originZ, double radial, int radius, boolean left) {
        this.angle = (float) angle;
        this.originX = (float) originX;
        this.originZ = (float) originZ;
        this.radial = (float) radial;
        this.radius = radius;
        this.left = left;
        setChanged();
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
        }
    }

    /** 渲染用的摆放参数。 */
    public RailCurve.Transform transform() {
        return new RailCurve.Transform(
                (float) Math.cos(angle), (float) Math.sin(angle), originX, originZ, radial);
    }

    public int radius() {
        return radius;
    }

    public boolean left() {
        return left;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat("Angle", angle);
        tag.putFloat("OriginX", originX);
        tag.putFloat("OriginZ", originZ);
        tag.putFloat("Radial", radial);
        tag.putInt("Radius", radius);
        tag.putBoolean("Left", left);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        angle = tag.getFloat("Angle");
        originX = tag.contains("OriginX") ? tag.getFloat("OriginX") : 0.5F;
        originZ = tag.contains("OriginZ") ? tag.getFloat("OriginZ") : 0.5F;
        radial = tag.getFloat("Radial");
        radius = tag.getInt("Radius");
        left = tag.getBoolean("Left");
    }

    /** 方块更新包：让客户端一收到数据就能改动渲染，避免先渲染成直道再跳变。 */
    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
