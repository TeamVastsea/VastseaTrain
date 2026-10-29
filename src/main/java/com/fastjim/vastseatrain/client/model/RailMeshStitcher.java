package com.fastjim.vastseatrain.client.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 轨道模型的三角面拼接：把同一材质下共用一条边的三角面合成一整块，再按 UV 接缝切开。
 * <p>
 * 为什么要做：美术导出的 OBJ 把道床、道砟这类平面切成了很多小三角形，而且切分位置在 UV 上并不连续。
 * 逐三角形提交时，共用边两侧各自做透视校正插值，浮点误差在像素级挤出细缝；
 * 在接近水平、与视线夹角很小的道床面上，这些细缝会叠成肉眼可见的三角形空洞。
 * <p>
 * 合并后，内部边两侧共用同一批顶点与同一套插值参数，接缝处的像素<b>完全重合</b>，不会露底。
 * 合并只改变“怎么画”，不改变外形：所有顶点位置与 UV 都来自原文件。
 * <p>
 * 本类是纯计算，不依赖 Minecraft 类，便于用真实 OBJ 离线验证。
 */
final class RailMeshStitcher {
    private RailMeshStitcher() {
    }

    /** UV 比较容差：同一接缝两侧的 UV 在这个范围内视为连续。 */
    private static final float UV_EPSILON = 1.0E-4F;

    /**
     * 拼接结果中的一块：顶点表 + 三角形索引表（三角形下标是顶点下标，绕序与源面一致）。
     * <p>
     * {@link #vertices} 每 5 个 float 是一个顶点：x, y, z, u, v；{@link #indices} 每 3 个 int 是一个三角形。
     */
    record Patch(float[] vertices, int[] indices) {
        int vertexCount() {
            return vertices.length / 5;
        }

        int triangleCount() {
            return indices.length / 3;
        }
    }

    /** 拼接统计，仅用于日志。 */
    record Result(List<Patch> patches, int sourceTriangles, int outputTriangles) {
    }

    /**
     * @param data      扁平三角面数据，每个顶点 5 个 float（x, y, z, u, v），每个三角形 15 个 float
     * @param triangles 三角面数
     */
    static Result stitch(float[] data, int triangles) {
        int vertexCount = triangles * 3;
        // 位置 + UV 完全相同的顶点视为同一个顶点
        Map<String, Integer> unique = new HashMap<>(vertexCount * 2);
        List<float[]> uniqueValues = new ArrayList<>(vertexCount);
        int[] vertexOf = new int[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            float x = data[i * 5];
            float y = data[i * 5 + 1];
            float z = data[i * 5 + 2];
            float u = data[i * 5 + 3];
            float v = data[i * 5 + 4];
            String key = key(x, y, z) + "@" + Math.round(u * 10000.0F) + "," + Math.round(v * 10000.0F);
            Integer index = unique.get(key);
            if (index == null) {
                index = uniqueValues.size();
                unique.put(key, index);
                uniqueValues.add(new float[]{x, y, z, u, v});
            }
            vertexOf[i] = index;
        }

        // 无向边 → 使用它的三角形（同一三角形重复使用同一条边只记一次）
        Map<Long, List<Integer>> edgeUsers = new HashMap<>();
        for (int t = 0; t < triangles; t++) {
            for (int k = 0; k < 3; k++) {
                int a = vertexOf[t * 3 + k];
                int b = vertexOf[t * 3 + (k + 1) % 3];
                edgeUsers.computeIfAbsent(edgeKey(a, b), key -> new ArrayList<>(2)).add(t);
            }
        }

        // 并查集：共用一条几何边就必须在同一个补丁里，否则接缝两侧仍由两份顶点各自插值
        int[] parent = new int[triangles];
        for (int i = 0; i < triangles; i++) {
            parent[i] = i;
        }
        for (Map.Entry<Long, List<Integer>> entry : edgeUsers.entrySet()) {
            List<Integer> users = entry.getValue();
            // 两个端点位置相同、UV 不同：这是 UV 接缝，两侧按贴图不同区域取样，必须分开画
            if (users.size() == 2 && isSeam(data, vertexOf, users.get(0), users.get(1))) {
                continue;
            }
            for (int i = 1; i < users.size(); i++) {
                union(parent, users.get(0), users.get(i));
            }
        }

        List<Patch> patches = new ArrayList<>();
        int outputTriangles = 0;
        boolean[] done = new boolean[triangles];
        for (int t = 0; t < triangles; t++) {
            if (done[t]) {
                continue;
            }
            int root = find(parent, t);
            Map<Integer, Integer> local = new HashMap<>();
            List<float[]> localValues = new ArrayList<>();
            List<Integer> order = new ArrayList<>();
            for (int other = t; other < triangles; other++) {
                if (done[other] || find(parent, other) != root) {
                    continue;
                }
                done[other] = true;
                order.add(other);
                for (int k = 0; k < 3; k++) {
                    int global = vertexOf[other * 3 + k];
                    if (!local.containsKey(global)) {
                        local.put(global, localValues.size());
                        localValues.add(uniqueValues.get(global));
                    }
                }
            }
            float[] vertices = new float[localValues.size() * 5];
            for (int i = 0; i < localValues.size(); i++) {
                System.arraycopy(localValues.get(i), 0, vertices, i * 5, 5);
            }
            int[] indices = new int[order.size() * 3];
            int written = 0;
            for (int other : order) {
                for (int k = 0; k < 3; k++) {
                    indices[written++] = local.get(vertexOf[other * 3 + k]);
                }
            }
            patches.add(new Patch(vertices, indices));
            outputTriangles += order.size();
        }
        return new Result(patches, triangles, outputTriangles);
    }

    /**
     * 两个三角形共用的一条边，在几何上是否属于 UV 接缝（两端 UV 不连续）。
     * <p>
     * 顶点已按“位置 + UV”去重，共用顶点 UV 必然相同；接缝只出现在“位置相同、UV 不同”的两组顶点之间，
     * 所以这里按边的两个端点位置配对，再看两侧 UV。
     */
    private static boolean isSeam(float[] data, int[] vertexOf, int t1, int t2) {
        int shared = 0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (vertexOf[t1 * 3 + i] == vertexOf[t2 * 3 + j]) {
                    shared++;
                }
            }
        }
        if (shared == 2) {
            // 共用的是同一个顶点（UV 相同），不是接缝
            return false;
        }
        for (int i = 0; i < 3; i++) {
            int a1 = t1 * 3 + i;
            int b1 = t1 * 3 + (i + 1) % 3;
            String pa = positionKey(data, a1);
            String pb = positionKey(data, b1);
            for (int j = 0; j < 3; j++) {
                int a2 = t2 * 3 + j;
                int b2 = t2 * 3 + (j + 1) % 3;
                if (pa.equals(positionKey(data, b2)) && pb.equals(positionKey(data, a2))) {
                    return !continuous(data, a1, a2) || !continuous(data, b1, b2);
                }
            }
        }
        return false;
    }

    private static boolean continuous(float[] data, int a, int b) {
        return Math.abs(data[a * 5 + 3] - data[b * 5 + 3]) <= UV_EPSILON
                && Math.abs(data[a * 5 + 4] - data[b * 5 + 4]) <= UV_EPSILON;
    }

    private static String positionKey(float[] data, int vertex) {
        return key(data[vertex * 5], data[vertex * 5 + 1], data[vertex * 5 + 2]);
    }

    private static String key(float x, float y, float z) {
        return Math.round(x * 100000.0F) + "," + Math.round(y * 100000.0F) + "," + Math.round(z * 100000.0F);
    }

    private static long edgeKey(int a, int b) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        return ((long) lo << 32) | (hi & 0xFFFFFFFFL);
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[rb] = ra;
        }
    }
}
