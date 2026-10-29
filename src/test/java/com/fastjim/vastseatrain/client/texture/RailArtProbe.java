package com.fastjim.vastseatrain.client.texture;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 贴图读取自检（纯 JDK，不需要游戏）。
 *
 * <p>把 {@code src/main/resources/assets/vastseatrain/models/block/rail/} 下的每张 png/jpg/jpeg 解码一遍，
 * 用最近邻缩放到 16×16，输出 PNG 字节（十六进制 + 长度）以及取色统计（平均 RGB、透明像素比例）。
 * 同时检查 {@code textures/block/rail/} 的占位 PNG 与 {@code .mcmeta} 是否配对。</p>
 *
 * <p>运行：</p>
 * <pre>
 * javac -encoding UTF-8 -d build\probe src\test\java\com\fastjim\vastseatrain\client\texture\RailArtProbe.java
 * java -cp build\probe com.fastjim.vastseatrain.client.texture.RailArtProbe
 * </pre>
 */
public final class RailArtProbe {
    private static final String[] EXTENSIONS = {"png", "jpg", "jpeg"};
    private static final int SIZE = 16;

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length > 0 ? args[0] : "src/main/resources/assets/vastseatrain");
        Path art = root.resolve("models/block/rail");
        Path textures = root.resolve("textures/block/rail");
        System.out.println("美术目录：" + art.toAbsolutePath());
        System.out.println("贴图目录：" + textures.toAbsolutePath());

        if (!Files.isDirectory(art)) {
            System.out.println("!! 美术目录不存在");
            return;
        }

        List<Path> files = new ArrayList<>(Files.list(art)
                .filter(Files::isRegularFile)
                .filter(p -> hasImageExtension(p.getFileName().toString()))
                .sorted()
                .toList());

        int ok = 0;
        int failed = 0;
        System.out.println();
        System.out.println("=== 美术文件（共 " + files.size() + " 张） ===");
        for (Path file : files) {
            String name = file.getFileName().toString();
            System.out.println();
            System.out.println("[文件] " + name + "（" + Files.size(file) + " 字节）");
            BufferedImage image;
            try (InputStream in = Files.newInputStream(file)) {
                image = ImageIO.read(in);
            }
            if (image == null) {
                failed++;
                System.out.println("  解码：失败（ImageIO 不支持该格式）");
                continue;
            }
            ok++;
            System.out.println("  解码：成功  " + image.getWidth() + "x" + image.getHeight()
                    + "  类型=" + typeName(image));

            BufferedImage scaled = nearestNeighbor(image, SIZE, SIZE);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            boolean written = ImageIO.write(scaled, "png", png);
            byte[] bytes = png.toByteArray();
            System.out.println("  最近邻缩放 " + SIZE + "x" + SIZE + " → PNG：" + bytes.length
                    + " 字节（编码" + (written ? "成功" : "失败") + "）");
            System.out.println("  PNG 字节：");
            printHexDump(bytes);

            Stats stats = stats(scaled);
            System.out.printf(Locale.ROOT, "  平均 RGB：R=%d G=%d B=%d  (#%02X%02X%02X，不透明像素计)%n",
                    stats.r, stats.g, stats.b, stats.r, stats.g, stats.b);
            System.out.printf(Locale.ROOT, "  透明像素比例：%.2f%%（%d/%d 个像素 alpha<255；完全透明 %d 个）%n",
                    stats.transparent * 100.0 / (SIZE * SIZE), stats.transparent, SIZE * SIZE, stats.fullyTransparent);
            System.out.printf(Locale.ROOT, "  最近邻 16×16 像素矩阵（#RRGGBBaa）：%n");
            for (int y = 0; y < SIZE; y++) {
                StringBuilder row = new StringBuilder("    ");
                for (int x = 0; x < SIZE; x++) {
                    row.append(String.format(Locale.ROOT, "%08x ", scaled.getRGB(x, y)));
                }
                System.out.println(row.toString().stripTrailing());
            }
        }
        System.out.println();
        System.out.println("=== 汇总：解码成功 " + ok + " 张，失败 " + failed + " 张 ===");

        checkTextures(art, textures);
    }

    /** 最近邻缩放（SimpleGraphics2D 不做插值，保证逐像素取样）。 */
    static BufferedImage nearestNeighbor(BufferedImage image, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int sx = (int) ((long) x * image.getWidth() / width);
                int sy = (int) ((long) y * image.getHeight() / height);
                target.setRGB(x, y, image.getRGB(sx, sy));
            }
        }
        return target;
    }

    static Stats stats(BufferedImage image) {
        Stats stats = new Stats();
        long r = 0;
        long g = 0;
        long b = 0;
        long opaque = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a < 255) {
                    stats.transparent++;
                }
                if (a == 0) {
                    stats.fullyTransparent++;
                }
                if (a > 0) {
                    r += (argb >> 16) & 0xFF;
                    g += (argb >> 8) & 0xFF;
                    b += argb & 0xFF;
                    opaque++;
                }
            }
        }
        stats.opaque = (int) opaque;
        if (opaque > 0) {
            stats.r = (int) (r / opaque);
            stats.g = (int) (g / opaque);
            stats.b = (int) (b / opaque);
        }
        return stats;
    }

    /** 检查贴图目录：占位 PNG 是否有同名 .mcmeta，以及 .mcmeta 里的 loader 是否指向本模组。 */
    private static void checkTextures(Path artRoot, Path textures) throws Exception {
        System.out.println();
        System.out.println("=== 图集侧检查（" + textures + "） ===");
        if (!Files.isDirectory(textures)) {
            System.out.println("!! 贴图目录不存在");
            return;
        }
        List<String> names = new ArrayList<>();
        try (var stream = Files.list(textures)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".png"))
                    .map(p -> p.getFileName().toString().substring(0, p.getFileName().toString().length() - 4))
                    .sorted()
                    .forEach(names::add);
        }
        for (String name : names) {
            Path png = textures.resolve(name + ".png");
            Path mcmeta = textures.resolve(name + ".png.mcmeta");
            int[] size = pngSize(Files.readAllBytes(png));
            String loader = "（无 .mcmeta，图集不会走美术加载器）";
            boolean ok = false;
            if (Files.isRegularFile(mcmeta)) {
                try {
                    String json = Files.readString(mcmeta, StandardCharsets.UTF_8);
                    String found = findLoader(json);
                    if (found == null) {
                        loader = "（.mcmeta 无 forge.loader）";
                    } else {
                        loader = found;
                        ok = "vastseatrain:rail_art".equals(found);
                    }
                } catch (Exception e) {
                    loader = "（.mcmeta 解析失败：" + e.getMessage() + "）";
                }
            }
            System.out.printf(Locale.ROOT, "  %-24s 占位 %dx%d  loader=%-20s %s%n",
                    name, size[0], size[1], loader, ok ? "OK" : "!!");
            // 放大后的图集 UV 只认占位图尺寸：占位图必须和美术图的宽高比一致，
            // 否则美术图会被拉变形/被裁掉（rail_sleeper_top 就是 128x128 的高清图）
            Path source = null;
            for (String extension : EXTENSIONS) {
                Path candidate = Path.of(artRoot.toString(), name + "." + extension);
                if (Files.isRegularFile(candidate)) {
                    source = candidate;
                    break;
                }
            }
            if (source != null) {
                int[] artSize = imageSize(Files.readAllBytes(source));
                boolean sameAspect = artSize[0] > 0 && artSize[1] > 0 && size[0] > 0 && size[1] > 0
                        && (long) artSize[0] * size[1] == (long) artSize[1] * size[0];
                System.out.printf(Locale.ROOT, "  %-24s 美术 %dx%d ← %s  %s%n", "",
                        artSize[0], artSize[1], source.getFileName(), sameAspect ? "宽高比一致 OK" : "!! 宽高比不一致");
            }

        }
    }

    /** 从 .mcmeta 文本里取出 forge.loader 的值（不用 gson，保持纯 JDK）。 */
    static String findLoader(String json) {
        int forge = json.indexOf("\"forge\"");
        if (forge < 0) {
            return null;
        }
        int loader = json.indexOf("\"loader\"", forge);
        if (loader < 0) {
            return null;
        }
        int colon = json.indexOf(':', loader);
        int first = json.indexOf('"', colon + 1);
        int second = first < 0 ? -1 : json.indexOf('"', first + 1);
        return first < 0 || second < 0 ? null : json.substring(first + 1, second);
    }

    /** 图片尺寸：PNG 直接读 IHDR，JPEG 用 ImageIO（美术目录里两种格式都有）。 */
    private static int[] imageSize(byte[] bytes) throws Exception {
        if (bytes.length > 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            try (InputStream in = new java.io.ByteArrayInputStream(bytes)) {
                BufferedImage image = ImageIO.read(in);
                if (image != null) {
                    return new int[]{image.getWidth(), image.getHeight()};
                }
            }
            return new int[]{0, 0};
        }
        return pngSize(bytes);
    }

    private static int[] pngSize(byte[] bytes) {

        if (bytes.length < 24) {
            return new int[]{0, 0};
        }
        int w = ((bytes[16] & 0xFF) << 24) | ((bytes[17] & 0xFF) << 16) | ((bytes[18] & 0xFF) << 8) | (bytes[19] & 0xFF);
        int h = ((bytes[20] & 0xFF) << 24) | ((bytes[21] & 0xFF) << 16) | ((bytes[22] & 0xFF) << 8) | (bytes[23] & 0xFF);
        return new int[]{w, h};
    }

    private static void printHexDump(byte[] bytes) {
        HexFormat hex = HexFormat.of().withUpperCase();
        StringBuilder line = new StringBuilder("    ");
        for (int i = 0; i < bytes.length; i++) {
            line.append(hex.toHexDigits(bytes[i]));
            if ((i + 1) % 48 == 0 || i == bytes.length - 1) {
                System.out.println(line.toString());
                line.setLength(0);
                line.append("    ");
            } else {
                line.append(' ');
            }
        }
    }

    private static boolean hasImageExtension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String extension : EXTENSIONS) {
            if (lower.endsWith("." + extension)) {
                return true;
            }
        }
        return false;
    }

    private static String typeName(BufferedImage image) {
        return switch (image.getType()) {
            case BufferedImage.TYPE_INT_RGB -> "INT_RGB";
            case BufferedImage.TYPE_INT_ARGB -> "INT_ARGB";
            case BufferedImage.TYPE_INT_ARGB_PRE -> "INT_ARGB_PRE";
            case BufferedImage.TYPE_INT_BGR -> "INT_BGR";
            case BufferedImage.TYPE_3BYTE_BGR -> "3BYTE_BGR（JPEG 常见）";
            case BufferedImage.TYPE_4BYTE_ABGR -> "4BYTE_ABGR";
            case BufferedImage.TYPE_BYTE_GRAY -> "BYTE_GRAY";
            case BufferedImage.TYPE_BYTE_INDEXED -> "BYTE_INDEXED";
            default -> "类型 " + image.getType();
        };
    }

    static final class Stats {
        int r;
        int g;
        int b;
        int opaque;
        int transparent;
        int fullyTransparent;
    }
}
