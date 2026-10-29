package com.fastjim.vastseatrain.client.model;

import com.fastjim.vastseatrain.VastseaTrain;
import org.joml.Vector3f;

/**
 * 轨道模型的启动期日志：用来确认自定义加载器真的在跑、切块数量是否符合预期。
 * <p>
 * 排查“材质看起来不对”时先看这一行；数值对不上说明模型没走 {@code vastseatrain:rail_obj}。
 */
final class RailModelLog {
    private static boolean logged;

    private RailModelLog() {
    }

    static void baked(int meshes, int patches, int quads, Vector3f translation) {
        if (logged) {
            return;
        }
        logged = true;
        VastseaTrain.LOGGER.info("轨道模型已烘焙：{} 个材质段，{} 个补丁，{} 个四边形，平移 ({}, {}, {})",
                meshes, patches, quads, translation.x(), translation.y(), translation.z());
    }

    /** 烘焙期发现的数据异常（例如 UV 跨度被截断）。 */
    static void warn(String format, Object... args) {
        VastseaTrain.LOGGER.warn(format, args);
    }
}
