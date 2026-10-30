package com.fastjim.vastseatrain.client.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 界面里的 3D 轨道预览（标准 3D 浏览：左键拖拽旋转、滚轮缩放、右键拖拽平移）。
 * <p>
 * 面先投影到离屏颜色/深度缓冲，再合成到 GUI，避免相交面依赖画家排序。
 * <p>
 * 面数据统一在轨道局部坐标里：长度轴沿 X、宽度轴沿 Z、单位是格。
 */
public final class RailPreviewRenderer {
    /** 预览模型面使用方块贴图图集。 */
    public enum Mode {
        TEXTURED,
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
    /** 轨道所在方块的顶面高度，仅供预览网格与相机取景参考，不裁剪模型。 */
    public static final double GROUND_Y = 0.0D;
    private static final double NEAR_PLANE = 0.15D;
    private static final double FAR_PLANE = 4096.0D;
    /** 光照方向（指向光源），与游戏内日光观感接近。 */
    private static final Vec3 LIGHT = new Vec3(0.35D, 0.85D, 0.40D).normalize();

    /** 预览视口内的一个面及其中心点（每帧现算，不缓存）。 */
    private record Projected(Face face, double cx, double cy, double cz) {
    }

    private record ClipVertex(double x, double y, double z, double u, double v) {
    }

    private record DrawFace(Projected projected, int color) {
    }

    private static TextureTarget previewTarget;

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
        public double yaw = 45.0D;
        /** 俯仰（度），负值为从上方俯视。 */
        public double pitch = -35.0D;
        /** 缩放倍数，1 为默认距离。 */
        public double zoom = 1.0D;
        private double defaultZoom = 1.0D;
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
            panU -= dx * perPixel;
            panV -= dy * perPixel;
        }

        /** 复位到默认视角。 */
        public void reset() {
            yaw = 45.0D;
            pitch = -35.0D;
            zoom = defaultZoom;
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
        private double renderScale = 1.0D;

        /** 设置画到屏幕上的额外缩放。 */
        public void setDisplayScale(double displayScale) {
            this.displayScale = displayScale <= 0.0D ? 1.0D : displayScale;
        }

        /** 当前显示比例（1 像素对应多少设计单位）。 */
        public double displayScale() {
            return displayScale;
        }

        public void setRenderScale(double renderScale) {
            this.renderScale = renderScale <= 0.0D ? 1.0D : renderScale;
        }

        public void setDefaultZoom(double defaultZoom) {
            this.defaultZoom = Mth.clamp(defaultZoom, 0.25D, 6.0D);
            this.zoom = this.defaultZoom;
        }

        /** 相机位置（模型局部坐标）：投影平面偏移与视点使用一致的逆旋转。 */
        public Vec3 eye() {
            double yawRad = StrictMath.toRadians(yaw);
            double pitchRad = StrictMath.toRadians(pitch);
            double sinYaw = Math.sin(yawRad);
            double cosYaw = Math.cos(yawRad);
            double sinPitch = Math.sin(pitchRad);
            double cosPitch = Math.cos(pitchRad);
            double centerX = target.x - cosYaw * panU - sinYaw * sinPitch * panV;
            double centerY = target.y - cosPitch * panV;
            double centerZ = target.z - sinYaw * panU + cosYaw * sinPitch * panV;
            return new Vec3(centerX + DISTANCE * zoom * cosPitch * sinYaw,
                    centerY - DISTANCE * zoom * sinPitch,
                    centerZ - DISTANCE * zoom * cosPitch * cosYaw);
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
            double sinYaw = Math.sin(yawRad);
            double cosYaw = Math.cos(yawRad);
            double sinPitch = Math.sin(pitchRad);
            double cosPitch = Math.cos(pitchRad);
            double horizontalDepth = -dx * sinYaw + dz * cosYaw;
            double rx = dx * cosYaw + dz * sinYaw + panU;
            double cy = dy * cosPitch - horizontalDepth * sinPitch + panV;
            double depth = dy * sinPitch + horizontalDepth * cosPitch + DISTANCE * zoom;
            double focal = (y1 - y0) * 0.5D / StrictMath.tan(StrictMath.toRadians(FOV) * 0.5D);
            double sx = (x0 + x1) * 0.5D + rx * focal / depth;
            double sy = (y0 + y1) * 0.5D - cy * focal / depth;
            return new double[]{sx, sy, depth};
        }

        private double depth(double px, double py, double pz) {
            double pitchRad = StrictMath.toRadians(pitch);
            double dx = px - target.x;
            double dy = py - target.y;
            double dz = pz - target.z;
            double horizontalDepth = -dx * StrictMath.sin(StrictMath.toRadians(yaw))
                    + dz * StrictMath.cos(StrictMath.toRadians(yaw));
            return dy * Math.sin(pitchRad) + horizontalDepth * Math.cos(pitchRad) + DISTANCE * zoom;
        }
    }

    // ------------------------------------------------------------------ 绘制

    /**
     * 画一次预览。
     *
     * @param faces   模型面（局部坐标）
     * @param camera  相机
     * @param tint    整体色调（选中高亮时为 1.05，否则 1.0）
     */
    public static void draw(GuiGraphics graphics, List<Face> faces, Camera camera, float tint) {
        // 先把视图推到视口左下角：之后所有投影坐标都相对视口计算，视口在窗口里挪动也不会错位
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(camera.x0, camera.y0, 0.0F);
        try {
        List<Projected> projected = new ArrayList<>(faces.size());
        for (Face face : faces) {
            projected.addAll(project(camera, face));
        }
        if (projected.isEmpty()) {
            return;
        }
        // 明暗在这里算好；面不做背面剔除，与游戏内的 addUnculledFace 一致。
        List<DrawFace> visible = new ArrayList<>(projected.size());
        for (Projected p : projected) {
            Face face = p.face();
            float[] n = normal(face);
            visible.add(new DrawFace(p, color(face.tint(), shade(n), tint)));
        }
        if (visible.isEmpty()) {
            return;
        }

        graphics.flush();
        Minecraft minecraft = Minecraft.getInstance();
        int[] viewport = new int[4];
        int[] scissor = new int[4];
        org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT, viewport);
        org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_SCISSOR_BOX, scissor);
        boolean scissorEnabled = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        boolean depthEnabled = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        boolean blendEnabled = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        boolean cullEnabled = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE);
        boolean depthMask = org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
        int depthFunc = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_DEPTH_FUNC);
        int blendSrcRgb = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB);
        int blendDstRgb = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB);
        int blendSrcAlpha = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA);
        int blendDstAlpha = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        int oldTexture = RenderSystem.getShaderTexture(0);
        var oldShader = RenderSystem.getShader();
        float[] oldShaderColor = RenderSystem.getShaderColor().clone();
        boolean projectionBackedUp = false;
        boolean modelViewPushed = false;
        boolean targetBound = false;
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        try {
            int targetWidth = Math.max(1, (int) Math.ceil((camera.x1 - camera.x0) * camera.renderScale));
            int targetHeight = Math.max(1, (int) Math.ceil((camera.y1 - camera.y0) * camera.renderScale));
            if (previewTarget == null) {
                previewTarget = new TextureTarget(targetWidth, targetHeight, true, Minecraft.ON_OSX);
            } else if (previewTarget.width != targetWidth || previewTarget.height != targetHeight) {
                previewTarget.resize(targetWidth, targetHeight, Minecraft.ON_OSX);
            }

            RenderSystem.disableScissor();
            previewTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            previewTarget.clear(Minecraft.ON_OSX);
            RenderSystem.backupProjectionMatrix();
            projectionBackedUp = true;
            previewTarget.bindWrite(true);
            targetBound = true;
            modelViewStack.pushPose();
            modelViewPushed = true;
            modelViewStack.setIdentity();
            RenderSystem.applyModelViewMatrix();
            Vec3 eye = camera.eye();
            double yawRad = StrictMath.toRadians(camera.yaw);
            double pitchRad = StrictMath.toRadians(camera.pitch);
            double centerX = camera.target.x - camera.panU * Math.cos(yawRad)
                    - camera.panV * Math.sin(yawRad) * Math.sin(pitchRad);
            double centerY = camera.target.y - camera.panV * Math.cos(pitchRad);
            double centerZ = camera.target.z - camera.panU * Math.sin(yawRad)
                    + camera.panV * Math.cos(yawRad) * Math.sin(pitchRad);
            Matrix4f view = new Matrix4f().lookAt(
                    (float) eye.x, (float) eye.y, (float) eye.z,
                    (float) centerX, (float) centerY, (float) centerZ,
                    0.0F, 1.0F, 0.0F);
            modelViewStack.mulPoseMatrix(view);
            modelViewStack.scale(-1.0F, 1.0F, 1.0F);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setPerspective((float) StrictMath.toRadians(FOV),
                    (float) targetWidth / targetHeight, (float) NEAR_PLANE, (float) FAR_PLANE),
                    VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            Matrix4f matrix = new Matrix4f();
            int start = 0;
            while (start < visible.size()) {
                Mode mode = visible.get(start).projected().face().mode();
                int end = start + 1;
                while (end < visible.size() && visible.get(end).projected().face().mode() == mode) {
                    end++;
                }
                drawBatch(matrix, visible, start, end, mode, camera);
                start = end;
            }
        } finally {
            if (targetBound && previewTarget != null) {
                previewTarget.unbindWrite();
            }
            minecraft.getMainRenderTarget().bindWrite(true);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (projectionBackedUp) {
                RenderSystem.restoreProjectionMatrix();
            }
            if (modelViewPushed) {
                modelViewStack.popPose();
            }
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderTexture(0, oldTexture);
            if (oldShader != null) {
                RenderSystem.setShader(() -> oldShader);
            }
            RenderSystem.setShaderColor(oldShaderColor[0], oldShaderColor[1], oldShaderColor[2], oldShaderColor[3]);
            restoreRenderState(scissorEnabled, scissor, depthEnabled, blendEnabled, cullEnabled,
                    depthMask, depthFunc, blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        }

        try {
            graphics.flush();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, previewTarget.getColorTextureId());
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            BufferBuilder composite = Tesselator.getInstance().getBuilder();
            composite.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            composite.vertex(pose.last().pose(), 0.0F, camera.y1 - camera.y0, 0.0F)
                    .uv(0.0F, 0.0F).color(255, 255, 255, 255).endVertex();
            composite.vertex(pose.last().pose(), camera.x1 - camera.x0, camera.y1 - camera.y0, 0.0F)
                    .uv(1.0F, 0.0F).color(255, 255, 255, 255).endVertex();
            composite.vertex(pose.last().pose(), camera.x1 - camera.x0, 0.0F, 0.0F)
                    .uv(1.0F, 1.0F).color(255, 255, 255, 255).endVertex();
            composite.vertex(pose.last().pose(), 0.0F, 0.0F, 0.0F)
                    .uv(0.0F, 1.0F).color(255, 255, 255, 255).endVertex();
            BufferUploader.drawWithShader(composite.end());
        } finally {
            RenderSystem.setShaderTexture(0, oldTexture);
            if (oldShader != null) {
                RenderSystem.setShader(() -> oldShader);
            }
            RenderSystem.setShaderColor(oldShaderColor[0], oldShaderColor[1], oldShaderColor[2], oldShaderColor[3]);
            restoreRenderState(scissorEnabled, scissor, depthEnabled, blendEnabled, cullEnabled,
                    depthMask, depthFunc, blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        }
        } finally {
        pose.popPose();
        }
    }

    private static void restoreRenderState(boolean scissorEnabled, int[] scissor, boolean depthEnabled,
                                           boolean blendEnabled, boolean cullEnabled, boolean depthMask,
                                           int depthFunc, int blendSrcRgb, int blendDstRgb,
                                           int blendSrcAlpha, int blendDstAlpha) {
        if (scissorEnabled) {
            RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
        } else {
            RenderSystem.disableScissor();
        }
        if (depthEnabled) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        if (blendEnabled) {
            RenderSystem.enableBlend();
        } else {
            RenderSystem.disableBlend();
        }
        if (cullEnabled) {
            RenderSystem.enableCull();
        } else {
            RenderSystem.disableCull();
        }
        RenderSystem.depthFunc(depthFunc);
        RenderSystem.depthMask(depthMask);
        RenderSystem.blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
    }

    public static void release() {
        if (previewTarget != null) {
            previewTarget.destroyBuffers();
            previewTarget = null;
        }
    }

    private static List<Projected> project(Camera camera, Face face) {
        if (!face.visible()) {
            return List.of();
        }
        List<ClipVertex> polygon = new ArrayList<>(4);
        for (int i = 0; i < 3; i++) {
            polygon.add(new ClipVertex(face.x()[i], face.y()[i], face.z()[i], face.u()[i], face.v()[i]));
        }
        List<ClipVertex> clipped = new ArrayList<>(4);
        for (int i = 0; i < polygon.size(); i++) {
            ClipVertex current = polygon.get(i);
            ClipVertex next = polygon.get((i + 1) % polygon.size());
            double currentDepth = camera.depth(current.x(), current.y(), current.z());
            double nextDepth = camera.depth(next.x(), next.y(), next.z());
            boolean currentInside = currentDepth >= NEAR_PLANE;
            boolean nextInside = nextDepth >= NEAR_PLANE;
            if (currentInside) {
                clipped.add(current);
            }
            if (currentInside != nextInside) {
                double t = (NEAR_PLANE - currentDepth) / (nextDepth - currentDepth);
                clipped.add(new ClipVertex(
                        current.x() + (next.x() - current.x()) * t,
                        current.y() + (next.y() - current.y()) * t,
                        current.z() + (next.z() - current.z()) * t,
                        current.u() + (next.u() - current.u()) * t,
                        current.v() + (next.v() - current.v()) * t));
            }
        }
        if (clipped.size() < 3) {
            return List.of();
        }

        List<Projected> result = new ArrayList<>(clipped.size() - 2);
        for (int i = 1; i + 1 < clipped.size(); i++) {
            ClipVertex a = clipped.get(0);
            ClipVertex b = clipped.get(i);
            ClipVertex c = clipped.get(i + 1);
            Face triangle = new Face(
                    new float[]{(float) a.x(), (float) b.x(), (float) c.x()},
                    new float[]{(float) a.y(), (float) b.y(), (float) c.y()},
                    new float[]{(float) a.z(), (float) b.z(), (float) c.z()},
                    new float[]{(float) a.u(), (float) b.u(), (float) c.u()},
                    new float[]{(float) a.v(), (float) b.v(), (float) c.v()}, face.mode(), face.tint());
            result.add(projectTriangle(camera, triangle));
        }
        return result;
    }

    private static Projected projectTriangle(Camera camera, Face face) {
        double cx = 0.0D;
        double cy = 0.0D;
        double cz = 0.0D;
        for (int i = 0; i < 3; i++) {
            cx += face.x()[i];
            cy += face.y()[i];
            cz += face.z()[i];
        }
        return new Projected(face, cx / 3.0D, cy / 3.0D, cz / 3.0D);
    }

    private static void drawBatch(Matrix4f matrix, List<DrawFace> faces, int start, int end, Mode mode,
                                  Camera camera) {
        boolean textured = mode == Mode.TEXTURED;
        RenderSystem.setShader(textured ? GameRenderer::getPositionColorTexShader : GameRenderer::getPositionColorShader);
        if (textured) {
            RenderSystem.setShaderTexture(0, net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
        }
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS,
                textured ? DefaultVertexFormat.POSITION_COLOR_TEX : DefaultVertexFormat.POSITION_COLOR);
        for (int index = start; index < end; index++) {
            DrawFace draw = faces.get(index);
            Projected projected = draw.projected();
            Face face = projected.face();
            int color = draw.color();
            for (int i = 0; i < 3; i++) {
                var vertex = buffer.vertex(matrix, face.x()[i], face.y()[i], face.z()[i]).color(color);
                if (textured) {
                    vertex.uv(face.u()[i], face.v()[i]);
                }
                vertex.endVertex();
            }
            var vertex = buffer.vertex(matrix, face.x()[0], face.y()[0], face.z()[0]).color(color);
            if (textured) {
                vertex.uv(face.u()[0], face.v()[0]);
            }
            vertex.endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    /** Draw a Blender-style ground grid in model space before the model itself. */
    public static void drawGrid(GuiGraphics graphics, Camera camera, double centerX, double centerZ,
                                double y, double halfExtent) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(camera.x0, camera.y0, 0.0F);

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR);
        int steps = (int) Math.ceil(halfExtent / 0.5D);
        for (int step = -steps; step <= steps; step++) {
            double offset = step * 0.5D;
            if (step == 0) {
                continue;
            }
            int gridColor = step % 2 == 0 ? 0xC078808C : 0x804F5661;
            addLine(buffer, matrix, camera, centerX + offset, y, centerZ - halfExtent,
                    centerX + offset, y, centerZ + halfExtent, gridColor);
            addLine(buffer, matrix, camera, centerX - halfExtent, y, centerZ + offset,
                    centerX + halfExtent, y, centerZ + offset, gridColor);
        }

        // Blender-style axis colors: X red, Y green, Z blue.
        addLine(buffer, matrix, camera, centerX - halfExtent, y, centerZ,
                centerX + halfExtent, y, centerZ, 0xDFFF5555);
        addLine(buffer, matrix, camera, centerX, y, centerZ - halfExtent,
                centerX, y, centerZ + halfExtent, 0xDF6688FF);
        addLine(buffer, matrix, camera, centerX, y, centerZ,
                centerX, y + Math.max(1.5D, halfExtent * 0.4D), centerZ, 0xDF55FF55);
        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static void addLine(BufferBuilder buffer, Matrix4f matrix, Camera camera,
                                double ax, double ay, double az, double bx, double by, double bz, int color) {
        double depthA = camera.depth(ax, ay, az);
        double depthB = camera.depth(bx, by, bz);
        boolean insideA = depthA >= NEAR_PLANE;
        boolean insideB = depthB >= NEAR_PLANE;
        if (!insideA && !insideB) {
            return;
        }
        if (insideA != insideB) {
            double t = (NEAR_PLANE - depthA) / (depthB - depthA);
            double x = ax + (bx - ax) * t;
            double y = ay + (by - ay) * t;
            double z = az + (bz - az) * t;
            if (insideA) {
                bx = x;
                by = y;
                bz = z;
            } else {
                ax = x;
                ay = y;
                az = z;
            }
        }
        double[] a = camera.project(ax, ay, az);
        double[] b = camera.project(bx, by, bz);
        double[] screenA = {a[0] - camera.x0, a[1] - camera.y0};
        double[] screenB = {b[0] - camera.x0, b[1] - camera.y0};
        if (!clipLineToViewport(screenA, screenB, camera.x1 - camera.x0, camera.y1 - camera.y0)) {
            return;
        }
        buffer.vertex(matrix, (float) screenA[0], (float) screenA[1], 0.0F)
                .color(color).endVertex();
        buffer.vertex(matrix, (float) screenB[0], (float) screenB[1], 0.0F)
                .color(color).endVertex();
    }

    private static boolean clipLineToViewport(double[] start, double[] end, double width, double height) {
        double dx = end[0] - start[0];
        double dy = end[1] - start[1];
        double[] range = {0.0D, 1.0D};
        if (!clipLineEdge(-dx, start[0], range)
                || !clipLineEdge(dx, width - start[0], range)
                || !clipLineEdge(-dy, start[1], range)
                || !clipLineEdge(dy, height - start[1], range)) {
            return false;
        }
        double startX = start[0];
        double startY = start[1];
        start[0] = startX + range[0] * dx;
        start[1] = startY + range[0] * dy;
        end[0] = startX + range[1] * dx;
        end[1] = startY + range[1] * dy;
        return true;
    }

    private static boolean clipLineEdge(double p, double q, double[] range) {
        if (p == 0.0D) {
            return q >= 0.0D;
        }
        double t = q / p;
        if (p < 0.0D) {
            if (t > range[1]) {
                return false;
            }
            range[0] = Math.max(range[0], t);
        } else {
            if (t < range[0]) {
                return false;
            }
            range[1] = Math.min(range[1], t);
        }
        return true;
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
