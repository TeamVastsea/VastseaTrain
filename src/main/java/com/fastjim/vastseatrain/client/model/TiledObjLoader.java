package com.fastjim.vastseatrain.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.obj.ObjLoader;
import net.minecraftforge.client.model.obj.ObjModel;

/**
 * 与 {@code forge:obj} 相同，但烘焙时会把越界 UV 铺成重复贴图，见 {@link TiledObjGeometry}。
 * <p>
 * 美术导出的 OBJ 里，道砟 / 轨枕 / 扣件的 UV 按世界尺寸书写（例如 {@code u ∈ -1.6..2.3}），
 * 原版图集采样越界就会取到相邻方块贴图；钢轨的 UV 恰好落在 {@code 0..1}，所以只有钢轨正常。
 */
public class TiledObjLoader implements IGeometryLoader<TiledObjGeometry> {
    @Override
    public TiledObjGeometry read(JsonObject json, JsonDeserializationContext context) {
        String modelLocation = GsonHelper.getAsString(json, "model");
        boolean automaticCulling = GsonHelper.getAsBoolean(json, "automatic_culling", true);
        boolean shadeQuads = GsonHelper.getAsBoolean(json, "shade_quads", true);
        boolean flipV = GsonHelper.getAsBoolean(json, "flip_v", false);
        boolean emissiveAmbient = GsonHelper.getAsBoolean(json, "emissive_ambient", true);
        String mtlOverride = GsonHelper.getAsString(json, "mtl_override", null);

        ObjModel obj = ObjLoader.INSTANCE.loadModel(new ObjModel.ModelSettings(
                ResourceLocation.parse(modelLocation), automaticCulling, shadeQuads, flipV,
                emissiveAmbient, mtlOverride));
        return new TiledObjGeometry(obj);
    }
}
