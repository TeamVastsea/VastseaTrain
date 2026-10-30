package com.fastjim.vastseatrain.client.render;

import com.fastjim.vastseatrain.VastseaTrain;
import com.fastjim.vastseatrain.client.render.RailPreviewRenderer.Face;
import com.fastjim.vastseatrain.client.render.RailPreviewRenderer.Mode;
import com.fastjim.vastseatrain.track.RailStyle;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 界面 3D 预览用的模型数据。
 * <p>
 * 为什么单独解析而不是复用烘焙好的方块模型：已烘焙模型的顶点里已经没有 UV、文件信息也被丢掉了，
 * 而界面不需要离屏渲染目标，只需要顶点 + 贴图坐标，直接读美术文件最简单、也最好调试。
 * <p>
 * UV 处理与游戏内加载器保持一致：材质贴图引用解析成图集贴图，三角面按 UV 整数格切块，
 * 每块用完整的 {@code 0..1} 贴图坐标——否则界面里会像 {@code forge:obj} 那样出现花屏。
 */
public final class RailPreviewGeometry {
    /** 贴图在 UI 里的显示尺寸（像素）。 */
    public static final int SPRITE_SIZE = 32;
    /** 单个三角面最多切成多少格。 */
    private static final int MAX_TILES = 16;

    /**
     * 一个模型。
     *
     * @param faces     三角面（OBJ 局部坐标，单位：格）
     * @param sprites   六朝向各自的贴图
     * @param minX      包围盒（用于自动调整相机与大小）
     */
    public record Geometry(List<Face> faces, Map<Direction6, TextureAtlasSprite> sprites,
                           float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        /** 模型在平面上的最大跨度（格），决定预览的大小。 */
        public float planLength() {
            return Math.max(maxX - minX, maxZ - minZ);
        }

        /** 模型在长度轴上的中心（相机对准这里）。 */
        public float centerX() {
            return (minX + maxX) * 0.5F;
        }

        public float centerZ() {
            return (minZ + maxZ) * 0.5F;
        }
    }

    /** 面朝向：用于给每个方向挑一张代表贴图（贴图与朝向无关，挑最常出现的即可）。 */
    public enum Direction6 {
        UP, DOWN, NORTH, SOUTH, EAST, WEST
    }

    private static final Map<RailStyle, Geometry> CACHE = new EnumMap<>(RailStyle.class);
    private static final Map<ResourceLocation, TextureAtlasSprite> SPRITE_CACHE = new HashMap<>();

    private RailPreviewGeometry() {
    }

    /** 取某种外观的模型（带缓存；资源重载后由 {@link #invalidate()} 清空）。 */
    public static Geometry of(RailStyle style) {
        Geometry cached = CACHE.get(style);
        if (cached != null) {
            return cached;
        }
        Geometry geometry;
        try {
            geometry = load(style);
        } catch (Exception e) {
            VastseaTrain.LOGGER.error("无法读取轨道模型 {} 的预览数据", style.getSerializedName(), e);
            geometry = new Geometry(List.of(), new EnumMap<>(Direction6.class),
                    -1.0F, -1.0F, -1.0F, 1.0F, 0.5F, 1.0F);
        }
        CACHE.put(style, geometry);
        return geometry;
    }

    /** 资源重载（F3+T）后贴图对象会失效，重新解析。 */
    public static void invalidate() {
        CACHE.clear();
        SPRITE_CACHE.clear();
    }

    // ------------------------------------------------------------------ 读取

    private static Geometry load(RailStyle style) {
        ResourceLocation modelId = ResourceLocation.fromNamespaceAndPath(VastseaTrain.MOD_ID,
                "models/block/" + style.straightModel() + ".json");
        JsonObject json = readJson(modelId);

        boolean flipV = json.has("flip_v") && json.get("flip_v").getAsBoolean();
        // 预览沿用 OBJ 原始局部坐标，不叠加方块放置用的根模型 translation。
        float originX = 0.0F;
        float originY = 0.0F;
        float originZ = 0.0F;

        Map<String, String> textureRefs = new LinkedHashMap<>();
        if (json.has("textures") && json.get("textures").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("textures").entrySet()) {
                textureRefs.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        String modelPath = json.get("model").getAsString();
        String mtlPath = json.has("mtl_override") ? json.get("mtl_override").getAsString() : null;

        Map<String, String> materialTextures = mtlPath == null
                ? Map.of() : readMaterialLibrary(ResourceLocation.parse(mtlPath));

        List<Face> faces = new ArrayList<>();
        Map<Direction6, Map<TextureAtlasSprite, Integer>> spriteCounts = new EnumMap<>(Direction6.class);
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        List<Vector3f> positions = new ArrayList<>();
        List<float[]> texCoords = new ArrayList<>();
        String material = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource(ResourceLocation.parse(modelPath)).open(), StandardCharsets.US_ASCII))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+");
                switch (parts[0]) {
                    case "v" -> positions.add(new Vector3f(Float.parseFloat(parts[1]), Float.parseFloat(parts[2]),
                            Float.parseFloat(parts[3])));
                    case "vt" -> {
                        float u = Float.parseFloat(parts[1]);
                        float v = parts.length > 2 ? Float.parseFloat(parts[2]) : 0.0F;
                        texCoords.add(new float[]{u, flipV ? 1.0F - v : v});
                    }
                    case "usemtl" -> material = trimmed.substring("usemtl".length()).trim();
                    case "f" -> {
                        String ref = material == null ? null : materialTextures.get(material);
                        if (ref == null) {
                            continue;
                        }
                        TextureAtlasSprite sprite = sprite(ref, textureRefs);
                        for (Triangle triangle : triangulate(parts, positions, texCoords)) {
                            for (Triangle piece : tile(triangle)) {
                                Face face = toFace(piece, sprite, originX, originY, originZ, 0xFFFFFF);
                                if (face == null) {
                                    continue;
                                }
                                faces.add(face);
                                for (int i = 0; i < 3; i++) {
                                    minX = Math.min(minX, face.x()[i]);
                                    maxX = Math.max(maxX, face.x()[i]);
                                    minY = Math.min(minY, face.y()[i]);
                                    maxY = Math.max(maxY, face.y()[i]);
                                    minZ = Math.min(minZ, face.z()[i]);
                                    maxZ = Math.max(maxZ, face.z()[i]);
                                }
                                spriteCounts.computeIfAbsent(directionOf(face), key -> new HashMap<>())
                                        .merge(sprite, 1, Integer::sum);
                            }
                        }
                    }
                    default -> {
                        // o / g / s / mtllib / vn 等无需处理
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("解析预览模型失败：" + modelPath, e);
        }

        Map<Direction6, TextureAtlasSprite> sprites = new EnumMap<>(Direction6.class);
        for (Direction6 side : Direction6.values()) {
            Map<TextureAtlasSprite, Integer> counts = spriteCounts.get(side);
            if (counts == null || counts.isEmpty()) {
                continue;
            }
            // 每个方向挑出现次数最多的那张贴图，界面里画出的是该方向最典型的样子
            TextureAtlasSprite best = null;
            int bestCount = -1;
            for (Map.Entry<TextureAtlasSprite, Integer> entry : counts.entrySet()) {
                if (entry.getValue() > bestCount) {
                    best = entry.getKey();
                    bestCount = entry.getValue();
                }
            }
            sprites.put(side, best);
        }
        if (faces.isEmpty()) {
            minX = minY = minZ = -1.0F;
            maxX = 1.0F;
            maxY = 0.5F;
            maxZ = 1.0F;
        }
        return new Geometry(List.copyOf(faces), sprites, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static TextureAtlasSprite sprite(String ref, Map<String, String> textureRefs) {
        String path = ref.startsWith("#") ? textureRefs.get(ref.substring(1)) : ref;
        if (path == null) {
            path = "vastseatrain:block/rail/gravel";
        }
        ResourceLocation id = ResourceLocation.parse(path);
        // 图集按 sprite ID 查找（block/rail/name），不是磁盘文件路径 textures/...png。
        return SPRITE_CACHE.computeIfAbsent(id, key -> Minecraft.getInstance()
                .getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
                .apply(key));
    }

    private static Direction6 directionOf(Face face) {
        float nx = 0.0F;
        float ny = 0.0F;
        float nz = 0.0F;
        float ax = face.x()[1] - face.x()[0];
        float ay = face.y()[1] - face.y()[0];
        float az = face.z()[1] - face.z()[0];
        float bx = face.x()[2] - face.x()[0];
        float by = face.y()[2] - face.y()[0];
        float bz = face.z()[2] - face.z()[0];
        nx = ay * bz - az * by;
        ny = az * bx - ax * bz;
        nz = ax * by - ay * bx;
        float absX = Math.abs(nx);
        float absY = Math.abs(ny);
        float absZ = Math.abs(nz);
        if (absY >= absX && absY >= absZ) {
            return ny >= 0 ? Direction6.UP : Direction6.DOWN;
        }
        if (absX >= absZ) {
            return nx >= 0 ? Direction6.EAST : Direction6.WEST;
        }
        return nz >= 0 ? Direction6.SOUTH : Direction6.NORTH;
    }

    // ------------------------------------------------------------------ 三角面处理

    private record Triangle(Vector3f[] position, float[] u, float[] v) {
    }

    private static List<Triangle> triangulate(String[] parts, List<Vector3f> positions, List<float[]> texCoords) {
        int count = parts.length - 1;
        List<Triangle> result = new ArrayList<>(2);
        if (count < 3) {
            return result;
        }
        if (count == 4) {
            add(result, parts, new int[]{1, 2, 3}, positions, texCoords);
            add(result, parts, new int[]{1, 3, 4}, positions, texCoords);
            return result;
        }
        for (int i = 1; i + 1 < count; i++) {
            add(result, parts, new int[]{1, i + 1, i + 2}, positions, texCoords);
        }
        return result;
    }

    private static void add(List<Triangle> out, String[] parts, int[] tokens,
                            List<Vector3f> positions, List<float[]> texCoords) {
        Vector3f[] p = new Vector3f[3];
        float[] u = new float[3];
        float[] v = new float[3];
        for (int i = 0; i < 3; i++) {
            String[] ref = parts[tokens[i]].split("/");
            int pi = indexOf(ref[0], positions.size());
            int ti = ref.length > 1 && !ref[1].isEmpty() ? indexOf(ref[1], texCoords.size()) : -1;
            if (pi < 0 || ti < 0) {
                return;
            }
            p[i] = positions.get(pi);
            u[i] = texCoords.get(ti)[0];
            v[i] = texCoords.get(ti)[1];
        }
        out.add(new Triangle(p, u, v));
    }

    private static int indexOf(String raw, int size) {
        int index = Integer.parseInt(raw.trim());
        return index < 0 ? size + index : index - 1;
    }

    /** 按 UV 整数格切块（与游戏内加载器同一套算法，只是这里不需要处理极端截断）。 */
    private static List<Triangle> tile(Triangle triangle) {
        float[] u = triangle.u();
        float[] v = triangle.v();
        float minU = Math.min(u[0], Math.min(u[1], u[2]));
        float maxU = Math.max(u[0], Math.max(u[1], u[2]));
        float minV = Math.min(v[0], Math.min(v[1], v[2]));
        float maxV = Math.max(v[0], Math.max(v[1], v[2]));
        int startU = (int) Math.floor(minU);
        int startV = (int) Math.floor(minV);
        int tilesU = Math.min(MAX_TILES, (int) Math.ceil(maxU) - startU);
        int tilesV = Math.min(MAX_TILES, (int) Math.ceil(maxV) - startV);
        List<Triangle> result = new ArrayList<>();
        if (tilesU <= 1 && tilesV <= 1) {
            result.add(new Triangle(triangle.position(),
                    new float[]{u[0] - startU, u[1] - startU, u[2] - startU},
                    new float[]{v[0] - startV, v[1] - startV, v[2] - startV}));
            return result;
        }
        List<float[]> polygon = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            polygon.add(new float[]{u[i], v[i]});
        }
        for (int iu = 0; iu < tilesU; iu++) {
            for (int iv = 0; iv < tilesV; iv++) {
                float u0 = startU + iu;
                float v0 = startV + iv;
                List<float[]> clipped = clip(polygon, u0, v0, u0 + 1.0F, v0 + 1.0F);
                if (clipped.size() < 3) {
                    continue;
                }
                if (isReversed(clipped, triangle)) {
                    java.util.Collections.reverse(clipped);
                }
                for (int k = 1; k + 1 < clipped.size(); k++) {
                    float[] a = clipped.get(0);
                    float[] b = clipped.get(k);
                    float[] c = clipped.get(k + 1);
                    result.add(new Triangle(
                            new Vector3f[]{positionAt(triangle, a[0], a[1]), positionAt(triangle, b[0], b[1]),
                                    positionAt(triangle, c[0], c[1])},
                            new float[]{a[0] - u0, b[0] - u0, c[0] - u0},
                            new float[]{a[1] - v0, b[1] - v0, c[1] - v0}));
                }
            }
        }
        return result;
    }

    private static List<float[]> clip(List<float[]> polygon, float u0, float v0, float u1, float v1) {
        List<float[]> out = polygon;
        out = clipAxis(out, 0, u0, true);
        out = clipAxis(out, 0, u1, false);
        out = clipAxis(out, 1, v0, true);
        out = clipAxis(out, 1, v1, false);
        return out;
    }

    private static List<float[]> clipAxis(List<float[]> polygon, int axis, float value, boolean keepGreater) {
        List<float[]> out = new ArrayList<>(polygon.size() + 1);
        for (int i = 0; i < polygon.size(); i++) {
            float[] a = polygon.get(i);
            float[] b = polygon.get((i + 1) % polygon.size());
            boolean aIn = keepGreater ? a[axis] >= value : a[axis] <= value;
            boolean bIn = keepGreater ? b[axis] >= value : b[axis] <= value;
            if (aIn) {
                out.add(a);
            }
            if (aIn != bIn) {
                float t = (value - a[axis]) / (b[axis] - a[axis]);
                float[] point = new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
                point[axis] = value;
                out.add(point);
            }
        }
        return out;
    }

    private static boolean isReversed(List<float[]> clipped, Triangle triangle) {
        // UV 空间绕序与 3D 绕序可能相反（贴图镜像），必须用 3D 绕序判断
        Vector3f a = positionAt(triangle, clipped.get(0)[0], clipped.get(0)[1]);
        Vector3f b = positionAt(triangle, clipped.get(1)[0], clipped.get(1)[1]);
        Vector3f c = positionAt(triangle, clipped.get(2)[0], clipped.get(2)[1]);
        float cx = (b.y - a.y) * (c.z - a.z) - (b.z - a.z) * (c.y - a.y);
        float cy = (b.z - a.z) * (c.x - a.x) - (b.x - a.x) * (c.z - a.z);
        float cz = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
        Vector3f[] p = triangle.position();
        float tx = (p[1].y - p[0].y) * (p[2].z - p[0].z) - (p[1].z - p[0].z) * (p[2].y - p[0].y);
        float ty = (p[1].z - p[0].z) * (p[2].x - p[0].x) - (p[1].x - p[0].x) * (p[2].z - p[0].z);
        float tz = (p[1].x - p[0].x) * (p[2].y - p[0].y) - (p[1].y - p[0].y) * (p[2].x - p[0].x);
        return cx * tx + cy * ty + cz * tz < 0.0F;
    }

    private static Vector3f positionAt(Triangle triangle, float u, float v) {
        Vector3f[] p = triangle.position();
        float[] tu = triangle.u();
        float[] tv = triangle.v();
        float d = (tu[1] - tu[0]) * (tv[2] - tv[0]) - (tu[2] - tu[0]) * (tv[1] - tv[0]);
        if (Math.abs(d) < 1.0E-9F) {
            return new Vector3f(p[0]);
        }
        float a = ((u - tu[0]) * (tv[2] - tv[0]) - (tu[2] - tu[0]) * (v - tv[0])) / d;
        float b = ((tu[1] - tu[0]) * (v - tv[0]) - (u - tu[0]) * (tv[1] - tv[0])) / d;
        return new Vector3f(
                p[0].x + a * (p[1].x - p[0].x) + b * (p[2].x - p[0].x),
                p[0].y + a * (p[1].y - p[0].y) + b * (p[2].y - p[0].y),
                p[0].z + a * (p[1].z - p[0].z) + b * (p[2].z - p[0].z));
    }

    private static Face toFace(Triangle triangle, TextureAtlasSprite sprite,
                               float originX, float originY, float originZ, int tint) {
        return new Face(
                new float[]{triangle.position()[0].x - originX,
                        triangle.position()[1].x - originX,
                        triangle.position()[2].x - originX},
                new float[]{triangle.position()[0].y - originY,
                        triangle.position()[1].y - originY,
                        triangle.position()[2].y - originY},
                new float[]{triangle.position()[0].z - originZ,
                        triangle.position()[1].z - originZ,
                        triangle.position()[2].z - originZ},
                new float[]{spriteU(sprite, triangle.u()[0]), spriteU(sprite, triangle.u()[1]),
                        spriteU(sprite, triangle.u()[2])},
                new float[]{spriteV(sprite, triangle.v()[0]), spriteV(sprite, triangle.v()[1]),
                        spriteV(sprite, triangle.v()[2])},
                Mode.TEXTURED, tint);
    }

    /** 0..1 的局部 UV → 图集里的实际 UV（方块内 1 格对应 16 像素）。 */
    private static float spriteU(TextureAtlasSprite sprite, float local) {
        return sprite.getU(clamp01(local) * 16.0F);
    }

    private static float spriteV(TextureAtlasSprite sprite, float local) {
        return sprite.getV(clamp01(local) * 16.0F);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    // ------------------------------------------------------------------ 资源

    private static JsonObject readJson(ResourceLocation id) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource(id).open(), StandardCharsets.UTF_8))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("找不到模型 " + id, e);
        }
    }

    private static Map<String, String> readMaterialLibrary(ResourceLocation id) {
        Map<String, String> result = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource(id).open(), StandardCharsets.US_ASCII))) {
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
                    result.put(current, parts[parts.length - 1]);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("找不到材质库 " + id, e);
        }
        return result;
    }

    private static Resource resource(ResourceLocation id) {
        return Minecraft.getInstance().getResourceManager().getResource(id)
                .orElseThrow(() -> new IllegalStateException("找不到资源 " + id));
    }
}
