package com.fastjim.vastseatrain.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.client.model.geometry.IGeometryLoader;

/**
 * 弯道模型加载器：{@code vastseatrain:rail_curve_obj}。
 * <p>
 * 与直道 {@link RailObjLoader} 读同一份美术 OBJ、同一套贴图映射，差别在于烘焙结果：
 * 直道烘焙成一份固定摆放的 {@link net.minecraft.client.resources.model.BakedModel}，
 * 弯道则把整理好的面数据交给 {@link RailCurveBakedModel}，在区块烘焙时按方块实体的角度现算顶点。
 */
public class RailCurveObjLoader implements IGeometryLoader<RailCurveGeometry> {
    @Override
    public RailCurveGeometry read(JsonObject json, JsonDeserializationContext context) {
        String modelLocation = GsonHelper.getAsString(json, "model");
        String mtlOverride = GsonHelper.getAsString(json, "mtl_override", null);
        boolean flipV = GsonHelper.getAsBoolean(json, "flip_v", false);
        boolean shadeQuads = GsonHelper.getAsBoolean(json, "shade_quads", true);

        RailObjGeometry geometry = RailObjGeometry.parse(
                context,
                json,
                ResourceLocation.parse(modelLocation),
                ResourceLocation.parse(mtlOverride),
                flipV,
                shadeQuads);
        return new RailCurveGeometry(geometry);
    }
}
