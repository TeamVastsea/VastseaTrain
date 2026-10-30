package com.fastjim.vastseatrain.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 界面里的 3D 轨道预览（标准 3D 浏览：左键拖拽旋转、滚轮缩放、右键拖拽平移）。
 * <p>
 * 做法：把每个三角面投影成屏幕坐标，再按屏幕坐标直接画（GPU 只做 2x2 线性映射），
 * 因此不需要离屏渲染目标，也不用关心 OpenGL 版本；面和方块都用同一套 {@link Face} 结构。
 * <p>
 * 面数据统一在“轨道局部坐标”里：长度轴沿 X、宽度轴沿 Z、方块上表面为 {@code y = 0}、单位是格。
 */
public final class RailPreviewRenderer {
    /** 面的渲染方式：贴图，或纯色（草方块等）。 */
    public enum Mode {
        TEXTURED,
        FLAT
    }

    /** 一个待绘制的三角面（三个顶点）。 */
    public record Face(float[] x, float[] y, float[] z, float[] u, float[] v, Mode mode, int tint) {
        /** 三个顶点都在范围内时返回 true。 */
        boolean visible() {
            return x.length >= 3 && y.length >= 3 && z.length >= 3;
        }
    }

    /** 透视：竖直视场角（度）。 */
    private static final double FOV = 34.0D;
    /** 默认相机距离（格）。 */
    public static final double BASE_DISTANCE = 3.2D;
    /** 实际使用的相机距离：由 {@link Camera} 的缩放决定，{@link #BASE_DISTANCE} × zoom。 */
    private static final double DISTANCE = BASE_DISTANCE;
    /** 光照方向（指向光源），与游戏内日光观感接近。 */
    private static final Vec3 LIGHT = new Vec3(0.35D, 0.85D, 0.40D).normalize();

    /** 预览视口内的一个面及其屏幕坐标、深度与中心点（每帧现算，不缓存）。 */
    private record Projected(Face face, double[] sx, double[] sy, double depth,
                             double cx, double cy, double cz) {
    }

    private RailPreviewRenderer() {
    }

    // ------------------------------------------------------------------ 相机

    /** 一次预览的相机状态：视口矩形 + 旋转 + 缩放 + 平移。 */
    public static final class Camera {
        public final int x0;
        public final int y0;
        public final int x1;
        public final int y1;
        /** 水平旋转（度），绕竖直轴。 */
        public double yaw = 35.0D;
        /** 俯仰（度），正值为从上方俯视。 */
        public double pitch = 26.0D;
        /** 缩放倍数，1 为默认距离。 */
        public double zoom = 1.0D;
        /** 相机平面内的偏移（格）：正值分别向屏幕右侧、上方移动模型。 */
        public double panU;
        public double panV;
        /** 观察目标（格）。 */
        public Vec3 target = Vec3.ZERO;

        public Camera(int x0, int y0, int x1, int y1) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }

        public boolean inside(double mouseX, double mouseY) {
            return mouseX >= x0 && mouseX <= x1 && mouseY >= y0 && mouseY <= y1;
        }

        /** 左键拖拽：旋转。 */
        public void dragRotate(double dx, double dy) {
            yaw += dx * 0.6D;
            pitch = Mth.clamp(pitch - dy * 0.5D, -85.0D, 85.0D);
        }

        /** 滚轮：缩放。 */
        public void dragZoom(double scroll) {
            zoom = Mth.clamp(zoom * Math.pow(0.85D, scroll), 0.25D, 6.0D);
        }

        /** 右键拖拽：在投影平面内移动模型，方向与鼠标拖动一致。 */
        public void dragPan(double dx, double dy) {
            double perPixel = scale();
            panU += dx * perPixel;
            panV -= dy * perPixel;
        }

        /** 复位到默认视角。 */
        public void reset() {
            yaw = 35.0D;
            pitch = 26.0D;
            zoom = 1.0D;
            panU = 0.0D;
            panV = 0.0D;
        }

        /** 1 像素在“模型局部坐标”里对应多少格（与投影用的焦距一致）。 */
        private double scale() {
            double height = Math.max(1, y1 - y0);
            double perPixel = 2.0D * DISTANCE * zoom / height * StrictMath.tan(StrictMath.toRadians(FOV) * 0.5D);
            // 再按显示倍数校正：屏幕像素不是设计单位时，鼠标移动的像素数才是真实观感
            return perPixel / displayScale;
        }

        /** 画到屏幕上的额外缩放（帧缓冲像素 / 设计单位），用来让拖拽距离贴合真实鼠标位移。 */
        private double displayScale = 1.0D;

        /** 设置画到屏幕上的额外缩放。 */
        public void setDisplayScale(double displayScale) {
            this.displayScale = displayScale <= 0.0D ? 1.0D : displayScale;
        }

        /** 当前显示比例（1 像素对应多少设计单位）。 */
        public double displayScale() {
            return displayScale;
        }

        /** 相机位置（模型局部坐标）：投影平面偏移与视点使用一致的逆旋转。 */
        public Vec3 eye() {
            double yawRad = StrictMath.toRadians(yaw);
            double pitchRad = StrictMath.toRadians(pitch);
            double cameraUp = -panV;
            double cameraDepth = -DISTANCE * zoom;
            double rotatedY = cameraUp * Math.cos(pitchRad) + cameraDepth * Math.sin(pitchRad);
            double rotatedZ = -cameraUp * Math.sin(pitchRad) + cameraDepth * Math.cos(pitchRad);
            double cameraRight = -panU;
            double dx = cameraRight * Math.cos(yawRad) - rotatedZ * Math.sin(yawRad);
            double dz = cameraRight * Math.sin(yawRad) + rotatedZ * Math.cos(yawRad);
            return new Vec3(target.x + dx, target.y + rotatedY, target.z + dz);
        }

        /** 面是否朝向相机（法线指向眼睛）。 */
        public boolean facesCamera(Face face) {
            float[] n = normal(face);
            return facesCamera(face, n, (face.x()[0] + face.x()[1] + face.x()[2]) / 3.0D,
                    (face.y()[0] + face.y()[1] + face.y()[2]) / 3.0D,
                    (face.z()[0] + face.z()[1] + face.z()[2]) / 3.0D);
        }

        /** 同上，复用已经算好的法线与面中心。 */
        boolean facesCamera(Face face, float[] n, double cx, double cy, double cz) {
            Vec3 eye = eye();
            double vx = eye.x - cx;
            double vy = eye.y - cy;
            double vz = eye.z - cz;
            return n[0] * vx + n[1] * vy + n[2] * vz > 0.0D;
        }

        /**
         * 模型局部坐标 → 屏幕坐标。
         *
         * @return {@code {屏幕 X, 屏幕 Y, 相机空间深度}}
         */
        public double[] project(double px, double py, double pz) {
            double yawRad = StrictMath.toRadians(yaw);
            double pitchRad = StrictMath.toRadians(pitch);
            double dx = px - target.x;
            double dy = py - target.y;
            double dz = pz - target.z;
            double rx = dx * Math.cos(yawRad) + dz * Math.sin(yawRad);
            double rz = -dx * Math.sin(yawRad) + dz * Math.cos(yawRad);
            double cy = dy * Math.cos(pitchRad) - rz * Math.sin(pitchRad) + panV;
            double depth = dy * Math.sin(pitchRad) + rz * Math.cos(pitchRad) + DISTANCE * zoom;
            rx += panU;
            double focal = (y1 - y0) * 0.5D / StrictMath.tan(StrictMath.toRadians(FOV) * 0.5D);
            double sx = (x0 + x1) * 0.5D + rx * focal / depth;
            double sy = (y0 + y1) * 0.5D - cy * focal / depth;
            return new double[]{sx, sy, depth};
        }
    }

    // ------------------------------------------------------------------ 绘制

    /**
     * 画一次预览。
     *
     * @param faces   模型面（局部坐标）
     * @param floor   地面（草方块等）面的集合，可以为空
     * @param camera  相机
     * @param tint    整体色调（选中高亮时为 1.05，否则 1.0）
     */
    public static void draw(GuiGraphics graphics, List<Face> faces, List<Face> floor, Camera camera, float tint) {
        // 先把视图推到视口左下角：之后所有投影坐标都相对视口计算，视口在窗口里挪动也不会错位
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(camera.x0, camera.y0, 0.0F);
        List<Projected> projected = new ArrayList<>(faces.size() + floor.size());
        for (Face face : faces) {
            Projected p = project(camera, face);
            if (p != null) {
                projected.add(p);
            }
        }
        for (Face face : floor) {
            Projected p = project(camera, face);
            if (p != null) {
                projected.add(p);
            }
        }
        if (projected.isEmpty()) {
            pose.popPose();
            return;
        }
        // 画家算法：远的先画。轨道是一堆小面片（钢轨、枕木、道床），按深度排序足够正确
        projected.sort(Comparator.comparingDouble(Projected::depth).reversed());

        // 先把要画的面筛出来：背面剔除与明暗都在这里算好，避免在顶点提交过程中做多余计算
        List<Integer> colors = new ArrayList<>(projected.size());
        List<Projected> visible = new ArrayList<>(projected.size());
        for (Projected p : projected) {
            Face face = p.face();
            // 正面判定：面法线朝向相机。不能用屏幕绕序——从下方看时（俯仰角为负），
            // 背面的绕序会反过来，绕序判定会把底面当成正面。
            float[] n = normal(face);
            if (!camera.facesCamera(face, n, p.cx(), p.cy(), p.cz())) {
                continue;
            }
            visible.add(p);
            colors.add(color(face.tint(), shade(n), tint));
        }
        if (visible.isEmpty()) {
            pose.popPose();
            return;
        }

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.setShaderTexture(0, net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // 屏幕坐标在这里是<b>设计单位</b>（调用方已经把界面缩放放进 pose 里）。
        // 顶点直接贴着近平面写：投影得到的 z 用不上，反而会因为在 1 附近被裁掉或与背景抢深度。
        Matrix4f matrix = graphics.pose().last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        for (int index = 0; index < visible.size(); index++) {
            Projected p = visible.get(index);
            Face face = p.face();
            double[] sx = p.sx();
            double[] sy = p.sy();
            int color = colors.get(index);
            for (int i = 0; i < 3; i++) {
                buffer.vertex(matrix, (float) sx[i], (float) sy[i], 0.0F).color(color)
                        .uv(face.u()[i], face.v()[i]).endVertex();
            }
            // 第 4 个顶点与第 1 个重合，凑成 QUADS 模式需要的四个角
            buffer.vertex(matrix, (float) sx[0], (float) sy[0], 0.0F).color(color)
                    .uv(face.u()[0], face.v()[0]).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static Projected project(Camera camera, Face face) {
        if (!face.visible()) {
            return null;
        }
        double depth = 0.0D;
        double[] sx = new double[3];
        double[] sy = new double[3];
        double cx = 0.0D;
        double cy = 0.0D;
        double cz = 0.0D;
        for (int i = 0; i < 3; i++) {
            double[] p = camera.project(face.x()[i], face.y()[i], face.z()[i]);
            if (p[2] < 0.15D) {
                return null;
            }
            sx[i] = p[0];
            sy[i] = p[1];
            depth += p[2];
            cx += face.x()[i];
            cy += face.y()[i];
            cz += face.z()[i];
        }
        // 投影坐标是“视口内坐标”；画的时候视图已经平移到视口左下角，这里换回相对视口的值
        for (int i = 0; i < 3; i++) {
            sx[i] -= camera.x0;
            sy[i] -= camera.y0;
        }
        return new Projected(face, sx, sy, depth / 3.0D, cx / 3.0D, cy / 3.0D, cz / 3.0D);
    }

    /** 面朝向对应的明暗（平行光 + 环境光）。 */
    static float shade(float[] n) {
        float diffuse = (float) Math.max(0.0D, n[0] * LIGHT.x + n[1] * LIGHT.y + n[2] * LIGHT.z);
        return Mth.clamp(0.55F + 0.45F * diffuse, 0.0F, 1.0F);
    }

    static float shade(Face face) {
        return shade(normal(face));
    }

    private static int color(int tint, float shade, float global) {
        float[] base = {
                ((tint >> 16) & 0xFF) / 255.0F,
                ((tint >> 8) & 0xFF) / 255.0F,
                (tint & 0xFF) / 255.0F};
        int r = (int) (255 * Math.min(1.0F, base[0] * shade * global));
        int g = (int) (255 * Math.min(1.0F, base[1] * shade * global));
        int b = (int) (255 * Math.min(1.0F, base[2] * shade * global));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static float[] normal(Face face) {
        float ax = face.x()[1] - face.x()[0];
        float ay = face.y()[1] - face.y()[0];
        float az = face.z()[1] - face.z()[0];
        float bx = face.x()[2] - face.x()[0];
        float by = face.y()[2] - face.y()[0];
        float bz = face.z()[2] - face.z()[0];
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0E-6F) {
            return new float[]{0.0F, 1.0F, 0.0F};
        }
        return new float[]{nx / length, ny / length, nz / length};
    }

    // ------------------------------------------------------------------ 样板面

    /** 一块草方块（局部坐标：中心在原点、上表面 y = 0、向两侧各延伸半个单位）。 */
    public static List<Face> grassBlock(float halfSize) {
        List<Face> faces = new ArrayList<>(5);
        float s = halfSize;
        int topColor = 0x8BC34A;
        int sideColor = 0x6D4C41;
        faces.add(flat(new float[]{-s, 0, -s, s, 0, -s, s, 0, s, -s, 0, s}, topColor));
        faces.add(flat(new float[]{-s, -1, -s, s, -1, -s, s, 0, -s, -s, 0, -s}, sideColor));
        faces.add(flat(new float[]{s, -1, s, -s, -1, s, -s, 0, s, s, 0, s}, sideColor));
        faces.add(flat(new float[]{-s, -1, s, -s, -1, -s, -s, 0, -s, -s, 0, s}, sideColor));
        faces.add(flat(new float[]{s, -1, -s, s, -1, s, s, 0, s, s, 0, -s}, sideColor));
        return faces;
    }

    /** 四个顶点组成的四边形 → 两个三角面。 */
    private static Face flat(float[] xyz, int tint) {
        return new Face(
                new float[]{xyz[0], xyz[3], xyz[6]},
                new float[]{xyz[1], xyz[4], xyz[7]},
                new float[]{xyz[2], xyz[5], xyz[8]},
                new float[]{0.5F, 0.5F, 0.5F},
                new float[]{0.5F, 0.5F, 0.5F},
                Mode.FLAT, tint);
    }

    /** 把一个四边形拆成两个三角面（贴图面用）。 */
    public static void quad(List<Face> out, float[] x, float[] y, float[] z, float[] u, float[] v) {
        out.add(new Face(new float[]{x[0], x[1], x[2]}, new float[]{y[0], y[1], y[2]},
                new float[]{z[0], z[1], z[2]}, new float[]{u[0], u[1], u[2]},
                new float[]{v[0], v[1], v[2]}, Mode.TEXTURED, 0xFFFFFF));
        out.add(new Face(new float[]{x[0], x[2], x[3]}, new float[]{y[0], y[2], y[3]},
                new float[]{z[0], z[2], z[3]}, new float[]{u[0], u[2], u[3]},
                new float[]{v[0], v[2], v[3]}, Mode.TEXTURED, 0xFFFFFF));
    }

    /** 给贴图面乘一个色调（例如选中高亮）。 */
    public static Face withTint(Face face, int tint) {
        return new Face(face.x(), face.y(), face.z(), face.u(), face.v(), face.mode(), tint);
    }

    /** 贴图 sprite 的默认 UV（整张贴图）。 */
    public static float[] fullU(TextureAtlasSprite sprite) {
        return new float[]{sprite.getU(0.0F), sprite.getU(16.0F), sprite.getU(16.0F), sprite.getU(0.0F)};
    }

    public static float[] fullV(TextureAtlasSprite sprite) {
        return new float[]{sprite.getV(0.0F), sprite.getV(0.0F), sprite.getV(16.0F), sprite.getV(16.0F)};
    }
}
