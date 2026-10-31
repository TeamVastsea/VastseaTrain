package com.fastjim.vastseatrain.client.model;

import com.fastjim.vastseatrain.block.RailCurveBlockEntity;
import com.fastjim.vastseatrain.block.RailCurveBlockEntity.Entry;
import com.fastjim.vastseatrain.track.RailCurve;
import com.fastjim.vastseatrain.track.RailStyle;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 弯道渲染：把整份轨道模型按方块实体给出的旋转角转到切线方向，再平移到条带中心所在位置。
 * <h2>为什么在区块烘焙时算</h2>
 * 方块模型本身按变体只烘焙一次，拿不到坐标；但区块网格重建时会先调用
 * {@link #getModelData}（Forge 会传入带方块实体的 {@link BlockAndTintGetter}），
 * 再调用 {@link #getQuads}。于是在这里现算顶点，结果缓存进区块网格，逐帧不再重算，
 * 与直道每格的绘制成本完全一致。
 * <h2>变换</h2>
 * 与直道相比，只是把模型原点挪到格内的指定位置，并绕 Y 轴转过切线角度：
 * <pre>
 *   x' = cos · p.x − sin · p.z + originX
 *   y' = p.y
 *   z' = sin · p.x + cos · p.z + originZ
 * </pre>
 * 一格可能保存多份模型（急弯时多个采样点落在同一方块），各自按角度与原点变换。
 * 模型本身一个顶点都不改（满长度、满宽度），所以钢轨不会被切断；
 * 相邻条带在弯道内侧自然重叠、外侧的间隙由 3 格宽的道床盖住。
 */
public final class RailCurveBakedModel extends BakedModelWrapper<BakedModel> {
    /** 每个弯道格里的模型列表。 */
    public static final ModelProperty<List<Entry>> STRIPS = new ModelProperty<>();
    /** 每个弯道格的外观（旧存档没有时会变成空，按默认外观渲染）。 */
    public static final ModelProperty<RailStyle> STYLE = new ModelProperty<>();

    /** 一套外观的弯道面数据。 */
    public record StyleFaces(List<RailObjGeometry.Face> faces, boolean shadeQuads) {
    }

    private final List<RailObjGeometry.Face> faces;
    private final boolean shadeQuads;
    /** 其它外观的面数据；默认外观使用上面的 {@link #faces}。 */
    private final Map<RailStyle, StyleFaces> styles;
    private final boolean ambientOcclusion;

    public RailCurveBakedModel(BakedModel straight, List<RailObjGeometry.Face> faces,
                               boolean shadeQuads, boolean ambientOcclusion) {
        this(straight, faces, shadeQuads, Map.of(), ambientOcclusion);
    }

    public RailCurveBakedModel(BakedModel straight, List<RailObjGeometry.Face> faces,
                               boolean shadeQuads, Map<RailStyle, StyleFaces> styles,
                               boolean ambientOcclusion) {
        super(straight);
        this.faces = faces;
        this.shadeQuads = shadeQuads;
        this.styles = styles;
        this.ambientOcclusion = ambientOcclusion;
    }

    /**
     * 从方块实体取出这一格的摆放参数与外观。
     * <p>
     * 直接读方块实体、不经过 {@code ModelDataManager}：后者需要方块实体自己调用刷新，
     * 存档重载 / 客户端刚收到方块数据时容易晚一拍，导致轨道先渲染成直道再跳变。
     */
    @NotNull
    @Override
    public ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos,
                                  @NotNull BlockState state, @NotNull ModelData modelData) {
        if (level.getBlockEntity(pos) instanceof RailCurveBlockEntity curve) {
            List<Entry> strips = curve.entries();
            if (!strips.isEmpty()) {
                return ModelData.builder().with(STRIPS, strips).with(STYLE, curve.style()).build();
            }
        }
        return modelData;
    }

    /** 没有方块坐标的调用（物品栏、粒子等）不输出面，避免出现一份摆在格心的“假”轨道。 */
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return List.of();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand,
                                    @NotNull ModelData data, @Nullable RenderType renderType) {
        if (side != null) {
            return List.of();
        }
        List<Entry> strips = data.get(STRIPS);
        if (strips == null || strips.isEmpty()) {
            return List.of();
        }
        List<BakedQuad> quads = new ArrayList<>(faces.size() * strips.size());
        for (Entry strip : strips) {
            RailStyle style = strip.style() == null ? data.get(STYLE) : strip.style();
            StyleFaces source = style == null ? null : styles.get(style);
            List<RailObjGeometry.Face> usedFaces = source == null ? faces : source.faces();
            boolean usedShadeQuads = source == null ? shadeQuads : source.shadeQuads();
            RailCurve.Transform transform = strip.transform();
            for (RailObjGeometry.Face face : usedFaces) {
                BakedQuad quad = build(face, strip, transform, usedShadeQuads);
                if (quad != null) {
                    quads.add(quad);
                }
            }
        }
        return quads;
    }

    /** 把一个三角面按摆放参数变换后写成 {@link BakedQuad}（第 4 角与第 3 角重合）。 */
    @Nullable
    private BakedQuad build(RailObjGeometry.Face face, Entry strip,
                            RailCurve.Transform transform, boolean shade) {
        float[] p = face.position();
        int count = face.vertexCount();
        if (count < 3) {
            return null;
        }
        float[] xs = new float[count];
        float[] ys = new float[count];
        float[] zs = new float[count];
        float cos = transform.cos();
        float sin = transform.sin();
        float originX = transform.originX();
        float originZ = transform.originZ();
        float[] fitted = new float[2];
        double[] curveData = new double[6];
        for (int i = 0; i < count; i++) {
            float x = p[i * 3];
            float y = p[i * 3 + 1];
            float z = p[i * 3 + 2];
            fitRail(face.part(), strip, x, z, fitted, curveData);
            xs[i] = cos * fitted[0] - sin * fitted[1] + originX;
            ys[i] = y;
            zs[i] = sin * fitted[0] + cos * fitted[1] + originZ;
        }
        // 法线由旋转后的顶点重新算，面朝向（Direction）也跟着旋转，明暗才对得上
        float nx = (ys[1] - ys[0]) * (zs[2] - zs[0]) - (zs[1] - zs[0]) * (ys[2] - ys[0]);
        float ny = (zs[1] - zs[0]) * (xs[2] - xs[0]) - (xs[1] - xs[0]) * (zs[2] - zs[0]);
        float nz = (xs[1] - xs[0]) * (ys[2] - ys[0]) - (ys[1] - ys[0]) * (xs[2] - xs[0]);
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0E-6F) {
            return null;
        }
        nx /= length;
        ny /= length;
        nz /= length;

        QuadBakingVertexConsumer.Buffered buffer = new QuadBakingVertexConsumer.Buffered();
        TextureAtlasSprite sprite = face.sprite();
        buffer.setSprite(sprite);
        buffer.setDirection(Direction.getNearest(nx, ny, nz));
        buffer.setShade(shade);
        // Match RailObjGeometry: custom faces use directional shade, not block-neighbor AO.
        buffer.setHasAmbientOcclusion(false);
        for (int corner = 0; corner < 4; corner++) {
            int index = Math.min(corner, count - 1);
            buffer.vertex(xs[index], ys[index], zs[index]);
            buffer.color(1.0F, 1.0F, 1.0F, 1.0F);
            buffer.uv(sprite.getU(face.u()[index] * 16.0F), sprite.getV(face.v()[index] * 16.0F));
            buffer.uv2(0);
            buffer.normal(nx, ny, nz);
            buffer.endVertex();
        }
        return buffer.getQuad();
    }

    static void fitRail(RailObjGeometry.Part part, Entry strip, float x, float z,
                        float[] out, double[] curveData) {
        if (strip.curveRadius() <= 0 || part == RailObjGeometry.Part.BED) {
            out[0] = x;
            out[1] = z;
            return;
        }
        double arc = strip.curveArc();
        double radius = strip.curveRadius();
        double total = strip.curveTotal();
        fittedPoint(strip, arc, radius, total, curveData, 0);
        fittedRailPoint(strip, arc, x, z, radius, total, curveData, 2);
        double dx = curveData[2] - curveData[0];
        double dz = curveData[3] - curveData[1];
        double cos = Math.cos(strip.angle);
        double sin = Math.sin(strip.angle);
        out[0] = (float) (cos * dx + sin * dz);
        out[1] = (float) (-sin * dx + cos * dz);
    }

    private static void fittedPoint(Entry strip, double arc, double radius, double total,
                                    double[] out, int offset) {
        double theta = arc / radius;
        double t = total <= 0.0D ? 1.0D : Math.max(0.0D, Math.min(1.0D, arc / total));
        double blend = t * t * (3.0D - 2.0D * t);
        double advance = radius * Math.sin(theta) + strip.fitAdvance() * blend;
        double inward = radius * (1.0D - Math.cos(theta)) + strip.fitInward() * blend;
        double ex = directionX(strip.curveEntryAngle());
        double ez = directionZ(strip.curveEntryAngle());
        double ix = strip.curveLeft() ? ez : -ez;
        double iz = strip.curveLeft() ? -ex : ex;
        out[offset] = advance * ex + inward * ix;
        out[offset + 1] = advance * ez + inward * iz;
    }

    private static void fittedRailPoint(Entry strip, double arc, double localX, double localZ,
                                        double centerRadius, double total, double[] out, int offset) {
        double radius = centerRadius + (strip.curveLeft() ? -localZ : localZ);
        radius = Math.max(1.0E-6D, radius);
        double theta = (arc - localX) / centerRadius;
        double t = total <= 0.0D ? 1.0D : Math.max(0.0D, Math.min(1.0D, theta * centerRadius / total));
        double blend = t * t * (3.0D - 2.0D * t);
        double advance = radius * Math.sin(theta) + strip.fitAdvance() * blend;
        double inward = centerRadius - radius * Math.cos(theta) + strip.fitInward() * blend;
        double ex = directionX(strip.curveEntryAngle());
        double ez = directionZ(strip.curveEntryAngle());
        double ix = strip.curveLeft() ? ez : -ez;
        double iz = strip.curveLeft() ? -ex : ex;
        out[offset] = advance * ex + inward * ix;
        out[offset + 1] = advance * ez + inward * iz;
    }

    private static double directionX(double baseAngle) {
        return -Math.cos(baseAngle);
    }

    private static double directionZ(double baseAngle) {
        return -Math.sin(baseAngle);
    }

    // ---- 以下属性全部沿用直道烘焙结果，保证弯道与直道的渲染层、贴图、GUI 表现一致 ----

    @Override
    public boolean useAmbientOcclusion() {
        return ambientOcclusion;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return originalModel.getParticleIcon();
    }

    @Override
    public ItemTransforms getTransforms() {
        return originalModel.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand,
                                             @NotNull ModelData data) {
        return originalModel.getRenderTypes(state, rand, data);
    }
}
