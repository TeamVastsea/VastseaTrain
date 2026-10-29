package com.fastjim.vastseatrain.client.model;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.RenderTypeGroup;
import net.minecraftforge.client.model.IModelBuilder;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.SimpleUnbakedGeometry;
import net.minecraftforge.client.model.obj.ObjModel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 包一层 {@link ObjModel}，烘焙后把每个面的 UV 重新铺成“单位贴图重复”，解决 OBJ 里 UV 越界导致的花屏。
 * <p>
 * 原版方块贴图是一整张图集，每个精灵只能落在自己的矩形里；UV 超出 {@code 0..1} 时会采到图集里相邻的
 * 其他方块贴图，于是出现花屏和原版方块图案混杂。这里把一个面按 UV 跨度切成若干小格，每格独占一份
 * {@code 0..1}，视觉上就是重复贴图。美术文件不需要任何改动。
 */
public class TiledObjGeometry extends SimpleUnbakedGeometry<TiledObjGeometry> {
    /** 顶点格式 {@code BLOCK} 的布局：位置 3、颜色 1、UV 2、光照 1、法线 1，共 8 个 int。 */
    private static final int VERTEX_STRIDE = 8;
    private static final int OFFSET_POSITION = 0;
    private static final int OFFSET_UV = 4;
    /** 单个面最多切成多少格，避免异常 UV 产生海量面。 */
    private static final int MAX_TILES = 24;

    private final ObjModel model;

    public TiledObjGeometry(ObjModel model) {
        this.model = model;
    }

    /**
     * 未使用：所有烘焙都走下面的 {@link #bake}，这里必须实现但不会被调用。
     */
    @Override
    protected void addQuads(IGeometryBakingContext owner, IModelBuilder<?> modelBuilder, ModelBaker baker,
                            Function<Material, TextureAtlasSprite> spriteGetter,
                            ModelState modelTransform, ResourceLocation modelLocation) {
        // no-op
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                           Function<Material, TextureAtlasSprite> spriteGetter,
                           ModelState modelState, ItemOverrides overrides, ResourceLocation modelLocation) {
        BakedModel baked = model.bake(context, baker, spriteGetter, modelState, overrides, modelLocation);

        TextureAtlasSprite particle = spriteGetter.apply(context.getMaterial("particle"));
        ResourceLocation renderTypeHint = context.getRenderTypeHint();
        RenderTypeGroup renderTypes = renderTypeHint != null
                ? context.getRenderType(renderTypeHint) : RenderTypeGroup.EMPTY;
        ResourceLocation renderTypeFastHint = context.getRenderTypeFastHint();
        RenderTypeGroup renderTypesFast = renderTypeFastHint != null
                ? context.getRenderType(renderTypeFastHint) : RenderTypeGroup.EMPTY;

        IModelBuilder<?> builder = IModelBuilder.of(context.useAmbientOcclusion(), context.useBlockLight(),
                context.isGui3d(), context.getTransforms(), overrides, particle, renderTypes, renderTypesFast);

        RandomSource random = RandomSource.create(42L);
        for (Direction direction : Direction.values()) {
            for (BakedQuad quad : baked.getQuads(null, direction, random)) {
                for (BakedQuad tiled : tile(quad)) {
                    builder.addUnculledFace(tiled);
                }
            }
        }
        for (BakedQuad quad : baked.getQuads(null, null, random)) {
            for (BakedQuad tiled : tile(quad)) {
                builder.addUnculledFace(tiled);
            }
        }
        return builder.build();
    }

    /**
     * 把一个面按 UV 跨度切成小格。UV 跨度不超过一格时原样返回，不改动任何顶点。
     */
    private static List<BakedQuad> tile(BakedQuad quad) {
        TextureAtlasSprite sprite = quad.getSprite();
        int[] vertices = quad.getVertices();

        float[] u = new float[4];
        float[] v = new float[4];
        for (int i = 0; i < 4; i++) {
            u[i] = Float.intBitsToFloat(vertices[i * VERTEX_STRIDE + OFFSET_UV]);
            v[i] = Float.intBitsToFloat(vertices[i * VERTEX_STRIDE + OFFSET_UV + 1]);
        }

        float minU = Math.min(Math.min(u[0], u[1]), Math.min(u[2], u[3]));
        float maxU = Math.max(Math.max(u[0], u[1]), Math.max(u[2], u[3]));
        float minV = Math.min(Math.min(v[0], v[1]), Math.min(v[2], v[3]));
        float maxV = Math.max(Math.max(v[0], v[1]), Math.max(v[2], v[3]));

        float spriteU0 = sprite.getU0();
        float spriteV0 = sprite.getV0();
        float spriteUSize = sprite.getU1() - spriteU0;
        float spriteVSize = sprite.getV1() - spriteV0;
        if (spriteUSize <= 0.0F || spriteVSize <= 0.0F) {
            return List.of(quad);
        }

        int tilesU = clampTiles(Math.round((maxU - minU) / spriteUSize));
        int tilesV = clampTiles(Math.round((maxV - minV) / spriteVSize));
        if (tilesU <= 1 && tilesV <= 1) {
            return List.of(quad);
        }

        List<BakedQuad> result = new ArrayList<>(tilesU * tilesV);
        for (int iu = 0; iu < tilesU; iu++) {
            for (int iv = 0; iv < tilesV; iv++) {
                result.add(subQuad(quad, vertices, tilesU, tilesV, iu, iv,
                        spriteU0, spriteV0, spriteUSize, spriteVSize));
            }
        }
        return result;
    }

    private static int clampTiles(long tiles) {
        return (int) Math.max(1L, Math.min(MAX_TILES, tiles));
    }

    /** 双线性插值取小格四角：位置一起插值，UV 归一化到精灵内部 0..1。 */
    private static BakedQuad subQuad(BakedQuad quad, int[] source, int tilesU, int tilesV, int iu, int iv,
                                     float spriteU0, float spriteV0, float spriteUSize, float spriteVSize) {
        int[] out = new int[4 * VERTEX_STRIDE];
        float[][] corners = {
                {0.0F, 0.0F}, {1.0F, 0.0F}, {1.0F, 1.0F}, {0.0F, 1.0F}
        };
        float cellU0 = (float) iu / tilesU;
        float cellV0 = (float) iv / tilesV;
        float cellU1 = (float) (iu + 1) / tilesU;
        float cellV1 = (float) (iv + 1) / tilesV;

        for (int corner = 0; corner < 4; corner++) {
            float a = corners[corner][0];
            float b = corners[corner][1];
            float cellA = cellU0 + (cellU1 - cellU0) * a;
            float cellB = cellV0 + (cellV1 - cellV0) * b;

            int base = corner * VERTEX_STRIDE;
            System.arraycopy(source, base, out, base, VERTEX_STRIDE);
            for (int axis = 0; axis < 3; axis++) {
                float p = bilinearPosition(source, axis, a, b);
                out[base + OFFSET_POSITION + axis] = Float.floatToRawIntBits(p);
            }
            out[base + OFFSET_UV] = Float.floatToRawIntBits(spriteU0 + cellA * spriteUSize);
            out[base + OFFSET_UV + 1] = Float.floatToRawIntBits(spriteV0 + cellB * spriteVSize);
        }
        return new BakedQuad(out, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade());
    }

    private static float bilinearPosition(int[] source, int axis, float a, float b) {
        float p0 = Float.intBitsToFloat(source[OFFSET_POSITION + axis]);
        float p1 = Float.intBitsToFloat(source[VERTEX_STRIDE + OFFSET_POSITION + axis]);
        float p2 = Float.intBitsToFloat(source[2 * VERTEX_STRIDE + OFFSET_POSITION + axis]);
        float p3 = Float.intBitsToFloat(source[3 * VERTEX_STRIDE + OFFSET_POSITION + axis]);
        return (1 - a) * (1 - b) * p0 + a * (1 - b) * p1 + a * b * p2 + (1 - a) * b * p3;
    }
}
