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
import org.joml.Vector3f;

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
 *   y' = p.y + 0.5
 *   z' = sin · p.x + cos · p.z + originZ
 * </pre>
 * 一格可能叠多份模型（急弯补缝），各自按自己的角度与原点变换。
 * 模型本身一个顶点都不改（满长度、满宽度），所以钢轨不会被切断；
 * 相邻条带在弯道内侧自然重叠、外侧的间隙由 3 格宽的道床盖住。
 */
public final class RailCurveBakedModel extends BakedModelWrapper<BakedModel> {
    /** 每个弯道格里的模型列表。 */
    public static final ModelProperty<List<Entry>> STRIPS = new ModelProperty<>();
    /** 每个弯道格的外观（旧存档没有时会变成空，按默认外观渲染）。 */
    public static final ModelProperty<RailStyle> STYLE = new ModelProperty<>();

    /** 一套外观的弯道面数据（模型原点 + 面）。 */
    public record StyleFaces(Vector3f origin, List<RailObjGeometry.Face> faces) {
    }

    private final Vector3f origin;
    private final List<RailObjGeometry.Face> faces;
    /** 其它外观的面数据；本方块对应外观（默认外观）用上面的 {@link #origin}/{@link #faces}。 */
    private final Map<RailStyle, StyleFaces> styles;
    private final boolean ambientOcclusion;

    public RailCurveBakedModel(BakedModel straight, Vector3f origin,
                               List<RailObjGeometry.Face> faces, boolean ambientOcclusion) {
        this(straight, origin, faces, Map.of(), ambientOcclusion);
    }

    public RailCurveBakedModel(BakedModel straight, Vector3f origin,
                               List<RailObjGeometry.Face> faces, Map<RailStyle, StyleFaces> styles,
                               boolean ambientOcclusion) {
        super(straight);
        this.origin = origin;
        this.faces = faces;
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
        RailStyle style = data.get(STYLE);
        StyleFaces source = style == null ? null : styles.get(style);
        Vector3f usedOrigin = source == null ? origin : source.origin();
        List<RailObjGeometry.Face> usedFaces = source == null ? faces : source.faces();
        List<BakedQuad> quads = new ArrayList<>(usedFaces.size() * strips.size());
        for (Entry strip : strips) {
            RailCurve.Transform transform = strip.transform();
            for (RailObjGeometry.Face face : usedFaces) {
                BakedQuad quad = build(face, transform, usedOrigin);
                if (quad != null) {
                    quads.add(quad);
                }
            }
        }
        return quads;
    }

    /** 把一个三角面按摆放参数变换后写成 {@link BakedQuad}（第 4 角与第 3 角重合）。 */
    @Nullable
    private BakedQuad build(RailObjGeometry.Face face, RailCurve.Transform transform, Vector3f origin) {
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
        for (int i = 0; i < count; i++) {
            float x = p[i * 3] + origin.x();
            float y = p[i * 3 + 1] + origin.y();
            float z = p[i * 3 + 2] + origin.z();
            xs[i] = cos * x - sin * z + originX;
            ys[i] = y;
            zs[i] = sin * x + cos * z + originZ;
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
        buffer.setShade(true);
        buffer.setHasAmbientOcclusion(ambientOcclusion);
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
