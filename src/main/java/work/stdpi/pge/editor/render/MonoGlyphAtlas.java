package work.stdpi.pge.editor.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class MonoGlyphAtlas {
    private static final int SUPERSAMPLE = 2;
    private static final String GLYPHS =
        " " +
        "!\"#$%&'()*+,-./0123456789:;<=>?" +
        "@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_" +
        "`abcdefghijklmnopqrstuvwxyz{|}~" +
        "─│┌┐└┘├┤┬┴┼";

    private final Map<Character, Glyph> glyphs = new HashMap<>();
    private final Map<Integer, ColoredTexture> coloredTextures = new HashMap<>();
    private int fontSize = 8;
    private int cellScalePercent = 100;
    private int logicalCellWidth;
    private int logicalCellHeight;
    private int textureWidth;
    private int textureHeight;
    private int cellWidth;
    private int cellHeight;
    private int baseline;
    private BufferedImage alphaMask;

    public void ensureReady() {
        if (alphaMask != null) {
            return;
        }

        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D probeGraphics = probe.createGraphics();
        probeGraphics.setFont(getFont());
        FontMetrics metrics = probeGraphics.getFontMetrics();

        int maxWidth = 0;
        for (int i = 0; i < GLYPHS.length(); i++) {
            maxWidth = Math.max(maxWidth, metrics.charWidth(GLYPHS.charAt(i)));
        }
        cellWidth = maxWidth + 1;
        cellHeight = metrics.getAscent() + metrics.getDescent() + 1;
        updateLogicalCellDimensions();
        baseline = metrics.getAscent();
        probeGraphics.dispose();

        int columns = 16;
        int rows = (int) Math.ceil(GLYPHS.length() / (double) columns);
        textureWidth = columns * cellWidth;
        textureHeight = rows * cellHeight;

        alphaMask = new BufferedImage(textureWidth, textureHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = alphaMask.createGraphics();
        graphics.setFont(getFont());
        graphics.setColor(new Color(255, 255, 255, 255));
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        FontMetrics drawMetrics = graphics.getFontMetrics();
        for (int i = 0; i < GLYPHS.length(); i++) {
            char ch = GLYPHS.charAt(i);
            int col = i % columns;
            int row = i / columns;
            int cellX = col * cellWidth;
            int cellY = row * cellHeight;
            int charWidth = drawMetrics.charWidth(ch);
            int drawX = cellX + Math.max(0, (cellWidth - charWidth) / 2);
            int drawY = cellY + baseline;
            graphics.drawString(String.valueOf(ch), drawX, drawY);
            glyphs.put(ch, new Glyph(cellX, cellY));
        }
        graphics.dispose();

    }

    public void setFontSize(int fontSize) {
        int clamped = Math.max(6, Math.min(14, fontSize));
        if (this.fontSize == clamped) {
            return;
        }
        this.fontSize = clamped;
        invalidate();
    }

    public void setCellScalePercent(int cellScalePercent) {
        int clamped = Math.max(70, Math.min(130, cellScalePercent));
        if (this.cellScalePercent == clamped) {
            return;
        }
        this.cellScalePercent = clamped;
        if (alphaMask != null) {
            updateLogicalCellDimensions();
        }
    }

    public void drawText(DrawContext context, String text, int x, int y, int color) {
        ensureReady();
        ColoredTexture atlas = coloredTextures.computeIfAbsent(color, this::buildColoredTexture);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            Glyph glyph = glyphs.getOrDefault(ch, glyphs.get(' '));
            context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                atlas.id,
                x + i * logicalCellWidth,
                y,
                glyph.u,
                glyph.v,
                logicalCellWidth,
                logicalCellHeight,
                textureWidth,
                textureHeight
            );
        }
    }

    public int getCellWidth() {
        ensureReady();
        return logicalCellWidth;
    }

    public int getCellHeight() {
        ensureReady();
        return logicalCellHeight;
    }

    public int getBaselineOffset() {
        return 0;
    }

    private Font getFont() {
        return new Font(Font.MONOSPACED, Font.PLAIN, fontSize * SUPERSAMPLE);
    }

    private void updateLogicalCellDimensions() {
        logicalCellWidth = Math.max(1, Math.round(cellWidth * (cellScalePercent / 100.0f)));
        logicalCellHeight = Math.max(1, Math.round(cellHeight * (cellScalePercent / 100.0f)));
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
        logicalCellWidth = 0;
        logicalCellHeight = 0;
        cellWidth = 0;
        cellHeight = 0;
        baseline = 0;
    }

    private ColoredTexture buildColoredTexture(int color) {
        int alpha = (color >> 24) & 0xFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        NativeImage nativeImage = new NativeImage(textureWidth, textureHeight, true);
        for (int y = 0; y < textureHeight; y++) {
            for (int x = 0; x < textureWidth; x++) {
                int mask = alphaMask.getRGB(x, y);
                int glyphAlpha = (mask >> 24) & 0xFF;
                int a = glyphAlpha * alpha / 255;
                nativeImage.setColor(x, y, (a << 24) | (blue << 16) | (green << 8) | red);
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
