package com.fastjim.vastseatrain.client.texture;

import com.fastjim.vastseatrain.VastseaTrain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 资源重载后只做一件事：把美术目录 {@code models/block/rail/} 扫描一遍并打日志。
 * <p>
 * 贴图本身由 {@link RailTextureLoader} 在图集重建时读取，这里<b>不碰图集</b>——
 * 重载顺序里图集由原版自己安排，额外插手只会把图集状态搞乱。
 * 日志的作用是让美术（或排错时）能一眼看到“游戏到底读到了哪些文件”。
 */
public final class RailArtReloadListener extends SimplePreparableReloadListener<Map<String, Long>> {
    public static final RailArtReloadListener INSTANCE = new RailArtReloadListener();

    /** 最近一次扫描到的美术文件：完整资源路径 → 字节数。 */
    private static volatile Map<String, Long> lastScan = Map.of();

    /** 最近一次扫描到的“贴图 → 美术文件”。 */
    private static volatile Map<ResourceLocation, ResourceLocation> lastSources = Map.of();

    private RailArtReloadListener() {
    }

    @Override
    protected Map<String, Long> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, ResourceLocation> sources = RailArtSource.scan(resourceManager);
        lastSources = Map.copyOf(sources);
        return RailArtSource.listFiles(resourceManager);
    }

    @Override
    protected void apply(Map<String, Long> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        lastScan = Map.copyOf(files);
        VastseaTrain.LOGGER.info("美术目录贴图：{} 个来源文件（assets/<命名空间>/{}）",
                files.size(), RailArtSource.SOURCE_DIR);
        new TreeMap<>(files).forEach((id, size) ->
                VastseaTrain.LOGGER.info("  美术文件 {}（{} 字节）", id, size < 0 ? "未知" : size));
    }

    /** 最近一次扫描到的文件（副本）。 */
    public static Map<String, Long> lastScan() {
        return new HashMap<>(lastScan);
    }

    /** 最近一次扫描到的“贴图 → 美术文件”（副本）。 */
    public static Map<ResourceLocation, ResourceLocation> lastSources() {
        return new HashMap<>(lastSources);
    }
}
