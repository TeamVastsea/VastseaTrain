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
 * 布局按设计尺寸 560 × 210 排版，与蓝图界面共用同一套缩放规则（见 {@link TrackBlueprintScreen}）：
 * 控件按设计坐标创建，渲染时整体缩放，点击时把屏幕坐标反算回设计坐标。
 */
public class TrackStyleScreen extends Screen {
    private static final int DESIGN_WIDTH = 560;
    private static final int DESIGN_HEIGHT = 210;
    private static final int PANEL_TOP = 26;
    private static final int PANEL_BOTTOM = DESIGN_HEIGHT - 10;
    private static final int PANEL_LEFT = 8;
    private static final int LIST_WIDTH = 168;
    private static final int PREVIEW_LEFT = PANEL_LEFT + LIST_WIDTH + 8;
    private static final int PREVIEW_WIDTH = DESIGN_WIDTH - PREVIEW_LEFT - 8;
    private static final int ROW_TOP = PANEL_TOP + 22;
    private static final int ROW_HEIGHT = 28;

    private final TrackBlueprintScreen parent;
    private RailStyle selected;

    private float scale = 1.0F;
    private float originX;
    private float originY;

    private Camera camera;
    /** 当前拖拽模式：0 无、1 左键旋转、2 右键平移。 */
    private int dragMode;

    /** 当前预览的几何（切换选中项时换掉）。 */
    private RailPreviewGeometry.Geometry geometry;
    private List<Face> floor = List.of();
    /** 左键拖拽的起点（设计坐标）。 */
    private int lastClickX;
    private int lastClickY;

    public TrackStyleScreen(TrackBlueprintScreen parent, RailStyle selected) {
        super(Component.translatable("screen.vastseatrain.styles"));
        this.parent = parent;
        this.selected = selected == null ? RailStyle.STANDARD : selected;
    }

    @Override
    protected void init() {
        scale = computeScale();
        originX = (width - DESIGN_WIDTH * scale) / 2.0F;
        originY = (height - DESIGN_HEIGHT * scale) / 2.0F;

        int rowX = rowX();
        int rowWidth = LIST_WIDTH - 16;
        int rowY = ROW_TOP;
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
        }).bounds(rowX, PANEL_BOTTOM - 26, rowWidth, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.vastseatrain.style.reset_view"), b -> {
            if (camera != null) {
                camera.reset();
            }
        }).bounds(PREVIEW_LEFT + PREVIEW_WIDTH - 82, PANEL_TOP + 6, 78, 18).build());

        camera = new Camera(PREVIEW_LEFT + 4, PANEL_TOP + 26, PREVIEW_LEFT + PREVIEW_WIDTH - 4,
                PANEL_BOTTOM - 6);
        rebuildGeometry();
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
        // 模型已居中在原点（方块上表面 y = 0），相机对准原点即可
        if (camera != null) {
            camera.target = new Vec3(0.0D, 0.35D, 0.0D);
            // 模型越大，相机拉得越远，保证始终完整落在预览框里
            camera.zoom = Mth.clamp(geometry.planLength() / 1.9D, 0.8D, 4.0D);
        }
        float half = Math.max(0.5F, Math.max(geometry.maxX() - geometry.minX(),
                geometry.maxZ() - geometry.minZ()) * 0.5F);
        floor = RailPreviewRenderer.grassBlock(half);
    }

    private float computeScale() {
        float fitWidth = (width - 8) / (float) DESIGN_WIDTH;
        float fitHeight = (height - 8) / (float) DESIGN_HEIGHT;
        return Math.max(0.5F, Math.min(1.0F, Math.min(fitWidth, fitHeight)));
    }

    private int rowX() {
        return PANEL_LEFT + 8;
    }

    private int designX(double screenX) {
        return Math.round((float) ((screenX - originX) / scale));
    }

    private int designY(double screenY) {
        return Math.round((float) ((screenY - originY) / scale));
    }

    /** 是否点在 3D 预览区内。 */
    private boolean overPreview(double screenX, double screenY) {
        int x = designX(screenX);
        int y = designY(screenY);
        return camera != null && x >= PREVIEW_LEFT && x <= PREVIEW_LEFT + PREVIEW_WIDTH
                && y >= PANEL_TOP + 26 && y <= PANEL_BOTTOM;
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
        if (super.mouseClicked(designX(mouseX), designY(mouseY), button)) {
            return true;
        }
        if ((button == 0 || button == 1) && overPreview(mouseX, mouseY)) {
            dragMode = button == 0 ? 1 : 2;
            lastClickX = designX(mouseX);
            lastClickY = designY(mouseY);
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
        return super.mouseReleased(designX(mouseX), designY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragMode != 0 && camera != null) {
            int dx = designX(mouseX) - lastClickX;
            int dy = designY(mouseY) - lastClickY;
            if (dragMode == 1) {
                // 左键：绕模型中心旋转；每次位移按“上次点到现在的像素差”，与界面缩放无关
                camera.dragRotate(dx, dy);
            } else {
                // 右键：平移模型
                camera.dragPan(dx, dy);
            }
            lastClickX = designX(mouseX);
            lastClickY = designY(mouseY);
            return true;
        }
        return super.mouseDragged(designX(mouseX), designY(mouseY), button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (camera != null && overPreview(mouseX, mouseY)) {
            camera.dragZoom(delta);
            return true;
        }
        return super.mouseScrolled(designX(mouseX), designY(mouseY), delta);
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

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(originX, originY, 0.0F);
        pose.scale(scale, scale, 1.0F);

        panel(graphics, PANEL_LEFT, LIST_WIDTH);
        panel(graphics, PREVIEW_LEFT, PREVIEW_WIDTH);
        graphics.drawCenteredString(font, title, DESIGN_WIDTH / 2, 8, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("gui.vastseatrain.style.list"),
                rowX(), PANEL_TOP + 8, 0xFFD27F);
        graphics.drawString(font, Component.translatable("gui.vastseatrain.style.selected",
                selected.getDisplayName()), PREVIEW_LEFT + 8, PANEL_TOP + 9, 0xFFD27F);

        // 列表高亮当前选中项
        int rowY = ROW_TOP;
        for (RailStyle style : RailStyle.values()) {
            if (style == selected) {
                graphics.fill(rowX() - 3, rowY - 2, rowX() + LIST_WIDTH - 19, rowY + 22, 0x40FFD27F);
                graphics.fill(rowX() - 3, rowY - 2, rowX() - 1, rowY + 22, 0xFFFFD27F);
            }
            rowY += ROW_HEIGHT;
        }

        // 3D 预览
        if (geometry != null && !geometry.faces().isEmpty() && camera != null) {
            graphics.enableScissor(
                    Math.round(originX + camera.x0 * scale),
                    Math.round(originY + camera.y0 * scale),
                    Math.round(originX + camera.x1 * scale),
                    Math.round(originY + camera.y1 * scale));
            RailPreviewRenderer.draw(graphics, geometry.faces(), floor, camera, 1.0F);
            graphics.disableScissor();
            graphics.drawString(font, Component.translatable("gui.vastseatrain.style.hint"),
                    PREVIEW_LEFT + 8, PANEL_BOTTOM - 14, 0xA0A0A0, false);
        } else {
            graphics.drawCenteredString(font, Component.translatable("gui.vastseatrain.style.preview_failed"),
                    PREVIEW_LEFT + PREVIEW_WIDTH / 2, PANEL_TOP + 100, 0xFF6B6B);
        }

        for (var renderable : this.renderables) {
            renderable.render(graphics, designX(mouseX), designY(mouseY), partialTick);
        }
        pose.popPose();
    }

    private static void panel(GuiGraphics graphics, int x, int width) {
        graphics.fill(x, PANEL_TOP, x + width, PANEL_BOTTOM, 0xC0101018);
        graphics.fill(x, PANEL_TOP, x + width, PANEL_TOP + 1, 0x80FFD27F);
        graphics.fill(x, PANEL_BOTTOM - 1, x + width, PANEL_BOTTOM, 0x80FFD27F);
    }

    /** 当前选择（供自检/调试）。 */
    public RailStyle selected() {
        return selected;
    }
}
