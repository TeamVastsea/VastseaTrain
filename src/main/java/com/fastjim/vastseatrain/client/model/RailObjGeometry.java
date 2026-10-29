package com.fastjim.vastseatrain.client.model;

import com.google.gson.JsonArray;
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

    private final Map<String, Mesh> meshes = new java.util.LinkedHashMap<>();
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
        Vector3f translation = new Vector3f(0.0F, 0.0F, 0.0F);
        if (json.has("transform") && json.get("transform").isJsonObject()) {
            JsonObject transform = json.getAsJsonObject("transform");
            if (transform.has("translation") && transform.get("translation").isJsonArray()) {
                JsonArray array = transform.getAsJsonArray("translation");
                if (array.size() >= 3) {
                    translation = new Vector3f(
                            array.get(0).getAsFloat(),
                            array.get(1).getAsFloat(),
                            array.get(2).getAsFloat());
                }
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
                        if (texture == null) {
                            currentMesh = null;
                        } else {
                            // 同名材质在 OBJ 里会多次出现（美术按对象分段导出），这里复用同一个 Mesh，
                            // 避免拆成多个材质段导致贴图/剔除设置被重复处理
                            currentMesh = meshes.computeIfAbsent(currentMaterial, key -> new Mesh(texture));
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
        // OBJ 面可能是三角形或四边形，也可能是更多顶点。
        // 四边形按 (0,1,2) + (0,2,3) 共用对角线拆分，避免扇形拆分时重复顶点导致面积丢失。
        if (count == 4) {
            addTriangle(mesh, parts, new int[]{1, 2, 3}, positions, texCoords);
            addTriangle(mesh, parts, new int[]{1, 3, 4}, positions, texCoords);
            return;
        }
        for (int i = 1; i + 1 < count; i++) {
            addTriangle(mesh, parts, new int[]{1, i + 1, i + 2}, positions, texCoords);
        }
    }

    /** 按 OBJ 面里的三个顶点序号token生成一个三角面。 */
    private static void addTriangle(Mesh mesh, String[] parts, int[] tokens,
                                    List<Vector3f> positions, List<float[]> texCoords) {
        Vector3f p0 = vertexPosition(parts[tokens[0]], positions);
        Vector3f p1 = vertexPosition(parts[tokens[1]], positions);
        Vector3f p2 = vertexPosition(parts[tokens[2]], positions);
        float[] uv0 = vertexUv(parts[tokens[0]], texCoords);
        float[] uv1 = vertexUv(parts[tokens[1]], texCoords);
        float[] uv2 = vertexUv(parts[tokens[2]], texCoords);
        if (p0 == null || p1 == null || p2 == null || uv0 == null || uv1 == null || uv2 == null) {
            return;
        }
        mesh.triangles.add(new Triangle(
                new Vector3f[]{p0, p1, p2},
                new float[]{uv0[0], uv1[0], uv2[0]},
                new float[]{uv0[1], uv1[1], uv2[1]}));
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
        int stitched = 0;
        for (Mesh mesh : meshes.values()) {
            TextureAtlasSprite sprite = spriteGetter.apply(context.getMaterial(mesh.texture));
            // 先把共用边的三角面拼成整块：逐三角形提交时，共边两侧各自插值，浮点误差会挤出一像素细缝，
            // 在几乎与视线平行的道床面上叠成三角形空洞。
            List<Triangle> triangles = mesh.triangles;
            float[] data = new float[triangles.size() * 15];
            for (int t = 0; t < triangles.size(); t++) {
                Triangle triangle = triangles.get(t);
                for (int i = 0; i < 3; i++) {
                    int base = (t * 3 + i) * 5;
                    data[base] = triangle.position()[i].x();
                    data[base + 1] = triangle.position()[i].y();
                    data[base + 2] = triangle.position()[i].z();
                    data[base + 3] = triangle.u()[i];
                    data[base + 4] = triangle.v()[i];
                }
            }
            RailMeshStitcher.Result result = RailMeshStitcher.stitch(data, triangles.size());
            stitched += result.patches().size();
            for (RailMeshStitcher.Patch patch : result.patches()) {
                for (Triangle piece : tilePatch(patch)) {
                    for (BakedQuad quad : buildQuads(piece, sprite, rotation, rotate)) {
                        if (quad != null) {
                            builder.addUnculledFace(quad);
                            emitted++;
                        }
                    }
                }
            }
        }
        RailModelLog.baked(meshes.size(), stitched, emitted, translation);
        return builder.build();
    }

    /**
     * 把一个拼接好的补丁展开成三角面列表：先按 UV 整数格切块，再逐块扇形三角化。
     * <p>
     * 补丁内部的三角形共用顶点下标，展开后仍然是“相邻三角形共用同一对顶点”的网格，
     * 相邻面不会各自算出一份略有差异的交点。
     */
    private List<Triangle> tilePatch(RailMeshStitcher.Patch patch) {
        float[] vertices = patch.vertices();
        int[] indices = patch.indices();
        List<Triangle> result = new ArrayList<>(indices.length / 3);
        for (int i = 0; i < indices.length; i += 3) {
            int a = indices[i];
            int b = indices[i + 1];
            int c = indices[i + 2];
            Triangle triangle = new Triangle(
                    new Vector3f[]{
                            new Vector3f(vertices[a * 5], vertices[a * 5 + 1], vertices[a * 5 + 2]),
                            new Vector3f(vertices[b * 5], vertices[b * 5 + 1], vertices[b * 5 + 2]),
                            new Vector3f(vertices[c * 5], vertices[c * 5 + 1], vertices[c * 5 + 2])},
                    new float[]{vertices[a * 5 + 3], vertices[b * 5 + 3], vertices[c * 5 + 3]},
                    new float[]{vertices[a * 5 + 4], vertices[b * 5 + 4], vertices[c * 5 + 4]});
            for (float[] piece : tileUv(triangle)) {
                int count = piece.length / 5;
                if (count < 3) {
                    continue;
                }
                Vector3f[] position = new Vector3f[count];
                float[] u = new float[count];
                float[] v = new float[count];
                for (int k = 0; k < count; k++) {
                    position[k] = new Vector3f(piece[k * 5], piece[k * 5 + 1], piece[k * 5 + 2]);
                    u[k] = piece[k * 5 + 3];
                    v[k] = piece[k * 5 + 4];
                }
                for (int k = 1; k + 1 < count; k++) {
                    result.add(new Triangle(
                            new Vector3f[]{position[0], position[k], position[k + 1]},
                            new float[]{u[0], u[k], u[k + 1]},
                            new float[]{v[0], v[k], v[k + 1]}));
                }
            }
        }
        return result;
    }

    /**
     * 把一个三角面按 UV 整数格切块，每块返回若干个顶点：位置(3) + UV(2)。
     * <p>
     * 三角面从模型坐标到 UV 的映射是仿射的，所以“在 UV 空间裁剪三角形”等价于“在模型上切分”，
     * 切块后形状与原来完全一致，只是每块的 UV 都归一化到 {@code 0..1}。
     * <p>
     * 切分边界必须落在<b>贴图整数边界</b>上（也就是贴图重复的接缝处），否则相邻块各采到半张图、
     * 接缝处出现错位，视觉上就是锯齿状破面。因此网格按 {@code ceil} 取整后再均分。
     */
    private List<float[]> tileUv(Triangle triangle) {
        float[] u = triangle.u();
        float[] v = triangle.v();
        float minU = Math.min(u[0], Math.min(u[1], u[2]));
        float maxU = Math.max(u[0], Math.max(u[1], u[2]));
        float minV = Math.min(v[0], Math.min(v[1], v[2]));
        float maxV = Math.max(v[0], Math.max(v[1], v[2]));

        // 网格必须对齐到贴图整数边界：贴图重复的接缝在 u=整数、v=整数 处，
        // 只有切在这条线上，相邻块才各采一整张贴图、不会错位。
        int startU = (int) Math.floor(minU);
        int startV = (int) Math.floor(minV);
        int endU = (int) Math.ceil(maxU);
        int endV = (int) Math.ceil(maxV);
        int tilesU = Math.min(MAX_TILES, endU - startU);
        int tilesV = Math.min(MAX_TILES, endV - startV);
        if (maxU > startU + MAX_TILES || maxV > startV + MAX_TILES) {
            // 被 MAX_TILES 截断时网格不再对齐整数边界，相邻块会各采半张贴图。
            // 这种情况属于数据异常，宁可记录出来也不要静默画出错位的面。
            RailModelLog.warn("UV 跨度超过 {} 格，已被截断：u {}-{}, v {}-{}", MAX_TILES, minU, maxU, minV, maxV);
        }

        if (tilesU <= 1 && tilesV <= 1) {
            // 单格：美术 UV 本身就在一张贴图内，原样保留（归一化会改变贴图比例）
            List<float[]> single = new ArrayList<>(1);
            float[] flat = new float[15];
            for (int i = 0; i < 3; i++) {
                flat[i * 5] = triangle.position()[i].x();
                flat[i * 5 + 1] = triangle.position()[i].y();
                flat[i * 5 + 2] = triangle.position()[i].z();
                flat[i * 5 + 3] = u[i] - startU;
                flat[i * 5 + 4] = v[i] - startV;
            }
            single.add(flat);
            return single;
        }

        List<float[]> result = new ArrayList<>(tilesU * tilesV);
        // 顶点在 UV 空间里的多边形
        List<float[]> polygon = new ArrayList<>(3);
        polygon.add(new float[]{u[0], v[0]});
        polygon.add(new float[]{u[1], v[1]});
        polygon.add(new float[]{u[2], v[2]});
        float polygonArea = uvArea(polygon);

        for (int iu = 0; iu < tilesU; iu++) {
            for (int iv = 0; iv < tilesV; iv++) {
                float u0 = startU + iu;
                float v0 = startV + iv;
                float u1 = u0 + 1.0F;
                float v1 = v0 + 1.0F;
                List<float[]> clipped = clip(polygon, u0, v0, u1, v1);
                if (clipped.size() < 3) {
                    continue;
                }
                // Sutherland–Hodgman 可能改变顶点绕序，这里强制与原始三角形在 3D 空间中的绕序一致。
                // 注意必须用 3D 判断：UV 空间的绕序与 3D 绕序可以是相反的（UV 被镜像时），
                // 若按 UV 判断并翻转，块的绕序会与法线相反，被背面剔除后表现为破面。
                if (isReversed(clipped, triangle)) {
                    java.util.Collections.reverse(clipped);
                }
                float[] flat = new float[clipped.size() * 5];
                for (int i = 0; i < clipped.size(); i++) {
                    float[] uv = clipped.get(i);
                    float[] pos = positionAt(triangle, uv[0], uv[1]);
                    flat[i * 5] = pos[0];
                    flat[i * 5 + 1] = pos[1];
                    flat[i * 5 + 2] = pos[2];
                    flat[i * 5 + 3] = clamp01(uv[0] - u0);
                    flat[i * 5 + 4] = clamp01(uv[1] - v0);
                }
                result.add(flat);
            }
        }
        if (result.isEmpty()) {
            // 极端情况下（UV 退化）整面作为一个块
            List<float[]> single = new ArrayList<>(1);
            float[] flat = new float[15];
            float spanU = Math.max(maxU - minU, EPSILON);
            float spanV = Math.max(maxV - minV, EPSILON);
            for (int i = 0; i < 3; i++) {
                flat[i * 5] = triangle.position()[i].x();
                flat[i * 5 + 1] = triangle.position()[i].y();
                flat[i * 5 + 2] = triangle.position()[i].z();
                flat[i * 5 + 3] = clamp01((u[i] - minU) / spanU);
                flat[i * 5 + 4] = clamp01((v[i] - minV) / spanV);
            }
            single.add(flat);
            return single;
        }
        return result;
    }

    /** 多边形在 UV 平面的有向面积；正负号代表绕序。 */
    private static float uvArea(List<float[]> polygon) {
        float sum = 0.0F;
        int size = polygon.size();
        for (int i = 0; i < size; i++) {
            float[] a = polygon.get(i);
            float[] b = polygon.get((i + 1) % size);
            sum += a[0] * b[1] - b[0] * a[1];
        }
        return sum * 0.5F;
    }

    /**
     * 判断 UV 空间多边形反解到 3D 后，绕序是否与源三角形相反。
     * <p>
     * 必须用 3D 法线比较，不能看 UV 有向面积：UV 被镜像时，UV 空间的“逆时针”在模型上其实是顺时针。
     */
    private static boolean isReversed(List<float[]> clipped, Triangle triangle) {
        if (clipped.size() < 3) {
            return false;
        }
        float[] a = positionAt(triangle, clipped.get(0)[0], clipped.get(0)[1]);
        float[] b = positionAt(triangle, clipped.get(1)[0], clipped.get(1)[1]);
        float[] c = positionAt(triangle, clipped.get(2)[0], clipped.get(2)[1]);
        double[] e1 = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
        double[] e2 = {c[0] - a[0], c[1] - a[1], c[2] - a[2]};
        double[] normal = {
                e1[1] * e2[2] - e1[2] * e2[1],
                e1[2] * e2[0] - e1[0] * e2[2],
                e1[0] * e2[1] - e1[1] * e2[0]};
        Vector3f source = faceNormal(triangle);
        double dot = normal[0] * source.x() + normal[1] * source.y() + normal[2] * source.z();
        return dot < 0.0D;
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
        Vector3f p0 = triangle.position()[0];
        Vector3f p1 = triangle.position()[1];
        Vector3f p2 = triangle.position()[2];
        if (Math.abs(denom) < EPSILON) {
            // UV 退化（例如整块 UV 只用一个 v 值）：沿 UV 包围盒的长轴在三个顶点间插值，
            // 避免切块全部塌到同一个点上（那会表现为破面）
            float spanU = Math.max(uu[0], Math.max(uu[1], uu[2])) - Math.min(uu[0], Math.min(uu[1], uu[2]));
            float spanV = Math.max(vv[0], Math.max(vv[1], vv[2])) - Math.min(vv[0], Math.min(vv[1], vv[2]));
            float t = spanU >= spanV
                    ? (u - Math.min(uu[0], Math.min(uu[1], uu[2]))) / Math.max(spanU, EPSILON)
                    : (v - Math.min(vv[0], Math.min(vv[1], vv[2]))) / Math.max(spanV, EPSILON);
            t = clamp01(t);
            return new float[]{
                    p0.x() + (p1.x() - p0.x()) * t + (p2.x() - p0.x()) * t * 0.0F,
                    p0.y() + (p1.y() - p0.y()) * t + (p2.y() - p0.y()) * t * 0.0F,
                    p0.z() + (p1.z() - p0.z()) * t + (p2.z() - p0.z()) * t * 0.0F};
        }
        float a = ((vv[1] - vv[2]) * (u - uu[2]) + (uu[2] - uu[1]) * (v - vv[2])) / denom;
        float b = ((vv[2] - vv[0]) * (u - uu[2]) + (uu[0] - uu[2]) * (v - vv[2])) / denom;
        float c = 1.0F - a - b;
        return new float[]{
                p0.x() * a + p1.x() * b + p2.x() * c,
                p0.y() * a + p1.y() * b + p2.y() * c,
                p0.z() * a + p1.z() * b + p2.z() * c};
    }

    /**
     * 把一个三角面转成 {@link BakedQuad}（第四个角与第三个角重合，即三角形）。
     * 位置先按朝向旋转，再叠加 {@code translation}，最后写入顶点缓冲。
     */
    @Nullable
    private List<BakedQuad> buildQuads(Triangle source, TextureAtlasSprite sprite,
                                       Transformation rotation, boolean rotate) {
        Vector3f normal = faceNormal(source);
        // 面的朝向由几何绕序决定。三角形统一按“正面”上报，不要用 getNearest 得到的斜向方向：
        // 渲染器会按这个方向做朝向判断，斜向会被当成背对镜头而整面不画。
        Direction forward = Direction.getNearest(normal.x(), normal.y(), normal.z());
        if (forward == null) {
            forward = Direction.UP;
        }
        Vector3f rotatedNormal = new Vector3f(normal);
        if (rotate) {
            rotation.transformNormal(rotatedNormal);
        }
        BakedQuad quad = makeQuad(source, sprite, forward, rotatedNormal, rotation, rotate);
        return quad == null ? List.of() : List.of(quad);
    }

    /** 生成一个 {@link BakedQuad}：三个顶点 + 与第三个顶点重合的第四角。 */
    @Nullable
    private BakedQuad makeQuad(Triangle source, TextureAtlasSprite sprite,
                               Direction forward, Vector3f rotatedNormal,
                               Transformation rotation, boolean rotate) {
        Vector3f[] position = source.position();
        float[] u = source.u();
        float[] v = source.v();
        // 面积近似为零的退化面直接丢弃：画出来只会闪烁或产生破面
        Vector3f cross = new Vector3f(position[1]).sub(position[0])
                .cross(new Vector3f(position[2]).sub(position[0]));
        if (cross.lengthSquared() < 1.0E-12F) {
            return null;
        }

        QuadBakingVertexConsumer.Buffered buffer = new QuadBakingVertexConsumer.Buffered();
        buffer.setSprite(sprite);
        buffer.setTintIndex(-1);
        buffer.setShade(shadeQuads);
        // 三角形补齐成的四边形，第四角的法线依赖前三个角，可能被判为不可见。
        // 这里显式传入 forward，保证所有面照常渲染。
        buffer.setDirection(forward);
        // 关闭逐顶点 AO：自定义几何的道床面与地形方块共面，AO 会把面亮度改为依赖周围方块，
        // 结果就是面上出现三角形亮斑或暗洞。轨道整体亮度交给方块光照与 shade 即可。
        buffer.setHasAmbientOcclusion(false);
        for (int corner = 0; corner < 4; corner++) {
            int index = Math.min(corner, 2);
            Vector3f pos = new Vector3f(position[index]);
            if (rotate) {
                Vector4f transformed = new Vector4f(pos, 1.0F);
                rotation.transformPosition(transformed);
                pos.set(transformed.x(), transformed.y(), transformed.z());
            }
            pos.add(translation);
            buffer.vertex(pos.x(), pos.y(), pos.z());
            buffer.color(1.0F, 1.0F, 1.0F, 1.0F);
            buffer.uv(sprite.getU(u[index] * 16.0F), sprite.getV(v[index] * 16.0F));
            buffer.uv2(0);
            buffer.normal(rotatedNormal.x(), rotatedNormal.y(), rotatedNormal.z());
            buffer.endVertex();
        }
        return buffer.getQuad();
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
