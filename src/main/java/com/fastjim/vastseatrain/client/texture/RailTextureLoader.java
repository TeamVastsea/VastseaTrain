package com.fastjim.vastseatrain.client.texture;

import com.fastjim.vastseatrain.VastseaTrain;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.client.textures.ForgeTextureMetadata;
import net.minecraftforge.client.textures.ITextureAtlasSpriteLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 方块图集专用贴图加载器：把美术目录 {@code models/block/rail/} 里的 jpg/png 解码成图集贴图。
 *
 * <h2>命中机制</h2>
 * Forge 只会在贴图的侧车文件（{@code textures/block/rail/<名字>.png.mcmeta}）声明
 * {@code {"forge":{"loader":"vastseatrain:rail_art"}}} 时才调用这里——也就是说只能“按 .mcmeta 声明”命中，
 * 不能按贴图名字自动匹配。所以每个由美术目录提供的贴图都要有一个对应的 {@code .mcmeta}。
 * <p>
 * 加载器拿到的 {@code spriteImage} 是 {@code textures/block/rail/<名字>.png} 的像素（本模组里是 1×1 占位图），
 * {@code frameSize} 来自 {@code .mcmeta} 的 {@code animation} 段（我们显式写了宽高）。
 * 这里用 {@link java.awt} 把美术图缩放到 {@code frameSize} 的宽高后替换像素，贴图名与图集尺寸都保持不变。
 * <p>
 * 美术目录里没有同名图片时，本加载器不接管：直接返回 null，图集回落到自己的 {@code textures/} PNG。
 */
public final class RailTextureLoader implements ITextureAtlasSpriteLoader {
    /** 注册名，写在 .mcmeta 的 {@code forge.loader} 里：{@code vastseatrain:rail_art}。 */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(VastseaTrain.MOD_ID, "rail_art");

    /** 单例，注册与查找共用。 */
    public static final RailTextureLoader INSTANCE = new RailTextureLoader();

    private RailTextureLoader() {
    }

    @Override
    public SpriteContents loadContents(ResourceLocation name, Resource resource, FrameSize frameSize,
                                       NativeImage spriteImage, AnimationMetadataSection animationMetadata,
                                       ForgeTextureMetadata forgeMeta) {
        // resource 就是 textures/block/rail/<名字>.png；美术文件在 models/block/rail/ 下的同名图片
        ResourceLocation textureFile = ResourceLocation.fromNamespaceAndPath(name.getNamespace(),
                "textures/" + name.getPath() + ".png");
        try {
            ResourceManager manager = net.minecraft.client.Minecraft.getInstance().getResourceManager();
            ResourceLocation source = RailArtSource.findSource(manager, textureFile);
            if (source == null) {
                // 美术目录里没有：交还图集自己的 PNG
                return null;
            }
            int width = Math.max(1, frameSize.width());
            int height = Math.max(1, frameSize.height());
            BufferedImage decoded = RailArtSource.decodeAndScale(manager, source, width, height);

            ByteArrayOutputStream png = new ByteArrayOutputStream(Math.max(256, width * height * 4));
            if (!ImageIO.write(decoded, "png", png)) {
                VastseaTrain.LOGGER.error("PNG 编码失败：{}", source);
                return null;
            }
            NativeImage nativeImage;
            try (InputStream in = new ByteArrayInputStream(png.toByteArray())) {
                nativeImage = NativeImage.read(in);
            }
            VastseaTrain.LOGGER.debug("美术贴图 {} ← {}（缩放为 {}x{}）", name, source, width, height);
            // 必须用 4 参数版本：5 参数版本会把 forgeMeta 写进 final 字段，传 null 后访问会 NPE
            return new SpriteContents(name, new FrameSize(width, height), nativeImage, null);
        } catch (Throwable e) {
            // 绝不能抛出异常打断图集重载：记日志后返回 null，由原版回落到自己的 PNG
            VastseaTrain.LOGGER.error("美术贴图 {} 解码/缩放失败，回落到原贴图", name, e);
            return null;
        }
    }

    @Override
    public TextureAtlasSprite makeSprite(ResourceLocation atlasLocation, SpriteContents contents,
                                         int atlasWidth, int atlasHeight,
                                         int spriteX, int spriteY, int mipLevel) {
        // TextureAtlasSprite 的构造器是 protected：用匿名子类，图集照常按 SpriteContents 摆放
        return new TextureAtlasSprite(atlasLocation, contents, atlasWidth, atlasHeight, spriteX, spriteY) {
        };
    }
}
