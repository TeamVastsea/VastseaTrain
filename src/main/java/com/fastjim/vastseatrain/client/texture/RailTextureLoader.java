package com.fastjim.vastseatrain.client.texture;

import com.fastjim.vastseatrain.VastseaTrain;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.client.textures.ForgeTextureMetadata;
import net.minecraftforge.client.textures.ITextureAtlasSpriteLoader;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * 美术贴图加载器：图集里的 {@code vastseatrain:block/rail/<名字>} 改为读
 * {@code models/block/rail/<名字>.png|jpg|jpeg}。
 *
 * <h2>怎么被调用的</h2>
 * Forge 只在贴图的 {@code .png.mcmeta} 里写了
 * <pre>{@code {"forge":{"loader":"vastseatrain:rail_art"}}}</pre>
 * 时才会把这张贴图交给这里处理：{@code ResourceMetadata} 解析该段 → {@code ForgeTextureMetadata}
 * 非空 → {@code SpriteLoader.loadSprite} 调 {@link #loadContents}。
 * 贴图本身仍然要由图集的 {@link SpriteSource} 列出来（原版只列 {@code textures/} 下的 PNG），
 * 所以每张美术贴图都保留一份 {@code textures/block/rail/<名字>.png} 占位图，并在旁边放 {@code .png.mcmeta}。
 *
 * <h2>像素怎么替换</h2>
 * 原版那一步交给 Forge 时，{@code spriteImage} 已经是占位 PNG 的像素，
 * 它的 mipmap 层（{@code SpriteContents.byMipLevel}）也是按占位图算好的。
 * 这里把美术图解码、缩放到占位图尺寸后直接写进 {@code spriteImage}，
 * 原版拼图后会对每张贴图调 {@code increaseMipLevel(图集的 mip 级别)}，
 * 用的源就是这张 {@code spriteImage}，所以 mipmap 会自动基于新像素重算。
 * <b>不改尺寸、不换图对象</b>，所以拼图、UV 全部不受影响。
 * <p>
 * 美术目录里没有同名图片时返回 {@code null}，Forge 会回退成“原版自己加载占位 PNG”。
 */
public final class RailTextureLoader implements ITextureAtlasSpriteLoader {
    /** 注册名，写在 .mcmeta 的 {@code forge.loader} 里：{@code vastseatrain:rail_art}。 */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(VastseaTrain.MOD_ID, "rail_art");

    /** 注册与查找共用的单例。 */
    public static final RailTextureLoader INSTANCE = new RailTextureLoader();

    /** 只保留默认帧时长、不含任何帧的动画元数据：等价于“整张图 1 帧、不播放”。 */
    private static final AnimationMetadataSection DETACHED = new AnimationMetadataSection(List.of(), -1, -1, 1, false);

    private RailTextureLoader() {
    }

    @Override
    public SpriteContents loadContents(ResourceLocation name, Resource resource, FrameSize frameSize,
                                       NativeImage spriteImage, AnimationMetadataSection animationMetadata,
                                       ForgeTextureMetadata forgeMeta) {
        try {
            ResourceManager manager = Minecraft.getInstance().getResourceManager();
            ResourceLocation textureFile = RailArtSource.toTextureFile(name);
            ResourceLocation source = RailArtSource.findSource(manager, textureFile);
            if (source == null) {
                // 美术目录里没有：交还原版占位 PNG
                return null;
            }
            BufferedImage decoded = RailArtSource.decode(manager, source);
            int width = spriteImage.getWidth();
            int height = spriteImage.getHeight();
            BufferedImage scaled = RailArtSource.scale(decoded, width, height);
            copyInto(scaled, spriteImage);
            VastseaTrain.LOGGER.debug("美术贴图 {} ← {}（{}x{}）", name, source, width, height);
            // 必须带 animationMetadata，且不能传 forgeMeta（4 参构造的 final 字段为 null，之后访问会 NPE）。
            // 注意 animationMetadata 只用来定帧尺寸（FrameSize），帧列表要留空：
            // 空列表时原版按“整张图 = 1 帧”处理；若把带着 {width,height} 的元数据原样传进去，
            // 16x16 的图没事，但 128x128 的图会被当成 16x16 的 64 帧，帧序号和 UV 全错位。
            //
            // mipmap 不用在这里算：SpriteLoader.stitch 拼图后会对每张贴图调
            // increaseMipLevel(图集的 mip 级别)，而它生成 mipmap 用的源就是 byMipLevel[0]，
            // 也就是上面刚改过像素的这张 spriteImage，所以原版会自动基于新像素重算。
            // （这里若自己调 increaseMipLevel(Integer.MAX_VALUE)，MAX_VALUE + 1 会溢出成负数，
            //   原版判断 `mipLevel + 1 > length` 为 false 直接返回旧数组，等于什么都没做。）
            return new SpriteContents(name, new FrameSize(width, height), spriteImage, DETACHED);
        } catch (Throwable e) {
            // 绝不能抛出异常打断图集重载：记日志后返回 null，由原版回退到占位 PNG
            VastseaTrain.LOGGER.error("美术贴图 {} 读取失败，回退到占位 PNG", name, e);
            return null;
        }
    }

    /** 返回 {@code null} 时 Forge 用原版构造拼装贴图（原版构造是 protected，外部 new 不了）。 */
    @Override
    public TextureAtlasSprite makeSprite(ResourceLocation atlasLocation, SpriteContents contents,
                                         int atlasWidth, int atlasHeight, int spriteX, int spriteY, int mipLevel) {
        return null;
    }

    /** 把 Java ARGB 图逐像素写进 NativeImage；Minecraft 的像素整数使用 ABGR 通道顺序。 */
    private static void copyInto(BufferedImage image, NativeImage target) {
        int width = target.getWidth();
        int height = target.getHeight();
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            image.getRGB(0, y, width, 1, row, 0, width);
            for (int x = 0; x < width; x++) {
                int argb = row[x];
                int abgr = (argb & 0xFF00FF00) | ((argb >>> 16) & 0xFF) | ((argb & 0xFF) << 16);
                target.setPixelRGBA(x, y, abgr);
            }
        }
    }
}
