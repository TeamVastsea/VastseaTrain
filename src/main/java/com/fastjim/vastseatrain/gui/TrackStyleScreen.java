package com.fastjim.vastseatrain.gui;

import com.fastjim.vastseatrain.client.render.RailPreviewGeometry;
import com.fastjim.vastseatrain.client.render.RailPreviewRenderer;
import com.fastjim.vastseatrain.client.render.RailPreviewRenderer.Camera;
import com.fastjim.vastseatrain.track.RailStyle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
 * 布局按窗口尺寸现算；无法容纳最小布局时，面板、按钮、预览与鼠标坐标同步缩放。
 */
public class TrackStyleScreen extends AdaptiveScreen {
    /** 设计尺寸；窗口较小时由 {@link AdaptiveScreen} 整体缩放。 */
    private static final int MIN_WIDTH = 380;
    private static final int DESIGN_HEIGHT = 210;
    private static final int MARGIN = 6;
    private static final int PANEL_TOP = 32;
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

    /** 当前预览的几何（切换选中项时换掉）。 */
    private RailPreviewGeometry.Geometry geometry;
    public TrackStyleScreen(TrackBlueprintScreen parent, RailStyle selected) {
        super(Component.translatable("screen.vastseatrain.styles"), MIN_WIDTH, DESIGN_HEIGHT);
        this.parent = parent;
        this.selected = selected == null ? RailStyle.STANDARD : selected;
    }

    @Override
    protected void init() {
        hideHud();
        layout();

        int rowX = left + GAP;
        int rowWidth = listWidth - GAP * 2;
        int rowY = previewTop() + 26;
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

    /** 按自适应后的逻辑视口排版。 */
    private void layout() {
        int logicalWidth = logicalWidth();
        int logicalHeight = logicalHeight();
        int contentWidth = Math.min(logicalWidth - MARGIN * 2, 760);
        int contentHeight = Math.min(logicalHeight - MARGIN * 2, 300);
        left = (logicalWidth - contentWidth) / 2;
        top = (logicalHeight - contentHeight) / 2;
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
            return;
        }
        if (camera != null) {
            camera.target = new Vec3(geometry.centerX(), (geometry.minY() + geometry.maxY()) * 0.5D,
                    geometry.centerZ());
            camera.panU = 0.0D;
            camera.panV = 0.0D;
            // 模型越大，相机拉得越远，保证始终完整落在预览框里
            camera.setDefaultZoom(Mth.clamp(geometry.planLength() / 2.2D, 0.9D, 4.0D));
        }
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
        mouseX = logicalCoordinate(mouseX);
        mouseY = logicalCoordinate(mouseY);
        if ((button == 0 || button == 1) && overPreview(mouseX, mouseY)) {
            dragMode = button == 0 ? 1 : 2;
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
            // MouseHandler reports frame deltas after mouseMoved, which has already updated its current position.
            double scale = uiScale();
            double dx = dragX / scale;
            double dy = dragY / scale;
            if (dragMode == 1) {
                camera.dragRotate(dx, dy);
            } else {
                camera.dragPan(dx, dy);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double screenX = mouseX;
        double screenY = mouseY;
        mouseX = logicalCoordinate(mouseX);
        mouseY = logicalCoordinate(mouseY);
        if (camera != null && overPreview(mouseX, mouseY)) {
            camera.dragZoom(delta);
            return true;
        }
        return super.mouseScrolled(screenX, screenY, delta);
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
        beginScaledContent(graphics);
        double scale = uiScale();
        mouseX = (int) (mouseX / scale);
        mouseY = (int) (mouseY / scale);

        graphics.drawCenteredString(font, title, left + (listWidth + GAP + previewWidth) / 2, top + 3, 0xFFFFFF);
        int rowX = left + GAP;
        int headerY = previewTop() + 7;
        graphics.drawString(font, Component.translatable("gui.vastseatrain.style.list"),
                rowX, headerY, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("gui.vastseatrain.style.selected",
                selected.getDisplayName()), previewLeft + GAP, headerY, 0xFFFFFF);
        graphics.fill(left, previewTop() + 20, left + listWidth, previewTop() + 21, 0x806A6A78);
        graphics.fill(previewLeft, previewTop() + 20, previewLeft + previewWidth, previewTop() + 21,
                0x806A6A78);
        graphics.fill(previewLeft - GAP / 2, previewTop(), previewLeft - GAP / 2 + 1, panelBottom,
                0x605A5A66);

        // 列表高亮当前选中项（与按钮位置一致）
        int rowY = previewTop() + 26;
        for (RailStyle style : RailStyle.values()) {
            if (style == selected) {
                graphics.fill(rowX - 3, rowY - 2, rowX + listWidth - GAP * 2 + 3, rowY + 22, 0x405C7898);
                graphics.fill(rowX - 3, rowY - 2, rowX - 1, rowY + 22, 0xFFD8D8E0);
            }
            rowY += ROW_HEIGHT;
        }

        // 3D 预览
        if (geometry != null && !geometry.faces().isEmpty() && camera != null) {
            graphics.fill(previewLeft + 1, camera.y0, previewLeft + previewWidth - 1, panelBottom - 1,
                    0xA0202228);
            camera.setRenderScale(uiScale() * minecraft.getWindow().getGuiScale());
            graphics.enableScissor((int) (camera.x0 * scale), (int) (camera.y0 * scale),
                    (int) (camera.x1 * scale), (int) (camera.y1 * scale));
            double gridExtent = 128.0D + Math.abs(camera.panU) + Math.abs(camera.panV)
                    + camera.zoom * 16.0D;
            RailPreviewRenderer.drawGrid(graphics, camera, geometry.centerX(), geometry.centerZ(),
                    RailPreviewRenderer.GROUND_Y - 0.025D, gridExtent);
            RailPreviewRenderer.draw(graphics, geometry.faces(), camera, 1.0F);
            graphics.disableScissor();
            graphics.drawString(font, Component.translatable("gui.vastseatrain.style.hint"),
                    previewLeft + GAP, panelBottom - 14, 0xA0A0A0, false);
        } else {
            graphics.drawCenteredString(font, Component.translatable("gui.vastseatrain.style.preview_failed"),
                    previewLeft + previewWidth / 2, previewTop() + 100, 0xFF6B6B);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        endScaledContent(graphics);
    }

    @Override
    public void removed() {
        RailPreviewRenderer.release();
        super.removed();
    }

    /** 当前选择（供自检/调试）。 */
    public RailStyle selected() {
        return selected;
    }
}
