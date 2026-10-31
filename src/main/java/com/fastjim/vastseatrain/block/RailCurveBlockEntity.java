package com.fastjim.vastseatrain.block;

import com.fastjim.vastseatrain.registry.VTBlockEntities;
import com.fastjim.vastseatrain.track.RailCurve;
import com.fastjim.vastseatrain.track.RailStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 弯道上一个方块实体：保存“这一格里叠着的那几份轨道模型各自怎么摆”。
 * <p>
 * 为什么需要方块实体：90° 弯里每条模型的旋转角是连续的（几十上百份，不可能用 blockstate 的 4 个朝向表达），
 * 而方块模型只在区块烘焙时生成一次。所以把每份模型的旋转角与格内原点写进方块实体，客户端
 * {@code RailCurveBakedModel} 在区块烘焙时取用（一次烘焙后缓存进区块网格，逐帧不再重算）。
 * <p>
 * 一格可能保存多份：等弧长采样在急弯处会让多个模型实例落入同一格。
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
        private RailStyle style;
        private UUID segmentId;
        private int curveRadius;
        private float curveSweep;
        private float curveArc;
        private float fitAdvance;
        private float fitInward;
        private float curveTotal;
        private boolean curveLeft;
        private float curveEntryAngle;

        public Entry() {
        }

        public Entry(float angle, float originX, float originZ) {
            this(angle, originX, originZ, null, null);
        }

        public Entry(float angle, float originX, float originZ, RailStyle style, UUID segmentId) {
            this(angle, originX, originZ, style, segmentId, 0, 0.0F, 0.0F,
                    0.0F, 0.0F, 0.0F, false, angle);
        }

        public Entry(float angle, float originX, float originZ, RailStyle style, UUID segmentId,
                      int curveRadius, float curveSweep, float curveArc, float fitAdvance,
                     float fitInward, float curveTotal, boolean curveLeft, float curveEntryAngle) {
            this.angle = angle;
            this.originX = originX;
            this.originZ = originZ;
            this.style = style;
            this.segmentId = segmentId;
            this.curveRadius = curveRadius;
            this.curveSweep = curveSweep;
            this.curveArc = curveArc;
            this.fitAdvance = fitAdvance;
            this.fitInward = fitInward;
            this.curveTotal = curveTotal;
            this.curveLeft = curveLeft;
            this.curveEntryAngle = curveEntryAngle;
        }

        public RailStyle style() {
            return style;
        }

        public UUID segmentId() {
            return segmentId;
        }

        public int curveRadius() { return curveRadius; }
        public float curveSweep() { return curveSweep; }
        public float curveArc() { return curveArc; }
        public float fitAdvance() { return fitAdvance; }
        public float fitInward() { return fitInward; }
        public float curveTotal() { return curveTotal; }
        public boolean curveLeft() { return curveLeft; }
        public float curveEntryAngle() { return curveEntryAngle; }

        public Entry withMetadata(RailStyle fallbackStyle, UUID fallbackSegmentId) {
            return new Entry(angle, originX, originZ,
                    style == null ? fallbackStyle : style,
                    segmentId == null ? fallbackSegmentId : segmentId,
                    curveRadius, curveSweep, curveArc, fitAdvance, fitInward, curveTotal, curveLeft,
                    curveEntryAngle);
        }

        public Entry copy() {
            return new Entry(angle, originX, originZ, style, segmentId, curveRadius, curveSweep,
                    curveArc, fitAdvance, fitInward, curveTotal, curveLeft, curveEntryAngle);
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
    /** 这一格用的是哪套外观（弯道方块共用，几何由客户端按外观名选弯道根模型）。 */
    private RailStyle style = RailStyle.STANDARD;

    public RailCurveBlockEntity(BlockPos pos, BlockState state) {
        super(VTBlockEntities.RAIL_CURVE.get(), pos, state);
    }

    /** 设置这一格里的全部模型。 */
    public void setPlacement(List<Entry> placement, int radius, boolean left, RailStyle style) {
        entries.clear();
        entries.addAll(placement);
        this.radius = radius;
        this.left = left;
        this.style = style == null ? RailStyle.STANDARD : style;
        notifyPlacementChanged();
    }

    public void removeSegment(UUID segmentId, boolean removeLegacyEntries) {
        entries.removeIf(entry -> segmentId.equals(entry.segmentId())
                || removeLegacyEntries && entry.segmentId() == null);
        notifyPlacementChanged();
    }

    public void notifyPlacementChanged() {
        setChanged();
        if (level == null) return;
        if (level.isClientSide) {
            requestModelDataUpdate();
        } else {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    /** 这一格的轨道外观。 */
    public RailStyle style() {
        return style;
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
            one.putInt("CurveRadius", entry.curveRadius());
            one.putFloat("CurveSweep", entry.curveSweep());
            one.putFloat("CurveArc", entry.curveArc());
            one.putFloat("FitAdvance", entry.fitAdvance());
            one.putFloat("FitInward", entry.fitInward());
            one.putFloat("CurveTotal", entry.curveTotal());
            one.putBoolean("CurveLeft", entry.curveLeft());
            one.putFloat("CurveEntryAngle", entry.curveEntryAngle());
            if (entry.style() != null) one.putString("Style", entry.style().getSerializedName());
            if (entry.segmentId() != null) one.putUUID("Segment", entry.segmentId());
            list.add(one);
        }
        tag.put("Strips", list);
        tag.putInt("Radius", radius);
        tag.putBoolean("Left", left);
        tag.putString("Style", style.getSerializedName());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        entries.clear();
        ListTag list = tag.getList("Strips", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            RailStyle entryStyle = one.contains("Style")
                    ? RailStyle.byName(one.getString("Style")) : null;
            UUID segmentId = one.hasUUID("Segment") ? one.getUUID("Segment") : null;
            entries.add(new Entry(one.getFloat("Angle"), one.getFloat("OriginX"), one.getFloat("OriginZ"),
                    entryStyle, segmentId, one.getInt("CurveRadius"), one.getFloat("CurveSweep"),
                    one.getFloat("CurveArc"), one.getFloat("FitAdvance"), one.getFloat("FitInward"),
                    one.getFloat("CurveTotal"), one.getBoolean("CurveLeft"),
                    one.contains("CurveEntryAngle") ? one.getFloat("CurveEntryAngle")
                            : one.contains("CurveBaseAngle") ? one.getFloat("CurveBaseAngle")
                            : one.getFloat("Angle")));
        }
        radius = tag.getInt("Radius");
        left = tag.getBoolean("Left");
        style = RailStyle.byName(tag.getString("Style"));
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
