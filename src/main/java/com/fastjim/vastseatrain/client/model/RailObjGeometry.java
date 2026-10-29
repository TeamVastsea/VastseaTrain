package com.fastjim.vastseatrain.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Transformation;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.RenderTypeGroup;
import net.minecraftforge.client.model.IModelBuilder;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;
import net.minecraftforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 直接解析美术 OBJ 并烘焙成方块模型，绕开 {@code forge:obj} 的两个坑：
 * <ul>
 *     <li><b>UV 重复</b>：美术的 UV 是世界投影（1 单位 = 1 张贴图）。原版图集不支持重复采样，
 *     越界就会取到相邻方块贴图（花屏、原版图案）。这里把每个三角面按 UV 整数格切块，
 *     每块独立映射一整张贴图，视觉效果与渲染器的“重复采样”一致。</li>
 *     <li><b>平移方向</b>：{@code translation} 在朝向旋转之后叠加，四个朝向平移方向一致。</li>
 * </ul>
 * 坐标约定：美术 OBJ 以模型为原点；最终顶点 = {@code Rot(朝向) · p + translation}，
 * 即 {@code translation} 就是“模型原点落在方块局部坐标里的位置”，{@code [0.5, 0.5, 0.5]} 表示方块中心。
 */
public final class RailObjGeometry implements IUnbakedGeometry<RailObjGeometry> {
    /** 顶点格式 {@code BLOCK}：位置(3) 颜色(1) UV(2) 光照(1) 法线(1) 填充(1) = 9 个 int，前 8 个有效。 */
    private static final int VERTEX_STRIDE = DefaultVertexFormat.BLOCK.getIntegerSize();
    private static final int OFFSET_POSITION = findOffset(DefaultVertexFormat.ELEMENT_POSITION);
    private static final int OFFSET_COLOR = findOffset(DefaultVertexFormat.ELEMENT_COLOR);
    private static final int OFFSET_UV = findOffset(DefaultVertexFormat.ELEMENT_UV0);
    private static final int OFFSET_UV2 = findOffset(DefaultVertexFormat.ELEMENT_UV2);
    private static final int OFFSET_NORMAL = findOffset(DefaultVertexFormat.ELEMENT_NORMAL);
    private static final int OFFSET_PADDING = findOffset(DefaultVertexFormat.ELEMENT_PADDING);

    /** 单个三角面单方向最多切成多少格，防止异常 UV 产生海量面。 */
    private static final int MAX_TILES = 16;
    /** 裁剪三角形时允许的浮点误差。 */
    private static final float EPSILON = 1.0E-5F;

    private final List<Mesh> meshes = new ArrayList<>();
    private final Vector3f translation;
    private final boolean flipV;
    private final boolean shadeQuads;

    private RailObjGeometry(Vector3f translation, boolean flipV, boolean shadeQuads) {
        this.translation = translation;
        this.flipV = flipV;
        this.shadeQuads = shadeQuads;
    }

    /** 一个 usemtl 段：同一材质的所有三角面。 */
    private static final class Mesh {
        /** 材质引用，形如 {@code #ballast}，由根模型 JSON 的 {@code textures} 解析。 */
        private final String texture;
        private final List<Triangle> triangles = new ArrayList<>();

        private Mesh(String texture) {
            this.texture = texture;
        }
    }

    /** 三角面：三个顶点的模型坐标与 UV。 */
    private record Triangle(Vector3f[] position, float[] u, float[] v) {
    }

    private static int findOffset(VertexFormatElement element) {
        int index = DefaultVertexFormat.BLOCK.getElements().indexOf(element);
        return index < 0 ? -1 : DefaultVertexFormat.BLOCK.getOffset(index) / 4;
    }

    // ------------------------------------------------------------------ 解析

    public static RailObjGeometry parse(JsonDeserializationContext context, JsonObject json,
                                        ResourceLocation modelLocation, ResourceLocation mtlLocation,
                                        boolean flipV, boolean shadeQuads) {
        JsonObject transform = json.has("transform")
                ? GsonHelper.getAsJsonObject(json, "transform") : new JsonObject();
        Vector3f translation = new Vector3f(0.0F, 0.0F, 0.0F);
        if (transform.has("translation")) {
            Vector3f decoded = context.deserialize(transform.get("translation"), Vector3f.class);
            if (decoded != null) {
                translation = decoded;
            }
        }

        RailObjGeometry geometry = new RailObjGeometry(translation, flipV, shadeQuads);

        Map<String, String> materialTextures = readMaterialLibrary(mtlLocation);
        geometry.readModel(modelLocation, materialTextures);
        return geometry;
    }

    /** 读取 MTL，返回“材质名 → 贴图引用”（例如 {@code Material_#80} → {@code #ballast}）。 */
    private static Map<String, String> readMaterialLibrary(ResourceLocation location) {
        Map<String, String> result = new HashMap<>();
        Resource resource = load(location);
        if (resource == null) {
            return result;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.open(), StandardCharsets.US_ASCII))) {
            String current = null;
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 2) {
                    continue;
                }
                if ("newmtl".equals(parts[0])) {
                    current = line.trim().substring("newmtl".length()).trim();
                } else if ("map_Kd".equals(parts[0]) && current != null) {
                    // map_Kd 可能带选项，贴图路径总是最后一段
                    result.put(current, parts[parts.length - 1]);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("无法读取材质库 " + location, e);
        }
        return result;
    }

    private void readModel(ResourceLocation location, Map<String, String> materialTextures) {
        Resource resource = load(location);
        if (resource == null) {
            throw new IllegalStateException("找不到轨道模型 " + location);
        }

        List<Vector3f> positions = new ArrayList<>();
        List<float[]> texCoords = new ArrayList<>();
        Mesh currentMesh = null;
        String currentMaterial = null;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.open(), StandardCharsets.US_ASCII))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+");
                switch (parts[0]) {
                    case "v" -> positions.add(new Vector3f(
                            Float.parseFloat(parts[1]),
                            Float.parseFloat(parts[2]),
                            Float.parseFloat(parts[3])));
                    case "vt" -> {
                        float u = Float.parseFloat(parts[1]);
                        float v = parts.length > 2 ? Float.parseFloat(parts[2]) : 0.0F;
                        texCoords.add(new float[]{u, flipV ? 1.0F - v : v});
                    }
                    case "usemtl" -> {
                        currentMaterial = trimmed.substring("usemtl".length()).trim();
                        String texture = materialTextures.get(currentMaterial);
                        currentMesh = texture == null ? null : new Mesh(texture);
                        if (currentMesh != null) {
                            meshes.add(currentMesh);
                        }
                    }
                    case "f" -> {
                        if (currentMesh != null) {
                            addFace(currentMesh, parts, positions, texCoords);
                        }
                    }
                    default -> {
                        // o / g / s / mtllib / vn / vc 等无需处理
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("无法解析轨道模型 " + location, e);
        }
    }

    private static void addFace(Mesh mesh, String[] parts, List<Vector3f> positions, List<float[]> texCoords) {
        int count = parts.length - 1;
        if (count < 3) {
            return;
        }
        // 统一按三角形扇形拆分，美术文件里是三角形，这里兼容多边形
        for (int i = 1; i + 1 < count; i++) {
            Vector3f p0 = vertexPosition(parts[1], positions);
            Vector3f p1 = vertexPosition(parts[i + 1], positions);
            Vector3f p2 = vertexPosition(parts[i + 2], positions);
            float[] uv0 = vertexUv(parts[1], texCoords);
            float[] uv1 = vertexUv(parts[i + 1], texCoords);
            float[] uv2 = vertexUv(parts[i + 2], texCoords);
            if (p0 == null || p1 == null || p2 == null || uv0 == null || uv1 == null || uv2 == null) {
                continue;
            }
            mesh.triangles.add(new Triangle(
                    new Vector3f[]{p0, p1, p2},
                    new float[]{uv0[0], uv1[0], uv2[0]},
                    new float[]{uv0[1], uv1[1], uv2[1]}));
        }
    }

    @Nullable
    private static Vector3f vertexPosition(String token, List<Vector3f> positions) {
        int index = indexOf(token.split("/")[0], positions.size());
        return index < 0 ? null : positions.get(index);
    }

    @Nullable
    private static float[] vertexUv(String token, List<float[]> texCoords) {
        String[] parts = token.split("/");
        if (parts.length < 2 || parts[1].isEmpty()) {
            return null;
        }
        int index = indexOf(parts[1], texCoords.size());
        return index < 0 ? null : texCoords.get(index);
    }

    /** OBJ 下标：正数为 1 起，负数为从末尾倒数。 */
    private static int indexOf(String raw, int size) {
        int value = Integer.parseInt(raw);
        int index = value < 0 ? size + value : value - 1;
        return index >= 0 && index < size ? index : -1;
    }

    @Nullable
    private static Resource load(ResourceLocation location) {
        return MinecraftHolder.resource(location);
    }

    // ------------------------------------------------------------------ 烘焙

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                           Function<Material, TextureAtlasSprite> spriteGetter,
                           ModelState modelState, ItemOverrides overrides, ResourceLocation modelLocation) {
        TextureAtlasSprite particle = spriteGetter.apply(context.getMaterial("particle"));
        ResourceLocation renderTypeHint = context.getRenderTypeHint();
        RenderTypeGroup renderTypes = renderTypeHint != null
                ? context.getRenderType(renderTypeHint) : RenderTypeGroup.EMPTY;
        ResourceLocation renderTypeFastHint = context.getRenderTypeFastHint();
        RenderTypeGroup renderTypesFast = renderTypeFastHint != null
                ? context.getRenderType(renderTypeFastHint) : RenderTypeGroup.EMPTY;

        IModelBuilder<?> builder = IModelBuilder.of(context.useAmbientOcclusion(), context.useBlockLight(),
                context.isGui3d(), context.getTransforms(), overrides, particle, renderTypes, renderTypesFast);

        // 朝向旋转（来自 blockstate 的 y/x 旋转），围绕方块中心
        Transformation rotation = modelState.getRotation();
        boolean rotate = !rotation.isIdentity();

        int emitted = 0;
        RandomSource random = RandomSource.create();
        for (Mesh mesh : meshes) {
            TextureAtlasSprite sprite = spriteGetter.apply(context.getMaterial(mesh.texture));
            for (Triangle triangle : mesh.triangles) {
                List<float[]> pieces = tileUv(triangle);
                for (float[] piece : pieces) {
                    BakedQuad quad = buildQuad(piece, triangle, sprite, rotation, rotate);
                    if (quad != null) {
                        builder.addUnculledFace(quad);
                        emitted++;
                    }
                }
            }
        }
        RailModelLog.baked(meshes.size(), emitted, translation);
        return builder.build();
    }

    /**
     * 把一个三角面按 UV 整数格切块，每块返回 15 个 float：三个顶点的 位置(3) + UV(2)。
     * <p>
     * 三角面从模型坐标到 UV 的映射是仿射的，所以“在 UV 空间裁剪三角形”等价于“在模型上切分”，
     * 切块后形状与原来完全一致，只是每块的 UV 都归一化到 {@code 0..1}。
     */
    private List<float[]> tileUv(Triangle triangle) {
        float[] u = triangle.u();
        float[] v = triangle.v();
        float minU = Math.min(u[0], Math.min(u[1], u[2]));
        float maxU = Math.max(u[0], Math.max(u[1], u[2]));
        float minV = Math.min(v[0], Math.min(v[1], v[2]));
        float maxV = Math.max(v[0], Math.max(v[1], v[2]));

        int tilesU = clampTiles(Math.round(maxU - minU));
        int tilesV = clampTiles(Math.round(maxV - minV));

        float minX = Math.min(triangle.position()[0].x(), Math.min(triangle.position()[1].x(), triangle.position()[2].x()));
        float maxX = Math.max(triangle.position()[0].x(), Math.max(triangle.position()[1].x(), triangle.position()[2].x()));
        float minY = Math.min(triangle.position()[0].y(), Math.min(triangle.position()[1].y(), triangle.position()[2].y()));
        float maxY = Math.max(triangle.position()[0].y(), Math.max(triangle.position()[1].y(), triangle.position()[2].y()));
        float minZ = Math.min(triangle.position()[0].z(), Math.min(triangle.position()[1].z(), triangle.position()[2].z()));
        float maxZ = Math.max(triangle.position()[0].z(), Math.max(triangle.position()[1].z(), triangle.position()[2].z()));

        if (tilesU <= 1 && tilesV <= 1) {
            // 单格：直接把 UV 归一化，等价于原样使用
            List<float[]> single = new ArrayList<>(1);
            float[] out = new float[15];
            for (int i = 0; i < 3; i++) {
                out[i * 5] = triangle.position()[i].x();
                out[i * 5 + 1] = triangle.position()[i].y();
                out[i * 5 + 2] = triangle.position()[i].z();
                out[i * 5 + 3] = (u[i] - minU) / Math.max(maxU - minU, EPSILON);
                out[i * 5 + 4] = (v[i] - minV) / Math.max(maxV - minV, EPSILON);
            }
            single.add(out);
            return single;
        }

        List<float[]> result = new ArrayList<>(tilesU * tilesV);
        float cellU = (maxU - minU) / tilesU;
        float cellV = (maxV - minV) / tilesV;
        // 顶点在 UV 空间里的多边形
        List<float[]> polygon = new ArrayList<>(3);
        polygon.add(new float[]{u[0], v[0]});
        polygon.add(new float[]{u[1], v[1]});
        polygon.add(new float[]{u[2], v[2]});

        for (int iu = 0; iu < tilesU; iu++) {
            for (int iv = 0; iv < tilesV; iv++) {
                float u0 = minU + iu * cellU;
                float v0 = minV + iv * cellV;
                float u1 = u0 + cellU;
                float v1 = v0 + cellV;
                List<float[]> clipped = clip(polygon, u0, v0, u1, v1);
                if (clipped.size() < 3) {
                    continue;
                }
                float[] flat = new float[clipped.size() * 5];
                for (int i = 0; i < clipped.size(); i++) {
                    float[] uv = clipped.get(i);
                    float[] pos = positionAt(triangle, uv[0], uv[1]);
                    flat[i * 5] = pos[0];
                    flat[i * 5 + 1] = pos[1];
                    flat[i * 5 + 2] = pos[2];
                    flat[i * 5 + 3] = clamp01((uv[0] - u0) / Math.max(cellU, EPSILON));
                    flat[i * 5 + 4] = clamp01((uv[1] - v0) / Math.max(cellV, EPSILON));
                }
                result.add(flat);
            }
        }
        if (result.isEmpty()) {
            // 极端情况下（UV 退化）退回单格
            List<float[]> single = new ArrayList<>(1);
            single.add(new float[]{
                    triangle.position()[0].x(), triangle.position()[0].y(), triangle.position()[0].z(), 0.0F, 0.0F,
                    triangle.position()[1].x(), triangle.position()[1].y(), triangle.position()[1].z(), 1.0F, 0.0F,
                    triangle.position()[2].x(), triangle.position()[2].y(), triangle.position()[2].z(), 1.0F, 1.0F});
            return single;
        }
        return result;
    }

    private static int clampTiles(long tiles) {
        return (int) Math.max(1L, Math.min(MAX_TILES, tiles));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    /** Sutherland–Hodgman：把 UV 空间多边形裁剪到矩形内。 */
    private static List<float[]> clip(List<float[]> polygon, float u0, float v0, float u1, float v1) {
        List<float[]> current = new ArrayList<>(polygon);
        current = clipEdge(current, 0, u0, true);
        current = clipEdge(current, 0, u1, false);
        current = clipEdge(current, 1, v0, true);
        current = clipEdge(current, 1, v1, false);
        return current;
    }

    private static List<float[]> clipEdge(List<float[]> input, int axis, float limit, boolean keepGreater) {
        List<float[]> output = new ArrayList<>(input.size() + 2);
        int size = input.size();
        for (int i = 0; i < size; i++) {
            float[] current = input.get(i);
            float[] next = input.get((i + 1) % size);
            boolean currentIn = keepGreater ? current[axis] >= limit - EPSILON : current[axis] <= limit + EPSILON;
            boolean nextIn = keepGreater ? next[axis] >= limit - EPSILON : next[axis] <= limit + EPSILON;
            if (currentIn) {
                output.add(current);
            }
            if (currentIn != nextIn) {
                float denominator = next[axis] - current[axis];
                if (Math.abs(denominator) > EPSILON) {
                    float t = (limit - current[axis]) / denominator;
                    output.add(new float[]{
                            current[0] + (next[0] - current[0]) * t,
                            current[1] + (next[1] - current[1]) * t});
                }
            }
        }
        return output;
    }

    /** 由 UV 反求模型坐标（三角形内 UV 到位置的映射是仿射的）。 */
    private static float[] positionAt(Triangle triangle, float u, float v) {
        float[] uu = triangle.u();
        float[] vv = triangle.v();
        float denom = (vv[1] - vv[2]) * (uu[0] - uu[2]) + (uu[2] - uu[1]) * (vv[0] - vv[2]);
        if (Math.abs(denom) < EPSILON) {
            return new float[]{
                    triangle.position()[0].x(), triangle.position()[0].y(), triangle.position()[0].z()};
        }
        float a = ((vv[1] - vv[2]) * (u - uu[2]) + (uu[2] - uu[1]) * (v - vv[2])) / denom;
        float b = ((vv[2] - vv[0]) * (u - uu[2]) + (uu[0] - uu[2]) * (v - vv[2])) / denom;
        float c = 1.0F - a - b;
        Vector3f p0 = triangle.position()[0];
        Vector3f p1 = triangle.position()[1];
        Vector3f p2 = triangle.position()[2];
        return new float[]{
                p0.x() * a + p1.x() * b + p2.x() * c,
                p0.y() * a + p1.y() * b + p2.y() * c,
                p0.z() * a + p1.z() * b + p2.z() * c};
    }

    /**
     * 把切好的多边形（扇形三角化后逐个）转成 {@link BakedQuad}。
     * 位置先按朝向旋转，再叠加 {@code translation}，最后写入顶点缓冲。
     */
    @Nullable
    private BakedQuad buildQuad(float[] flat, Triangle source, TextureAtlasSprite sprite,
                                Transformation rotation, boolean rotate) {
        int count = flat.length / 5;
        if (count < 3) {
            return null;
        }
        Vector3f normal = faceNormal(source);
        Direction direction = Direction.getNearest(normal.x(), normal.y(), normal.z());

        for (int i = 1; i + 1 < count; i++) {
            QuadBakingVertexConsumer.Buffered buffer = new QuadBakingVertexConsumer.Buffered();
            buffer.setSprite(sprite);
            buffer.setTintIndex(-1);
            buffer.setShade(shadeQuads);
            buffer.setDirection(direction);
            int[] order = {0, i, i + 1, i + 1};
            for (int corner : order) {
                Vector3f position = new Vector3f(flat[corner * 5], flat[corner * 5 + 1], flat[corner * 5 + 2]);
                if (rotate) {
                    Vector4f transformed = new Vector4f(position, 1.0F);
                    rotation.transformPosition(transformed);
                    position.set(transformed.x(), transformed.y(), transformed.z());
                }
                position.add(translation);
                Vector3f n = new Vector3f(normal);
                if (rotate) {
                    rotation.transformNormal(n);
                }
                buffer.vertex(position.x(), position.y(), position.z());
                buffer.color(1.0F, 1.0F, 1.0F, 1.0F);
                buffer.uv(sprite.getU(flat[corner * 5 + 3] * 16.0F), sprite.getV(flat[corner * 5 + 4] * 16.0F));
                buffer.uv2(0);
                buffer.normal(n.x(), n.y(), n.z());
                buffer.endVertex();
            }
            return buffer.getQuad();
        }
        return null;
    }

    private static Vector3f faceNormal(Triangle triangle) {
        Vector3f a = new Vector3f(triangle.position()[1]).sub(triangle.position()[0]);
        Vector3f b = new Vector3f(triangle.position()[2]).sub(triangle.position()[0]);
        Vector3f normal = a.cross(b);
        if (normal.lengthSquared() < 1.0E-12F) {
            return new Vector3f(0.0F, 1.0F, 0.0F);
        }
        return normal.normalize();
    }

    /** 资源读取与日志的间接层，避免把 Minecraft 内部类散落到解析代码里。 */
    private static final class MinecraftHolder {
        private MinecraftHolder() {
        }

        @Nullable
        static Resource resource(ResourceLocation location) {
            return net.minecraft.client.Minecraft.getInstance().getResourceManager()
                    .getResource(location).orElse(null);
        }
    }
}
