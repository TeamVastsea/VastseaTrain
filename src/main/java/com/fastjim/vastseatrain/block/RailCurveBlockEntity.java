package com.fastjim.vastseatrain.block;

import com.fastjim.vastseatrain.registry.VTBlockEntities;
import com.fastjim.vastseatrain.track.RailCurve;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 弯道上一个方块实体：保存“这一格里叠着的那几份轨道模型各自怎么摆”。
 * <p>
 * 为什么需要方块实体：90° 弯里每条模型的旋转角是连续的（几十上百份，不可能用 blockstate 的 4 个朝向表达），
 * 而方块模型只在区块烘焙时生成一次。所以把每份模型的旋转角与格内原点写进方块实体，客户端
 * {@code RailCurveBakedModel} 在区块烘焙时取用（一次烘焙后缓存进区块网格，逐帧不再重算）。
 * <p>
 * 一格可能叠多份：急弯处补缝的模型会和相邻的模型落在同一格里。
 * 数据全部由服务端铺设时算好（见 {@code CurvePlan}），客户端不解算曲线、不需要额外网络包。
 */
public class RailCurveBlockEntity extends BlockEntity {
    /** 一格内的每一份模型。 */
    public static final class Entry {
        /** 绕 Y 轴的旋转角（弧度，世界坐标）。 */
        public float angle;
        /** 模型原点在方块局部坐标里的位置（0..1）。 */
        public float originX = 0.5F;
        public float originZ = 0.5F;

        public Entry() {
        }

        public Entry(float angle, float originX, float originZ) {
            this.angle = angle;
            this.originX = originX;
            this.originZ = originZ;
        }

        public RailCurve.Transform transform() {
            return new RailCurve.Transform(
                    (float) Math.cos(angle), (float) Math.sin(angle), originX, originZ);
        }
    }

    private final List<Entry> entries = new ArrayList<>(1);
    /** 参考信息：半径与左右，供提示/调试使用，不参与渲染。 */
    private int radius;
    private boolean left;

    public RailCurveBlockEntity(BlockPos pos, BlockState state) {
        super(VTBlockEntities.RAIL_CURVE.get(), pos, state);
    }

    /** 设置这一格里的全部模型。 */
    public void setPlacement(List<Entry> placement, int radius, boolean left) {
        entries.clear();
        entries.addAll(placement);
        this.radius = radius;
        this.left = left;
        setChanged();
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
        }
    }

    /** 这一格里的所有模型（可能为空，表示还没同步到）。 */
    public List<Entry> entries() {
        return entries;
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
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag one = new CompoundTag();
            one.putFloat("Angle", entry.angle);
            one.putFloat("OriginX", entry.originX);
            one.putFloat("OriginZ", entry.originZ);
            list.add(one);
        }
        tag.put("Strips", list);
        tag.putInt("Radius", radius);
        tag.putBoolean("Left", left);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        entries.clear();
        ListTag list = tag.getList("Strips", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            entries.add(new Entry(one.getFloat("Angle"), one.getFloat("OriginX"), one.getFloat("OriginZ")));
        }
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
