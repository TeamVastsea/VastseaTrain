package com.fastjim.vastseatrain.gui;

import com.fastjim.vastseatrain.client.render.RailPreviewGeometry;
import com.fastjim.vastseatrain.client.render.RailPreviewRenderer;
import com.fastjim.vastseatrain.client.render.RailPreviewRenderer.Camera;
import com.fastjim.vastseatrain.client.render.RailPreviewRenderer.Face;
import com.fastjim.vastseatrain.track.RailStyle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * “轨道模型”二级界面：左边是可选轨道外观的列表，右边是 3D 预览。
 * <p>
 * 3D 预览是一套标准浏览操作：<b>左键拖动旋转</b>、<b>滚轮缩放</b>、<b>右键拖动平移</b>；
 * 点“复位视角”回到默认角度。选中的外观立即生效（只影响之后新铺设的轨道），
 * 点“完成”或按 Esc 返回蓝图界面。
 * <p>
 * 布局全部按<b>当前窗口尺寸</b>现算（设计尺寸 560 × 210 只是最小尺寸），
 * 所有坐标直接使用 GUI 的坐标系，不做额外的整体缩放，因此与游戏自带的界面缩放（自动/小/中/大）
 * 叠加后仍然对得上：画在哪、点在哪完全一致。
 */
public class TrackStyleScreen extends Screen {
    /** 设计尺寸：窗口再小也至少按这个尺寸排版，超出部分允许裁切。 */
    private static final int DESIGN_WIDTH = 560;
    private static final int DESIGN_HEIGHT = 210;
    private static final int MARGIN = 6;
    private static final int PANEL_TOP = 22;
    private static final int LIST_WIDTH = 168;
    private static final int ROW_HEIGHT = 24;
    private static final int GAP = 8;

    private final TrackBlueprintScreen parent;
    private RailStyle selected;

    private int left;
    private int top;
    private int listWidth;
    private int previewLeft;
    private int previewWidth;
    private int panelBottom;

    private Camera camera;
    /** 当前拖拽模式：0 无、1 左键旋转、2 右键平移。 */
    private int dragMode;
    private double lastMouseX;
    private double lastMouseY;

    /** 当前预览的几何（切换选中项时换掉）。 */
    private RailPreviewGeometry.Geometry geometry;
    private List<Face> floor = List.of();

    public TrackStyleScreen(TrackBlueprintScreen parent, RailStyle selected) {
        super(Component.translatable("screen.vastseatrain.styles"));
        this.parent = parent;
        this.selected = selected == null ? RailStyle.STANDARD : selected;
    }

    @Override
    protected void init() {
        layout();

        int rowX = left + GAP;
        int rowWidth = listWidth - GAP * 2;
        int rowY = previewTop() + GAP;
        for (RailStyle style : RailStyle.values()) {
            RailStyle value = style;
            addRenderableWidget(Button.builder(style.getDisplayName(), b -> {
                selected = value;
                rebuildGeometry();
            }).bounds(rowX, rowY, rowWidth, 20).build());
            rowY += ROW_HEIGHT;
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.vastseatrain.style.done"), b -> {
            apply();
            onClose();
        }).bounds(rowX, panelBottom - 26, rowWidth, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.vastseatrain.style.reset_view"), b -> {
            if (camera != null) {
                camera.reset();
            }
        }).bounds(previewLeft + previewWidth - 82, previewTop() + 4, 78, 18).build());

        camera = new Camera(previewLeft, previewTop() + 24, previewLeft + previewWidth,
                panelBottom);
        rebuildGeometry();
    }

    /** 按当前窗口尺寸排版：窗口变大时面板跟着变大，但不超过设计尺寸太多。 */
    private void layout() {
        int contentWidth = Math.max(DESIGN_WIDTH, Math.min(width - MARGIN * 2, 760));
        int contentHeight = Math.max(DESIGN_HEIGHT, Math.min(height - MARGIN * 2, 300));
        left = (width - contentWidth) / 2;
        top = (height - contentHeight) / 2;
        listWidth = Math.min(LIST_WIDTH, contentWidth / 3);
        previewLeft = left + listWidth + GAP;
        previewWidth = left + contentWidth - previewLeft;
        panelBottom = top + contentHeight;
    }

    private int previewTop() {
        return top + PANEL_TOP;
    }

    /** 载入选中外观的模型，并按模型大小把相机拉到合适距离。 */
    private void rebuildGeometry() {
        geometry = null;
        try {
            geometry = RailPreviewGeometry.of(selected);
        } catch (Exception e) {
            geometry = null;
        }
        if (geometry == null || geometry.faces().isEmpty()) {
            floor = List.of();
            return;
        }
        if (camera != null) {
            // 模型已居中在原点、方块上表面为 y = 0：相机对准模型包围盒中心
            camera.target = new Vec3(0.0D, (geometry.minY() + geometry.maxY()) * 0.5D, 0.0D);
            camera.panU = 0.0D;
            camera.panV = 0.0D;
            // 模型越大，相机拉得越远，保证始终完整落在预览框里
            camera.zoom = Mth.clamp(geometry.planLength() / 2.2D, 0.9D, 4.0D);
        }
        float half = Math.max(0.5F, Math.max(geometry.maxX() - geometry.minX(),
                geometry.maxZ() - geometry.minZ()) * 0.5F);
        floor = RailPreviewRenderer.grassBlock(half);
    }

    /** 是否点在 3D 预览区内。 */
    private boolean overPreview(double mouseX, double mouseY) {
        return camera != null && mouseX >= camera.x0 && mouseX <= camera.x1
                && mouseY >= camera.y0 && mouseY <= camera.y1;
    }

    /** 把当前选择交给父界面。 */
    private void apply() {
        if (parent != null) {
            parent.applyStyle(selected);
        }
    }

    // ------------------------------------------------------------------ 交互

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 先让按钮处理点击；点空白处才进入 3D 浏览
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if ((button == 0 || button == 1) && overPreview(mouseX, mouseY)) {
            dragMode = button == 0 ? 1 : 2;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragMode != 0) {
            dragMode = 0;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragMode != 0 && camera != null) {
            // 用“上一次位置到当前位置”的屏幕位移：与界面缩放、帧缓冲缩放无关
            double dx = mouseX - lastMouseX;
            double dy = mouseY - lastMouseY;
            if (dragMode == 1) {
                camera.dragRotate(dx, dy);
            } else {
                camera.dragPan(dx, dy);
            }
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (camera != null && overPreview(mouseX, mouseY)) {
            camera.dragZoom(delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        // 直接关窗口（Esc）也把选择交回父界面
        apply();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ 绘制

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        panel(graphics, left, listWidth);
        panel(graphics, previewLeft, previewWidth);
        graphics.drawCenteredString(font, title, left + (listWidth + GAP + previewWidth) / 2, top + 6, 0xFFFFFF);
        int rowX = left + GAP;
        graphics.drawString(font, Component.translatable("gui.vastseatrain.style.list"),
                rowX, previewTop() + GAP - 12, 0xFFD27F);
        graphics.drawString(font, Component.translatable("gui.vastseatrain.style.selected",
                selected.getDisplayName()), previewLeft + GAP, previewTop() + GAP - 12, 0xFFD27F);

        // 列表高亮当前选中项（与按钮位置一致）
        int rowY = previewTop() + GAP;
        for (RailStyle style : RailStyle.values()) {
            if (style == selected) {
                graphics.fill(rowX - 3, rowY - 2, rowX + listWidth - GAP * 2 + 3, rowY + 22, 0x40FFD27F);
                graphics.fill(rowX - 3, rowY - 2, rowX - 1, rowY + 22, 0xFFFFD27F);
            }
            rowY += ROW_HEIGHT;
        }

        // 3D 预览
        if (geometry != null && !geometry.faces().isEmpty() && camera != null) {
            graphics.enableScissor(camera.x0, camera.y0, camera.x1, camera.y1);
            RailPreviewRenderer.draw(graphics, geometry.faces(), floor, camera, 1.0F);
            graphics.disableScissor();
            graphics.drawString(font, Component.translatable("gui.vastseatrain.style.hint"),
                    previewLeft + GAP, panelBottom - 14, 0xA0A0A0, false);
        } else {
            graphics.drawCenteredString(font, Component.translatable("gui.vastseatrain.style.preview_failed"),
                    previewLeft + previewWidth / 2, previewTop() + 100, 0xFF6B6B);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * 面板背景。
     * <p>
     * 面板是纯色半透明，直接按屏幕坐标画，不经过任何整体缩放——这样面板边缘、阴影都与按钮的
     * 位置严丝合缝。
     */
    private void panel(GuiGraphics graphics, int x, int width) {
        graphics.fill(x, previewTop(), x + width, panelBottom, 0xC0101018);
        graphics.fill(x, previewTop(), x + width, previewTop() + 1, 0x80FFD27F);
        graphics.fill(x, panelBottom - 1, x + width, panelBottom, 0x80FFD27F);
    }

    /** 当前选择（供自检/调试）。 */
    public RailStyle selected() {
        return selected;
    }
}
