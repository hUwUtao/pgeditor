package work.stdpi.pge.editor.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import work.stdpi.pge.editor.logic.EditorManager;

import java.awt.FontFormatException;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class MonoGlyphAtlas {
    private static final int SUPERSAMPLE = 4;
    private static final float CELL_HEIGHT_RATIO = 1.9f;
    private static final String GLYPHS =
        " " +
        "!\"#$%&'()*+,-./0123456789:;<=>?" +
        "@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_" +
        "`abcdefghijklmnopqrstuvwxyz{|}~" +
        "─│┌┐└┘├┤┬┴┼" +
        "╭╮╰╯╴╵╶╷╸╹╺╻╼╽╾╿" +
        "═║╔╗╚╝╠╣╦╩╬╞╡╥╨╪╟╢╤╧╫╋" +
        "┏┓┗┛┠┨┯┷┿" +
        "█▇▆▅▄▃▂▁▀▉▊▋▌▍▎▏░▒▓";
    private static final Map<EditorManager.TerminalFontWeight, String> FONT_RESOURCES = Map.of(
        EditorManager.TerminalFontWeight.LIGHT, "/assets/pge-editor/fonts/IntelOneMono-Light.ttf",
        EditorManager.TerminalFontWeight.REGULAR, "/assets/pge-editor/fonts/IntelOneMono-Regular.ttf",
        EditorManager.TerminalFontWeight.MEDIUM, "/assets/pge-editor/fonts/IntelOneMono-Medium.ttf",
        EditorManager.TerminalFontWeight.BOLD, "/assets/pge-editor/fonts/IntelOneMono-Bold.ttf"
    );
    private static final Map<EditorManager.TerminalFontWeight, Font> EMBEDDED_FONTS = new EnumMap<>(EditorManager.TerminalFontWeight.class);

    private final Map<Character, Glyph> glyphs = new HashMap<>();
    private final Map<Integer, ColoredTexture> coloredTextures = new HashMap<>();
    private int cellWidthPx = 4;
    private EditorManager.TerminalFontWeight fontWeight = EditorManager.TerminalFontWeight.REGULAR;
    private int cellHeightPx;
    private int rasterCellWidth;
    private int rasterCellHeight;
    private int textureWidth;
    private int textureHeight;
    private BufferedImage alphaMask;

    public void ensureReady() {
        if (alphaMask != null) {
            return;
        }

        updateCellMetrics();

        Font font = chooseRasterFont();
        int columns = 16;
        int rows = (int) Math.ceil(GLYPHS.length() / (double) columns);
        int rasterTextureWidth = columns * rasterCellWidth;
        int rasterTextureHeight = rows * rasterCellHeight;

        BufferedImage rasterMask = new BufferedImage(rasterTextureWidth, rasterTextureHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = rasterMask.createGraphics();
        graphics.setFont(font);
        graphics.setColor(Color.WHITE);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        FontMetrics metrics = graphics.getFontMetrics();
        int rasterBaseline = centerBaseline(metrics);
        for (int i = 0; i < GLYPHS.length(); i++) {
            char ch = GLYPHS.charAt(i);
            int col = i % columns;
            int row = i / columns;
            int cellX = col * rasterCellWidth;
            int cellY = row * rasterCellHeight;
            int charWidth = metrics.charWidth(ch);
            int drawX = cellX + Math.max(0, (rasterCellWidth - charWidth) / 2);
            int drawY = cellY + rasterBaseline;
            graphics.drawString(String.valueOf(ch), drawX, drawY);
        }

        graphics.dispose();

        textureWidth = columns * cellWidthPx;
        textureHeight = rows * cellHeightPx;
        alphaMask = new BufferedImage(textureWidth, textureHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D downsample = alphaMask.createGraphics();
        downsample.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        downsample.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        downsample.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        downsample.drawImage(rasterMask, 0, 0, textureWidth, textureHeight, null);
        downsample.dispose();

        for (int i = 0; i < GLYPHS.length(); i++) {
            int col = i % columns;
            int row = i / columns;
            glyphs.put(GLYPHS.charAt(i), new Glyph(col * cellWidthPx, row * cellHeightPx));
        }
    }

    public void setCellWidthPx(int cellWidthPx) {
        int clamped = Math.max(1, Math.min(24, cellWidthPx));
        if (this.cellWidthPx == clamped) {
            return;
        }
        this.cellWidthPx = clamped;
        invalidate();
    }

    public void setFontWeight(EditorManager.TerminalFontWeight fontWeight) {
        EditorManager.TerminalFontWeight target = fontWeight != null ? fontWeight : EditorManager.TerminalFontWeight.REGULAR;
        if (this.fontWeight == target) {
            return;
        }
        this.fontWeight = target;
        invalidate();
    }

    public void drawText(DrawContext context, String text, int x, int y, int color) {
        ensureReady();
        ColoredTexture atlas = coloredTextures.computeIfAbsent(color, this::buildColoredTexture);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int drawX = x + i * cellWidthPx;
            if (ch == ' ') {
                continue;
            }
            if (drawSpecialGlyph(context, ch, drawX, y, color)) {
                continue;
            }
            Glyph glyph = glyphs.getOrDefault(ch, glyphs.get(' '));
            context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                atlas.id,
                drawX,
                y,
                glyph.u,
                glyph.v,
                cellWidthPx,
                cellHeightPx,
                textureWidth,
                textureHeight
            );
        }
    }

    public int getCellWidth() {
        ensureReady();
        return cellWidthPx;
    }

    public int getCellHeight() {
        ensureReady();
        return cellHeightPx;
    }

    public int getBaselineOffset() {
        return 0;
    }

    private boolean drawSpecialGlyph(DrawContext context, char ch, int x, int y, int color) {
        if (drawBoxGlyph(context, ch, x, y, color)) {
            return true;
        }
        return drawBlockGlyph(context, ch, x, y, color);
    }

    private boolean drawBoxGlyph(DrawContext context, char ch, int x, int y, int color) {
        boolean left;
        boolean right;
        boolean up;
        boolean down;
        int thickness = Math.max(1, Math.round(Math.min(cellWidthPx, cellHeightPx) / 6.0f));

        switch (ch) {
            case '─', '═', '╴', '╶' -> {
                left = ch != '╶';
                right = ch != '╴';
                up = false;
                down = false;
            }
            case '│', '║', '╵', '╷' -> {
                left = false;
                right = false;
                up = ch != '╵';
                down = ch != '╷';
            }
            case '┌', '╔', '╭', '┏' -> { left = false; right = true; up = false; down = true; }
            case '┐', '╗', '╮', '┓' -> { left = true; right = false; up = false; down = true; }
            case '└', '╚', '╰', '┗' -> { left = false; right = true; up = true; down = false; }
            case '┘', '╝', '╯', '┛' -> { left = true; right = false; up = true; down = false; }
            case '├', '╠', '┠', '╞', '╟' -> { left = false; right = true; up = true; down = true; }
            case '┤', '╣', '┨', '╡', '╢' -> { left = true; right = false; up = true; down = true; }
            case '┬', '╦', '┯', '╤', '╥' -> { left = true; right = true; up = false; down = true; }
            case '┴', '╩', '┷', '╧', '╨' -> { left = true; right = true; up = true; down = false; }
            case '┼', '╬', '┿', '╪', '╫', '╋', '╼', '╽', '╾', '╿' -> { left = true; right = true; up = true; down = true; }
            case '╸', '╺' -> {
                left = ch != '╺';
                right = ch != '╸';
                up = false;
                down = false;
                thickness = Math.max(thickness, 2);
            }
            case '╹', '╻' -> {
                left = false;
                right = false;
                up = ch != '╹';
                down = ch != '╻';
                thickness = Math.max(thickness, 2);
            }
            default -> {
                return false;
            }
        }

        int centerX = x + cellWidthPx / 2;
        int centerY = y + cellHeightPx / 2;
        int half = Math.max(1, thickness / 2);

        if (left || right) {
            int lineY1 = centerY - half;
            int lineY2 = lineY1 + thickness;
            int lineX1 = left ? x : centerX - half;
            int lineX2 = right ? x + cellWidthPx : centerX + half + 1;
            context.fill(lineX1, lineY1, lineX2, lineY2, color);
        }
        if (up || down) {
            int lineX1 = centerX - half;
            int lineX2 = lineX1 + thickness;
            int lineY1 = up ? y : centerY - half;
            int lineY2 = down ? y + cellHeightPx : centerY + half + 1;
            context.fill(lineX1, lineY1, lineX2, lineY2, color);
        }
        return true;
    }

    private boolean drawBlockGlyph(DrawContext context, char ch, int x, int y, int color) {
        switch (ch) {
            case '█' -> {
                context.fill(x, y, x + cellWidthPx, y + cellHeightPx, color);
                return true;
            }
            case '▀' -> {
                context.fill(x, y, x + cellWidthPx, y + Math.max(1, cellHeightPx / 2), color);
                return true;
            }
            case '▁', '▂', '▃', '▄', '▅', '▆', '▇' -> {
                int level = "▁▂▃▄▅▆▇".indexOf(ch) + 1;
                int blockHeight = Math.max(1, Math.round(cellHeightPx * (level / 8.0f)));
                context.fill(x, y + cellHeightPx - blockHeight, x + cellWidthPx, y + cellHeightPx, color);
                return true;
            }
            case '▉', '▊', '▋', '▌', '▍', '▎', '▏' -> {
                int level = "▉▊▋▌▍▎▏".indexOf(ch);
                float ratio = switch (level) {
                    case 0 -> 7f / 8f;
                    case 1 -> 3f / 4f;
                    case 2 -> 5f / 8f;
                    case 3 -> 1f / 2f;
                    case 4 -> 3f / 8f;
                    case 5 -> 1f / 4f;
                    default -> 1f / 8f;
                };
                int blockWidth = Math.max(1, Math.round(cellWidthPx * ratio));
                context.fill(x, y, x + blockWidth, y + cellHeightPx, color);
                return true;
            }
            case '░', '▒', '▓' -> {
                int alpha = switch (ch) {
                    case '░' -> 0x55;
                    case '▒' -> 0x99;
                    default -> 0xCC;
                };
                int shaded = (color & 0x00FFFFFF) | (alpha << 24);
                context.fill(x, y, x + cellWidthPx, y + cellHeightPx, shaded);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void updateCellMetrics() {
        cellHeightPx = Math.max(3, Math.round(cellWidthPx * CELL_HEIGHT_RATIO));
        rasterCellWidth = cellWidthPx * SUPERSAMPLE;
        rasterCellHeight = cellHeightPx * SUPERSAMPLE;
    }

    private Font chooseRasterFont() {
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = probe.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        int usableWidth = Math.max(1, rasterCellWidth - Math.max(1, rasterCellWidth / 10));
        int usableHeight = Math.max(1, rasterCellHeight - Math.max(1, rasterCellHeight / 12));
        Font base = getEmbeddedFont(fontWeight);
        Font best = base.deriveFont(1f);

        for (int size = 1; size <= rasterCellHeight * 2; size++) {
            Font candidate = base.deriveFont((float) size);
            graphics.setFont(candidate);
            FontMetrics metrics = graphics.getFontMetrics();
            if (maxGlyphWidth(metrics) <= usableWidth && metrics.getAscent() + metrics.getDescent() <= usableHeight) {
                best = candidate;
            } else {
                break;
            }
        }

        graphics.dispose();
        return best;
    }

    private Font getEmbeddedFont(EditorManager.TerminalFontWeight weight) {
        return EMBEDDED_FONTS.computeIfAbsent(weight, key -> {
            String resource = FONT_RESOURCES.getOrDefault(key, FONT_RESOURCES.get(EditorManager.TerminalFontWeight.REGULAR));
            try (InputStream stream = MonoGlyphAtlas.class.getResourceAsStream(resource)) {
                if (stream == null) {
                    return new Font(Font.MONOSPACED, Font.PLAIN, 1);
                }
                return Font.createFont(Font.TRUETYPE_FONT, stream);
            } catch (FontFormatException | IOException e) {
                return new Font(Font.MONOSPACED, Font.PLAIN, 1);
            }
        });
    }

    private int centerBaseline(FontMetrics metrics) {
        int textHeight = metrics.getAscent() + metrics.getDescent();
        int topPadding = Math.max(0, (rasterCellHeight - textHeight) / 2);
        return topPadding + metrics.getAscent();
    }

    private int maxGlyphWidth(FontMetrics metrics) {
        int maxWidth = 0;
        for (int i = 0; i < GLYPHS.length(); i++) {
            maxWidth = Math.max(maxWidth, metrics.charWidth(GLYPHS.charAt(i)));
        }
        return maxWidth;
    }

    private void invalidate() {
        for (ColoredTexture texture : coloredTextures.values()) {
            texture.texture.close();
        }
        coloredTextures.clear();
        glyphs.clear();
        alphaMask = null;
        textureWidth = 0;
        textureHeight = 0;
        cellHeightPx = 0;
        rasterCellWidth = 0;
        rasterCellHeight = 0;
    }

    private ColoredTexture buildColoredTexture(int color) {
        NativeImage nativeImage = new NativeImage(textureWidth, textureHeight, true);
        int alpha = (color >> 24) & 0xFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        for (int y = 0; y < textureHeight; y++) {
            for (int x = 0; x < textureWidth; x++) {
                int mask = alphaMask.getRGB(x, y);
                int glyphAlpha = (mask >> 24) & 0xFF;
                int finalAlpha = glyphAlpha * alpha / 255;
                nativeImage.setColor(x, y, (finalAlpha << 24) | (blue << 16) | (green << 8) | red);
            }
        }

        Identifier id = Identifier.of("pge-editor", "mono_glyph_atlas_" + Integer.toHexString(color));
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "pge_mono_glyph_atlas", nativeImage);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        return new ColoredTexture(id, texture);
    }

    private record Glyph(int u, int v) {}
    private record ColoredTexture(Identifier id, NativeImageBackedTexture texture) {}
}
