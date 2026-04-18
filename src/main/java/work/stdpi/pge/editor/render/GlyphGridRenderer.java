package work.stdpi.pge.editor.render;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

public class GlyphGridRenderer {
    private static final int PADDING_X = 2;
    private static final int PADDING_Y = 2;

    private final MonoGlyphAtlas atlas = new MonoGlyphAtlas();
    private int cursorCol = 3;
    private int cursorRow = 2;
    private long startedAt = System.nanoTime();

    public void render(DrawContext context, int x, int y, int width, int height) {
        int cellWidth = atlas.getCellWidth();
        int cellHeight = atlas.getCellHeight();
        int cols = Math.max(1, (width - PADDING_X * 2) / cellWidth);
        int rows = Math.max(1, (height - PADDING_Y * 2) / cellHeight);

        context.fill(x, y, x + width, y + height, 0xFF0B0F14);
        drawSampleBuffer(context, x, y, cols, rows, cellHeight);
        drawCursor(context, x, y, cols, rows, cellWidth, cellHeight);
    }

    public boolean onMouse(int localX, int localY, int button, int action, int mods, int width, int height) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
            moveCursorTo(localX, localY, width, height);
            return true;
        }
        return true;
    }

    public void onMove(int localX, int localY, int width, int height) {
    }

    public boolean onKey(int key, int action) {
        if (action == GLFW.GLFW_RELEASE) {
            return false;
        }

        switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> cursorCol = Math.max(0, cursorCol - 1);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> cursorCol += 1;
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> cursorRow = Math.max(0, cursorRow - 1);
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> cursorRow += 1;
            case GLFW.GLFW_KEY_HOME -> cursorCol = 0;
            case GLFW.GLFW_KEY_END -> cursorCol = 9999;
            default -> {
                return false;
            }
        }
        return true;
    }

    private void drawSampleBuffer(DrawContext context, int panelX, int panelY, int cols, int rows, int cellHeight) {
        String[] lines = buildLines(cols, rows);
        int visibleRows = Math.min(rows, lines.length);

        for (int row = 0; row < visibleRows; row++) {
            String line = lines[row];
            if (line.isEmpty()) {
                continue;
            }

            int drawY = panelY + PADDING_Y + row * cellHeight + atlas.getBaselineOffset();
            if (row == rows - 1) {
                drawRun(context, panelX, drawY, line, 0, 0xFF17212B, 0xFFE6EDF3, cellHeight);
            } else {
                drawRun(context, panelX, drawY, line, 0, 0xFF0B0F14, 0xFFE6EDF3, cellHeight);
            }
        }
    }

    private void drawRun(DrawContext context, int panelX, int drawY, String text, int startCol, int background, int foreground, int cellHeight) {
        int cellWidth = atlas.getCellWidth();
        int rx = panelX + PADDING_X + startCol * cellWidth;
        int ry = drawY;
        context.fill(rx, ry, rx + text.length() * cellWidth, ry + cellHeight, background);
        atlas.drawText(context, text, rx, drawY, foreground);
    }

    private void drawCursor(DrawContext context, int x, int y, int cols, int rows, int cellWidth, int cellHeight) {
        cursorCol = clamp(cursorCol, 0, Math.max(0, cols - 1));
        cursorRow = clamp(cursorRow, 0, Math.max(0, rows - 1));

        int cx = x + PADDING_X + cursorCol * cellWidth;
        int cy = y + PADDING_Y + cursorRow * cellHeight;
        long blink = ((System.nanoTime() - startedAt) / 350_000_000L) % 2L;
        int body = blink == 0 ? 0xCC89B4FA : 0x8889B4FA;
        context.fill(cx, cy, cx + cellWidth, cy + cellHeight, body);
    }

    private void moveCursorTo(int localX, int localY, int width, int height) {
        int cellWidth = atlas.getCellWidth();
        int cellHeight = atlas.getCellHeight();
        cursorCol = clamp((localX - PADDING_X) / cellWidth, 0, Math.max(0, (width - PADDING_X * 2) / cellWidth - 1));
        cursorRow = clamp((localY - PADDING_Y) / cellHeight, 0, Math.max(0, (height - PADDING_Y * 2) / cellHeight - 1));
    }

    private String[] buildLines(int cols, int rows) {
        String[] lines = new String[Math.max(rows, 12)];
        lines[0] = padTo("  1  fn render_terminal(buffer: &Grid, viewport: Rect) {", cols);
        lines[1] = padTo("  2      draw_background_runs(buffer, viewport);", cols);
        lines[2] = padTo("  3      draw_glyph_runs(buffer, viewport);", cols);
        lines[3] = padTo("  4      draw_cursor(buffer.cursor());", cols);
        lines[4] = padTo("  5  }", cols);
        lines[6] = padTo("  7  let status = \"NORMAL  /src/render/term.rs\";", cols);
        lines[7] = padTo("  8  let note   = \"one quad per glyph, but batched\";", cols);
        lines[9] = padTo(" 10  ascii  !\"#$%&'()*+,-./0123456789:;<=>?", cols);
        lines[10] = padTo(" 11  alpha  abcdefghijklmnopqrstuvwxyz", cols);
        lines[11] = padTo(" 12  box    ─ │ ┌ ┐ └ ┘ ├ ┤ ┬ ┴ ┼", cols);

        for (int row = 0; row < lines.length; row++) {
            if (lines[row] == null) {
                lines[row] = "";
            }
        }

        if (rows > 0) {
            lines[Math.min(rows - 1, lines.length - 1)] = padTo(" NORMAL  glyph-atlas-demo", cols);
        }
        return lines;
    }

    private String padTo(String value, int cols) {
        if (value.length() >= cols) {
            return value.substring(0, cols);
        }
        return value;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
