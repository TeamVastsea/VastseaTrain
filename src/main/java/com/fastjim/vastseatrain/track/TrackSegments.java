package com.fastjim.vastseatrain.track;

import com.fastjim.vastseatrain.registry.VTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 记录“蓝图铺下的一段轨道”包含哪些方块，使一段轨道成为整体：
 * 破坏其中任意一格，整段一起被移除；只有全部格子都没了，记录才被清理。
 * <p>
 * 数据按维度保存在世界存档里（{@code SavedData}），跨存档重载有效；
 * 没有记录归属的方块（例如物品单独放置的轨道）仍然是普通单格方块。
 */
public class TrackSegments extends SavedData {
    private static final String DATA_NAME = "vastseatrain_track_segments";

    /** 方块位置 → 所属段 ID。 */
    private final Map<BlockPos, UUID> byPos = new HashMap<>();
    /** 段 ID → 该段包含的全部方块位置。 */
    private final Map<UUID, List<BlockPos>> bySegment = new HashMap<>();

    public static TrackSegments get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                TrackSegments::load,
                TrackSegments::new,
                DATA_NAME);
    }

    /** 把一组方块登记为同一段轨道。 */
    public void addSegment(UUID segmentId, List<BlockPos> positions) {
        List<BlockPos> list = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            BlockPos immutable = pos.immutable();
            UUID previous = byPos.put(immutable, segmentId);
            if (previous != null && !previous.equals(segmentId)) {
                removeFromSegment(previous, immutable);
            }
            list.add(immutable);
        }
        bySegment.computeIfAbsent(segmentId, id -> new ArrayList<>()).addAll(list);
        setDirty();
    }

    /** 方块所在段的 ID，没有归属时返回 null。 */
    public UUID segmentAt(BlockPos pos) {
        return byPos.get(pos);
    }

    /** 该位置是否属于某一段轨道。 */
    public boolean isTracked(BlockPos pos) {
        return byPos.containsKey(pos);
    }

    /** 该段包含的全部方块（可能已被其他途径移除，调用方需自行检查世界状态）。 */
    public List<BlockPos> memberPositions(UUID segmentId) {
        List<BlockPos> list = bySegment.get(segmentId);
        return list == null ? List.of() : List.copyOf(list);
    }

    /** 方块被移除后同步记录；段内已没有方块时删除整段。 */
    public void forget(BlockPos pos) {
        UUID segmentId = byPos.remove(pos);
        if (segmentId == null) {
            return;
        }
        removeFromSegment(segmentId, pos);
        setDirty();
    }

    private void removeFromSegment(UUID segmentId, BlockPos pos) {
        List<BlockPos> list = bySegment.get(segmentId);
        if (list == null) {
            return;
        }
        list.remove(pos);
        if (list.isEmpty()) {
            bySegment.remove(segmentId);
        }
    }

    /**
     * 整段移除：先清空记录，再把仍属于本模组的轨道方块一起清掉。
     *
     * @return 实际移除的方块数
     */
    public int removeSegment(ServerLevel level, UUID segmentId) {
        List<BlockPos> positions = new ArrayList<>(memberPositions(segmentId));
        bySegment.remove(segmentId);
        int removed = 0;
        for (BlockPos pos : positions) {
            byPos.remove(pos);
            BlockState state = level.getBlockState(pos);
            if (state.is(VTBlocks.RAIL.get())) {
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                removed++;
            }
        }
        setDirty();
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag segments = new ListTag();
        for (Map.Entry<UUID, List<BlockPos>> entry : bySegment.entrySet()) {
            CompoundTag segmentTag = new CompoundTag();
            segmentTag.putUUID("id", entry.getKey());
            ListTag positions = new ListTag();
            for (BlockPos pos : entry.getValue()) {
                positions.add(nbt(pos));
            }
            segmentTag.put("blocks", positions);
            segments.add(segmentTag);
        }
        tag.put("segments", segments);
        return tag;
    }

    private static TrackSegments load(CompoundTag tag) {
        TrackSegments data = new TrackSegments();
        ListTag segments = tag.getList("segments", Tag.TAG_COMPOUND);
        for (int i = 0; i < segments.size(); i++) {
            CompoundTag segmentTag = segments.getCompound(i);
            if (!segmentTag.hasUUID("id")) {
                continue;
            }
            UUID id = segmentTag.getUUID("id");
            ListTag positions = segmentTag.getList("blocks", Tag.TAG_COMPOUND);
            List<BlockPos> blocks = new ArrayList<>(positions.size());
            for (int j = 0; j < positions.size(); j++) {
                blocks.add(pos(positions.getCompound(j)));
            }
            data.addSegment(id, blocks);
        }
        return data;
    }

    private static CompoundTag nbt(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }

    private static BlockPos pos(CompoundTag tag) {
        return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }
}
