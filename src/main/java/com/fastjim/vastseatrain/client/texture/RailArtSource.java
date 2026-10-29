package com.fastjim.vastseatrain.client.texture;

import com.fastjim.vastseatrain.VastseaTrain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 美术目录 {@code models/block/rail/} 与图集贴图之间的对应关系与解码工具。
 *
 * <h2>约定</h2>
 * 美术文件 {@code assets/vastseatrain/models/block/rail/<名字>.png|jpg|jpeg}
 * 对应图集贴图 {@code vastseatrain:block/rail/<名字>}（即 {@code textures/block/rail/<名字>.png}）。
 * 同一名字有多份时按 <b>png &gt; jpg &gt; jpeg</b> 只取一份。
 * <p>
 * 本类只做“纯 JDK”的事情（ImageIO 解码 / java.awt 缩放），不碰 OpenGL，方便在无游戏环境里复用。
 */
public final class RailArtSource {
    /** 美术目录（资源路径前缀）。 */
    public static final String SOURCE_DIR = "models/block/rail/";
    /** 美术目录（不带结尾斜杠，专供 {@code listResources}；带斜杠会抛 Trailing slash 异常）。 */
    public static final String SOURCE_DIR_NO_SLASH = "models/block/rail";
    /** 贴图目录（资源路径前缀）。 */
    public static final String TARGET_DIR = "textures/block/rail/";
    /** 能用的图片扩展名。 */
    private static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg");
    /** 按优先级排列的扩展名。 */
    private static final String[] ORDERED = {"png", "jpg", "jpeg"};

    private RailArtSource() {
    }

    /**
     * 扫描美术目录，返回“图集贴图路径 → 美术来源文件”的映射（都已带扩展名）。
     * 同一名字多格式时已按 png &gt; jpg &gt; jpeg 去重。
     * <p>
     * 目录遍历见 {@link #listArtFiles}：<b>不能</b>给 {@code listResources} 传以 {@code /} 结尾的路径，
     * 1.20.1 会直接抛 {@code IllegalArgumentException: Trailing slash in path}。
     */
    public static Map<ResourceLocation, ResourceLocation> scan(ResourceManager manager) {
        Map<ResourceLocation, ResourceLocation> chosen = new HashMap<>();
        for (ResourceLocation id : listArtFiles(manager).keySet()) {
            String path = id.getPath();
            int dot = path.lastIndexOf('.');
            if (dot <= 0) {
                continue;
            }
            // models/block/rail/<名字>.<扩展名>  →  block/rail/<名字>
            String bare = path.substring(SOURCE_DIR.length(), dot);
            ResourceLocation sprite = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "block/rail/" + bare);
            ResourceLocation previous = chosen.get(sprite);
            if (previous == null || priority(extensionOf(path)) > priority(extensionOf(previous.getPath()))) {
                chosen.put(sprite, id);
            }
        }
        return chosen;
    }

    /**
     * 列出美术目录里的全部图片文件 → 该文件的资源来源。
     * <p>
     * 不能给 {@code listResources} / {@code listResourceStacks} 传<b>以 / 结尾</b>的路径：
     * 1.20.1 的 {@code MultiPackResourceManager} 会先做
     * {@code checkTrailingDirectoryPath} 并抛 {@code IllegalArgumentException: Trailing slash in path}。
     * 传不带斜杠的 {@code models/block/rail} 则只做前缀匹配（能匹配到目录内文件，
     * 也会顺带匹配 {@code rail_core.json} 这类同前缀文件，由 {@link #isImage} 过滤掉）。
     */
    public static Map<ResourceLocation, List<Resource>> listArtFiles(ResourceManager manager) {
        Map<ResourceLocation, List<Resource>> found = new java.util.TreeMap<>();
        manager.listResources(SOURCE_DIR_NO_SLASH, RailArtSource::isImage)
                .forEach((id, resource) -> found.put(id, List.of(resource)));
        return found;
    }

    /** 贴图资源路径（{@code textures/block/rail/x.png}）→ 美术来源文件（单份）。 */
    public static Map<ResourceLocation, ResourceLocation> textureToSource(ResourceManager manager) {
        Map<ResourceLocation, ResourceLocation> result = new HashMap<>();
        scan(manager).forEach((sprite, source) -> result.put(toTextureFile(sprite), source));
        return result;
    }

    /** 图集贴图 {@code <ns>:block/rail/x} → 贴图文件 {@code <ns>:textures/block/rail/x.png}。 */
    public static ResourceLocation toTextureFile(ResourceLocation sprite) {
        return ResourceLocation.fromNamespaceAndPath(sprite.getNamespace(),
                TARGET_DIR + sprite.getPath().substring("block/rail/".length()) + ".png");
    }

    /** 贴图文件 {@code <ns>:textures/block/rail/x.png} 对应的美术文件（不带扩展名）；不像本模组的轨道贴图时返回 null。 */
    public static ResourceLocation artBase(ResourceLocation textureFile) {
        String path = textureFile.getPath();
        if (!path.startsWith(TARGET_DIR) || !path.endsWith(".png")) {
            return null;
        }
        String bare = path.substring(TARGET_DIR.length(), path.length() - ".png".length());
        if (bare.isEmpty()) {
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath(textureFile.getNamespace(), SOURCE_DIR + bare);
    }

    /**
     * 找到一张贴图对应的美术文件（png &gt; jpg &gt; jpeg）。
     *
     * @param manager 资源管理器
     * @param texture 贴图文件路径，如 {@code vastseatrain:textures/block/rail/gray1.png}
     * @return 美术文件路径，找不到时返回 null
     */
    public static ResourceLocation findSource(ResourceManager manager, ResourceLocation texture) {
        ResourceLocation base = artBase(texture);
        if (base == null) {
            return null;
        }
        for (String extension : ORDERED) {
            ResourceLocation candidate = ResourceLocation.fromNamespaceAndPath(base.getNamespace(),
                    base.getPath() + "." + extension);
            if (manager.getResource(candidate).isPresent()) {
                return candidate;
            }
        }
        return null;
    }

    /** 贴图文件是否应该由美术目录提供（目录里有同名图片时才接管，否则沿用 {@code textures/} 的 PNG）。 */
    public static boolean hasArt(ResourceManager manager, ResourceLocation textureFile) {
        return findSource(manager, textureFile) != null;
    }

    /**
     * 解码美术图片并缩放到指定尺寸（双线性插值，保留 alpha），返回 ARGB 图。
     *
     * @throws IOException 读不到或解不开图片
     */
    public static BufferedImage decode(ResourceManager manager, ResourceLocation source) throws IOException {
        BufferedImage decoded;
        Resource resource = manager.getResourceOrThrow(source);
        try (InputStream in = resource.open()) {
            decoded = ImageIO.read(in);
        }
        if (decoded == null) {
            throw new IOException("无法解码（不受支持的图片格式？）：" + source);
        }
        return decoded;
    }

    /**
     * 解码美术图片并缩放到指定尺寸，返回 ARGB 图。
     *
     * @throws IOException 读不到或解不开图片
     */
    public static BufferedImage decodeAndScale(ResourceManager manager, ResourceLocation source,
                                               int width, int height) throws IOException {
        return scale(decode(manager, source), width, height);
    }

    /** 把任意图片缩放到 width×height 的 ARGB 图（尺寸相同则只转颜色类型，不重采样）。 */
    public static BufferedImage scale(BufferedImage image, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("非法尺寸 " + width + "x" + height);
        }
        if (image.getWidth() == width && image.getHeight() == height
                && image.getType() == BufferedImage.TYPE_INT_ARGB) {
            return image;
        }
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = target.createGraphics();
        try {
            // 缩小用“多步减半”避免锯齿丢细节，放大用双线性
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    image.getWidth() > width * 2 || image.getHeight() > height * 2
                            ? RenderingHints.VALUE_INTERPOLATION_BILINEAR
                            : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /** 美术目录里的全部图片（含被多格式去重掉的），按路径排序。 */
    public static Map<String, Long> listFiles(ResourceManager manager) {
        Map<String, Long> files = new java.util.TreeMap<>();
        listArtFiles(manager).forEach((id, resources) -> {
            long size = -1;
            try (InputStream in = resources.get(0).open()) {
                size = in.readAllBytes().length;
            } catch (Exception e) {
                VastseaTrain.LOGGER.warn("读取美术文件 {} 的字节数失败", id, e);
            }
            files.put(id.toString(), size);
        });
        return files;
    }

    static boolean isImage(ResourceLocation id) {
        String path = id.getPath();
        // 前缀匹配 models/block/rail 时也会匹配到 rail_core.json 这类同前缀文件，这里用带斜杠的前缀挡掉
        if (!path.startsWith(SOURCE_DIR)) {
            return false;
        }
        int dot = path.lastIndexOf('.');
        return dot > SOURCE_DIR.length() && EXTENSIONS.contains(extensionOf(path));
    }

    private static int priority(String extension) {
        return switch (extension) {
            case "png" -> 3;
            case "jpg" -> 2;
            default -> 1;
        };
    }

    private static String extensionOf(String path) {
        return path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
