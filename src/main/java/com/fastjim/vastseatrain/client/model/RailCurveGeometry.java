package com.fastjim.vastseatrain.client.model;

import com.mojang.math.Transformation;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.Function;

/**
 * 弯道模型：先按直道的方式烘焙一份完整模型，用作粒子贴图、渲染层、AO 等属性的来源；渲染时由
 * {@link RailCurveBakedModel} 按方块实体给出的角度与偏移现算顶点。
 */
public final class RailCurveGeometry implements IUnbakedGeometry<RailCurveGeometry> {
    private final RailObjGeometry geometry;

    public RailCurveGeometry(RailObjGeometry geometry) {
        this.geometry = geometry;
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                           Function<Material, TextureAtlasSprite> spriteGetter,
                           ModelState modelState, ItemOverrides overrides, ResourceLocation modelLocation) {
        // 兜底用的直道模型：只取粒子贴图等属性。弯道顶点一律现算，不经过它，
        // 所以这里用单位变换烘焙一次，避免 blockstate 的旋转被算两遍。
        BakedModel straight = geometry.bake(context, baker, spriteGetter, new ModelState() { }, overrides, modelLocation);
        List<RailObjGeometry.Face> faces = geometry.toFaces(
                name -> spriteGetter.apply(context.getMaterial(name.startsWith("#") ? name.substring(1) : name)));
        return new RailCurveBakedModel(straight, geometry.origin(), faces, context.useAmbientOcclusion());
    }
}
