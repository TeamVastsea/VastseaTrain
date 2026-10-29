package com.fastjim.vastseatrain.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.client.model.geometry.IGeometryLoader;

/**
 * 轨道 OBJ 加载器：{@code vastseatrain:rail_obj}。
 * <p>
 * 与 {@code forge:obj} 的差别只有两点，都是为了适配美术导出的文件：
 * <ol>
 *     <li>美术的 UV 是按世界尺寸投影的（1 个单位 = 1 张贴图，例如道砟 {@code u ∈ -1.64..2.34}），
 *     而原版方块贴图是图集，采样越界会取到别的方块贴图。这里把每个三角面按 UV 网格裁剪并逐格重复整张贴图。</li>
 *     <li>{@code forge:obj} 把 JSON 的 {@code transform.translation} 放在方块朝向旋转<b>之内</b>，
 *     导致同一个偏移在四个朝向上指向不同方向。本加载器改为 {@code pos = Rot(朝向) · p + translation}，
 *     平移方向与朝向无关。</li>
 * </ol>
 * 解析与烘焙实现见 {@link RailObjGeometry}。
 */
public class RailObjLoader implements IGeometryLoader<RailObjGeometry> {
    @Override
    public RailObjGeometry read(JsonObject json, JsonDeserializationContext context) {
        String modelLocation = GsonHelper.getAsString(json, "model");
        String mtlOverride = GsonHelper.getAsString(json, "mtl_override", null);
        boolean flipV = GsonHelper.getAsBoolean(json, "flip_v", false);
        boolean shadeQuads = GsonHelper.getAsBoolean(json, "shade_quads", true);

        return parse(json, mtlOverride, modelLocation, flipV, shadeQuads, context);
    }

    /**
     * 用一份根模型 JSON 解析出几何（不含烘焙）。
     * <p>
     * 供弯道加载器复用：弯道方块的其它外观共用这份直道解析逻辑。
     */
    RailObjGeometry parse(JsonObject json, JsonDeserializationContext context) {
        String modelLocation = GsonHelper.getAsString(json, "model");
        String mtlOverride = GsonHelper.getAsString(json, "mtl_override", null);
        boolean flipV = GsonHelper.getAsBoolean(json, "flip_v", false);
        boolean shadeQuads = GsonHelper.getAsBoolean(json, "shade_quads", true);
        return parse(json, mtlOverride, modelLocation, flipV, shadeQuads, context);
    }

    private static RailObjGeometry parse(JsonObject json, String mtlOverride, String modelLocation,
                                         boolean flipV, boolean shadeQuads, JsonDeserializationContext context) {
        return RailObjGeometry.parse(
                context,
                json,
                ResourceLocation.parse(modelLocation),
                ResourceLocation.parse(mtlOverride),
                flipV,
                shadeQuads);
    }
}
