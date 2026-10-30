package com.fastjim.vastseatrain.client.model;

import com.fastjim.vastseatrain.track.RailStyle;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * 弯道模型：先按直道的方式烘焙一份完整模型，用作粒子贴图、渲染层、AO 等属性的来源；渲染时由
 * {@link RailCurveBakedModel} 按方块实体给出的角度与偏移现算顶点。
 * <p>
 * 弯道方块是所有外观共用的，所以烘焙时把<b>每一套外观</b>的弯道面数据都准备好，
 * 渲染时按方块实体记录的外观名取用（见 {@link #bake}、{@link #withStyles}）。
 */
public final class RailCurveGeometry implements IUnbakedGeometry<RailCurveGeometry> {
    private final RailObjGeometry geometry;
    private final JsonObject rootJson;
    /** 其它外观的弯道根模型 JSON：外观 → JSON。烘焙时一次性转成面。 */
    private final Map<RailStyle, JsonObject> others;

    public RailCurveGeometry(RailObjGeometry geometry, JsonObject rootJson) {
        this(geometry, rootJson, Map.of());
    }

    private RailCurveGeometry(RailObjGeometry geometry, JsonObject rootJson, Map<RailStyle, JsonObject> others) {
        this.geometry = geometry;
        this.rootJson = rootJson;
        this.others = others;
    }

    /** 附上“其它外观的弯道 JSON”（由 {@link RailCurveObjLoader} 在解析后调用）。 */
    public RailCurveGeometry withStyles(Map<RailStyle, JsonObject> otherJson) {
        return new RailCurveGeometry(geometry, rootJson, otherJson);
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                           Function<Material, TextureAtlasSprite> spriteGetter,
                           ModelState modelState, ItemOverrides overrides, ResourceLocation modelLocation) {
        // 兜底用的直道模型：只取粒子贴图等属性。弯道顶点一律现算，不经过它，
        // 所以这里用单位变换烘焙一次，避免 blockstate 的旋转被算两遍。
        BakedModel straight = geometry.bake(context, baker, spriteGetter, new ModelState() { }, overrides, modelLocation);
        List<RailObjGeometry.Face> faces = geometry.toFaces(
                name -> spriteGetter.apply(material(rootJson, name)));

        RailObjLoader loader = new RailObjLoader();
        JsonDeserializationContext deserialization = new JsonDeserializationContext() {
            @Override
            public <T> T deserialize(com.google.gson.JsonElement json, java.lang.reflect.Type typeOfT) {
                return null;
            }
        };
        Map<RailStyle, RailCurveBakedModel.StyleFaces> styles = new EnumMap<>(RailStyle.class);
        for (Map.Entry<RailStyle, JsonObject> entry : others.entrySet()) {
            RailObjGeometry other = loader.parse(entry.getValue(), deserialization);
            styles.put(entry.getKey(), new RailCurveBakedModel.StyleFaces(other.toFaces(
                    name -> spriteGetter.apply(material(entry.getValue(), name))), other.shadeQuads()));
        }
        return new RailCurveBakedModel(straight, faces, geometry.shadeQuads(), styles,
                context.useAmbientOcclusion());
    }

    private static Material material(JsonObject root, String reference) {
        String texture = reference;
        if (reference.startsWith("#")) {
            JsonObject textures = root.getAsJsonObject("textures");
            com.google.gson.JsonElement value = textures == null ? null : textures.get(reference.substring(1));
            if (value == null || !value.isJsonPrimitive()) {
                throw new IllegalStateException("弯道模型缺少贴图键 " + reference);
            }
            texture = value.getAsString();
        }
        return new Material(InventoryMenu.BLOCK_ATLAS, ResourceLocation.parse(texture));
    }

    // ------------------------------------------------------------------ 其它外观的 JSON

    /**
     * 读取某一套外观的弯道根模型 JSON。
     * <p>
     * 弯道方块共用，其它外观的弯道模型不会成为“当前方块模型”而被直接烘焙，
     * 所以这里直接读资源；它们与直道共用同一份 OBJ/MTL 与贴图键，只是外层 loader 不同。
     * 贴图键也从这份 JSON 的 {@code textures} 里取（见 {@link RailObjGeometry#textureKeys()}）。
     */
    public static Optional<JsonObject> readStyleJson(RailStyle style) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("vastseatrain",
                "models/block/" + style.curveModel() + ".json");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            RailModelLog.warn("找不到弯道模型文件 {}", id);
            return Optional.empty();
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8))) {
            return Optional.of(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            RailModelLog.warn("无法读取弯道模型文件 {}：{}", id, e.toString());
            return Optional.empty();
        }
    }

    /** 读一遍所有外观的弯道 JSON（按枚举顺序）。 */
    public static Map<RailStyle, JsonObject> readAllStyleJson() {
        Map<RailStyle, JsonObject> result = new LinkedHashMap<>();
        for (RailStyle style : RailStyle.values()) {
            readStyleJson(style).ifPresent(json -> result.put(style, json));
        }
        return result;
    }
}
