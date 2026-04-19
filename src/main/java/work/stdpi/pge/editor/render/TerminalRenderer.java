package work.stdpi.pge.editor.render;

import com.jediterm.core.Color;
import com.jediterm.core.input.InputEvent;
import com.jediterm.core.input.KeyEvent;
import com.jediterm.core.input.MouseEvent;
import com.jediterm.core.typeahead.TerminalTypeAheadManager;
import com.jediterm.core.typeahead.TypeAheadTerminalModel;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TerminalDisplay;
import com.jediterm.terminal.TerminalExecutorServiceManager;
import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TtyBasedArrayDataStream;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.emulator.ColorPaletteImpl;
import com.jediterm.terminal.emulator.mouse.MouseButtonCodes;
import com.jediterm.terminal.emulator.mouse.MouseButtonModifierFlags;
import com.jediterm.terminal.emulator.mouse.MouseFormat;
import com.jediterm.terminal.emulator.mouse.MouseMode;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.model.TerminalTypeAheadSettings;
import com.jediterm.terminal.ui.JediTermExecutorServiceManager;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import net.minecraft.client.gui.DrawContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.stdpi.pge.editor.logic.EditorManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static com.jediterm.terminal.TextStyle.Option.BOLD;
import static com.jediterm.terminal.TextStyle.Option.HIDDEN;
import static com.jediterm.terminal.TextStyle.Option.INVERSE;

public class TerminalRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("pge-editor/native-terminal");
    private static final int PADDING_X = 0;
    private static final int PADDING_Y = 0;
    private static final int DEFAULT_BG = 0xFF0B0F14;
    private static final int DEFAULT_FG = 0xFFE6EDF3;
    private static final int CURSOR_ACCENT = 0xFF8AB4F8;

    private final MonoGlyphAtlas atlas = new MonoGlyphAtlas();
    private final ColorPalette palette = ColorPaletteImpl.XTERM_PALETTE;
    private final NativeDisplay display = new NativeDisplay();

    private TerminalTextBuffer textBuffer;
    private JediTerminal terminal;
    private TerminalStarter starter;
    private Process process;
    private TerminalExecutorServiceManager executorServiceManager;

    private int pixelWidth;
    private int pixelHeight;
    private int columns;
    private int rows;
    private int appliedCellWidthPx = -1;
    private EditorManager.TerminalFontWeight appliedFontWeight;
    private int activeMouseButton = MouseButtonCodes.NONE;
    private boolean pendingTerminalRefresh;
    private boolean initialized;

    public void render(DrawContext context, int x, int y, int width, int height) {
        syncAtlasSettings();
        ensureInitialized(width, height);
        resizeIfNeeded(width, height);

        context.fill(x, y, x + width, y + height, DEFAULT_BG);
        if (!initialized || textBuffer == null) {
            return;
        }

        textBuffer.lock();
        try {
            int cellWidth = atlas.getCellWidth();
            int cellHeight = atlas.getCellHeight();
            for (int row = 0; row < rows; row++) {
                drawRow(context, x, y, row, cellWidth, cellHeight);
            }
            drawCursor(context, x, y, cellWidth, cellHeight);
        } finally {
            textBuffer.unlock();
        }
    }

    public boolean onMouse(int localX, int localY, int button, int action, int mods, int width, int height) {
        ensureInitialized(width, height);
        resizeIfNeeded(width, height);

        int col = clamp((localX - PADDING_X) / atlas.getCellWidth(), 0, Math.max(0, columns - 1));
        int row = clamp((localY - PADDING_Y) / atlas.getCellHeight(), 0, Math.max(0, rows - 1));
        int modifiers = toMouseModifiers(mods);

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            int mappedButton = toMouseButton(button);
            if (action == GLFW.GLFW_PRESS) {
                activeMouseButton = mappedButton;
                terminal.mousePressed(col, row, new MouseEvent(mappedButton, modifiers));
            } else if (action == GLFW.GLFW_RELEASE) {
                terminal.mouseReleased(col, row, new MouseEvent(mappedButton, modifiers));
                if (activeMouseButton == mappedButton) {
                    activeMouseButton = MouseButtonCodes.NONE;
                }
            }
            return true;
        }
        return false;
    }

    public void onMove(int localX, int localY, int width, int height) {
        if (!initialized) {
            return;
        }
        resizeIfNeeded(width, height);

        int col = clamp((localX - PADDING_X) / atlas.getCellWidth(), 0, Math.max(0, columns - 1));
        int row = clamp((localY - PADDING_Y) / atlas.getCellHeight(), 0, Math.max(0, rows - 1));
        MouseEvent event = new MouseEvent(activeMouseButton, 0);
        if (activeMouseButton == MouseButtonCodes.NONE) {
            terminal.mouseMoved(col, row, event);
        } else {
            terminal.mouseDragged(col, row, event);
        }
    }

    public boolean onKey(int key, int action, int mods) {
        if (!initialized || action == GLFW.GLFW_RELEASE) {
            return false;
        }

        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) {
                EditorManager.INSTANCE.adjustTerminalCellWidthPx(1);
                syncAtlasSettings();
                resizeIfNeeded(pixelWidth, pixelHeight);
                return true;
            }
            if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) {
                EditorManager.INSTANCE.adjustTerminalCellWidthPx(-1);
                syncAtlasSettings();
                resizeIfNeeded(pixelWidth, pixelHeight);
                return true;
            }
        }

        int translatedKey = toTerminalKey(key);
        int translatedModifiers = toKeyModifiers(mods);

        if (translatedKey != -1) {
            byte[] bytes = terminal.getCodeForKey(translatedKey, translatedModifiers);
            if (bytes != null) {
                starter.sendBytes(bytes, true);
                return true;
            }
        }

        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0 && key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            char ctrlChar = (char) (key - GLFW.GLFW_KEY_A + 1);
            starter.sendBytes(new byte[]{(byte) ctrlChar}, true);
            return true;
        }

        return false;
    }

    public boolean onChar(int codepoint) {
        if (!initialized) {
            return false;
        }
        starter.sendString(new String(Character.toChars(codepoint)), true);
        return true;
    }

    public void focus() {
    }

    private void syncAtlasSettings() {
        int requestedCellWidth = EditorManager.INSTANCE.getTerminalCellWidthPx();
        if (requestedCellWidth != appliedCellWidthPx) {
            atlas.setCellWidthPx(requestedCellWidth);
            appliedCellWidthPx = requestedCellWidth;
            pendingTerminalRefresh = initialized;
        }
        EditorManager.TerminalFontWeight requestedFontWeight = EditorManager.INSTANCE.getTerminalFontWeight();
        if (requestedFontWeight != appliedFontWeight) {
            atlas.setFontWeight(requestedFontWeight);
            appliedFontWeight = requestedFontWeight;
        }
    }

    private void ensureInitialized(int width, int height) {
        if (initialized) {
            return;
        }

        pixelWidth = width;
        pixelHeight = height;
        columns = fitColumns(width);
        rows = fitRows(height);

        StyleState styleState = new StyleState();
        styleState.setDefaultStyle(new TextStyle(
            TerminalColor.rgb((DEFAULT_FG >> 16) & 0xFF, (DEFAULT_FG >> 8) & 0xFF, DEFAULT_FG & 0xFF),
            TerminalColor.rgb((DEFAULT_BG >> 16) & 0xFF, (DEFAULT_BG >> 8) & 0xFF, DEFAULT_BG & 0xFF)
        ));

        textBuffer = new TerminalTextBuffer(columns, rows, styleState);
        terminal = new JediTerminal(display, textBuffer, styleState);
        executorServiceManager = new JediTermExecutorServiceManager();

        try {
            ProcessTtyConnector connector = new ProcessTtyConnector(createProcessBuilder(columns, rows).start(), StandardCharsets.UTF_8) {
                @Override
                public String getName() {
                    return "PGE";
                }

                @Override
                public void resize(@NotNull TermSize termSize) {
                    Process ttyProcess = getProcess();
                    if (ttyProcess instanceof PtyProcess ptyProcess) {
                        ptyProcess.setWinSize(new WinSize(termSize.getColumns(), termSize.getRows()));
                    }
                }
            };
            process = connector.getProcess();
            NoOpTypeAheadModel typeAheadModel = new NoOpTypeAheadModel(terminal, textBuffer);
            TerminalTypeAheadManager typeAheadManager = new TerminalTypeAheadManager(typeAheadModel);
            starter = new TerminalStarter(
                terminal,
                connector,
                new TtyBasedArrayDataStream(connector, typeAheadManager::onTerminalStateChanged),
                typeAheadManager,
                executorServiceManager
            );
            executorServiceManager.getUnboundedExecutorService().submit(starter::start);
            initialized = true;
            LOGGER.info("initialized native terminal {}x{} cells for {}x{} px", columns, rows, width, height);
        } catch (IOException e) {
            LOGGER.error("failed to start native terminal backend", e);
        }
    }

    private void resizeIfNeeded(int width, int height) {
        if (!initialized) {
            return;
        }

        int newColumns = fitColumns(width);
        int newRows = fitRows(height);
        if (width == pixelWidth && height == pixelHeight && newColumns == columns && newRows == rows && !pendingTerminalRefresh) {
            return;
        }

        pixelWidth = width;
        pixelHeight = height;
        columns = newColumns;
        rows = newRows;
        starter.postResize(new TermSize(columns, rows), RequestOrigin.User);
        pendingTerminalRefresh = false;
        LOGGER.info("resized native terminal to {}x{} cells for {}x{} px", columns, rows, width, height);
    }

    private PtyProcessBuilder createProcessBuilder(int columns, int rows) {
        var env = new HashMap<>(System.getenv());
        env.put("TERM", "xterm-256color");
        env.put("COLORTERM", "truecolor");
        env.put("TERM_PROGRAM", "pge-editor");
        env.put("TERM_PROGRAM_VERSION", "dev");

        return new PtyProcessBuilder(buildShellCommand())
            .setDirectory(System.getProperty("user.home"))
            .setEnvironment(env)
            .setInitialColumns(columns)
            .setInitialRows(rows);
    }

    private String[] buildShellCommand() {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return new String[]{"cmd.exe"};
        }

        String shell = System.getenv("SHELL");
        if (shell != null && !shell.isBlank()) {
            return new String[]{shell, "-i"};
        }
        return new String[]{"/bin/bash", "--login"};
    }

    private void drawRow(DrawContext context, int x, int y, int row, int cellWidth, int cellHeight) {
        int startCol = 0;
        int runForeground = DEFAULT_FG;
        int runBackground = DEFAULT_BG;
        StringBuilder builder = new StringBuilder(columns);

        for (int col = 0; col < columns; col++) {
            char ch = normalizeGlyph(textBuffer.getCharAt(col, row));
            CellStyle style = resolveStyle(textBuffer.getStyleAt(col, row));

            if (builder.isEmpty()) {
                startCol = col;
                runForeground = style.foreground();
                runBackground = style.background();
            } else if (runForeground != style.foreground() || runBackground != style.background()) {
                drawRun(context, x, y, row, startCol, builder.toString(), runForeground, runBackground, cellWidth, cellHeight);
                builder.setLength(0);
                startCol = col;
                runForeground = style.foreground();
                runBackground = style.background();
            }

            builder.append(ch);
        }

        if (!builder.isEmpty()) {
            drawRun(context, x, y, row, startCol, builder.toString(), runForeground, runBackground, cellWidth, cellHeight);
        }
    }

    private void drawRun(DrawContext context, int x, int y, int row, int startCol, String text, int foreground, int background, int cellWidth, int cellHeight) {
        int drawX = x + PADDING_X + startCol * cellWidth;
        int drawY = y + PADDING_Y + row * cellHeight;
        context.fill(drawX, drawY, drawX + text.length() * cellWidth, drawY + cellHeight, background);

        int visibleLength = trimTrailingSpaces(text);
        if (visibleLength > 0) {
            atlas.drawText(context, text.substring(0, visibleLength), drawX, drawY, foreground);
        }
    }

    private void drawCursor(DrawContext context, int x, int y, int cellWidth, int cellHeight) {
        if (!display.cursorVisible || rows <= 0 || columns <= 0) {
            return;
        }

        CursorShape shape = display.cursorShape != null ? display.cursorShape : CursorShape.STEADY_BLOCK;
        if (shape.isBlinking() && ((System.nanoTime() / 350_000_000L) % 2L) == 0L) {
            return;
        }

        int cursorCol = clamp(display.cursorX, 0, Math.max(0, columns - 1));
        int cursorRow = clamp(display.cursorY - 1, 0, Math.max(0, rows - 1));
        int drawX = x + PADDING_X + cursorCol * cellWidth;
        int drawY = y + PADDING_Y + cursorRow * cellHeight;

        switch (shape) {
            case BLINK_VERTICAL_BAR, STEADY_VERTICAL_BAR -> {
                int barWidth = Math.max(1, cellWidth / 6);
                context.fill(drawX, drawY, drawX + barWidth, drawY + cellHeight, CURSOR_ACCENT);
            }
            case BLINK_UNDERLINE, STEADY_UNDERLINE -> context.fill(drawX, drawY + cellHeight - 2, drawX + cellWidth, drawY + cellHeight, CURSOR_ACCENT);
            case BLINK_BLOCK, STEADY_BLOCK -> {
                char glyph = normalizeGlyph(textBuffer.getCharAt(cursorCol, cursorRow));
                CellStyle style = resolveStyle(textBuffer.getStyleAt(cursorCol, cursorRow));
                context.fill(drawX, drawY, drawX + cellWidth, drawY + cellHeight, CURSOR_ACCENT);
                if (glyph != ' ') {
                    atlas.drawText(context, String.valueOf(glyph), drawX, drawY, style.background());
                }
            }
        }
    }

    private CellStyle resolveStyle(@Nullable TextStyle style) {
        int foreground = DEFAULT_FG;
        int background = DEFAULT_BG;

        if (style != null) {
            TerminalColor fgColor = style.getForeground();
            TerminalColor bgColor = style.getBackground();

            if (style.hasOption(BOLD) && fgColor != null && fgColor.isIndexed() && fgColor.getColorIndex() < 8) {
                fgColor = TerminalColor.index(fgColor.getColorIndex() + 8);
            }

            if (fgColor != null) {
                foreground = toArgb(palette.getForeground(fgColor));
            }
            if (bgColor != null) {
                background = toArgb(palette.getBackground(bgColor));
            }
            if (style.hasOption(INVERSE)) {
                int tmp = foreground;
                foreground = background;
                background = tmp;
            }
            if (style.hasOption(HIDDEN)) {
                foreground = background;
            }
        }

        return new CellStyle(foreground, background);
    }

    private char normalizeGlyph(char ch) {
        if (ch == 0 || ch == '\uE000' || Character.isISOControl(ch)) {
            return ' ';
        }
        return ch;
    }

    private int trimTrailingSpaces(String text) {
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == ' ') {
            end--;
        }
        return end;
    }

    private int fitColumns(int width) {
        return Math.max(5, (width - PADDING_X * 2) / atlas.getCellWidth());
    }

    private int fitRows(int height) {
        return Math.max(2, (height - PADDING_Y * 2) / atlas.getCellHeight());
    }

    private int toMouseButton(int glfwButton) {
        return switch (glfwButton) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> MouseButtonCodes.LEFT;
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> MouseButtonCodes.MIDDLE;
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> MouseButtonCodes.RIGHT;
            default -> MouseButtonCodes.NONE;
        };
    }

    private int toMouseModifiers(int mods) {
        int result = 0;
        if ((mods & GLFW.GLFW_MOD_SHIFT) != 0) {
            result |= MouseButtonModifierFlags.MOUSE_BUTTON_SHIFT_FLAG;
        }
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) {
            result |= MouseButtonModifierFlags.MOUSE_BUTTON_CTRL_FLAG;
        }
        if ((mods & GLFW.GLFW_MOD_ALT) != 0) {
            result |= MouseButtonModifierFlags.MOUSE_BUTTON_META_FLAG;
        }
        return result;
    }

    private int toKeyModifiers(int mods) {
        int result = 0;
        if ((mods & GLFW.GLFW_MOD_SHIFT) != 0) {
            result |= InputEvent.SHIFT_MASK;
        }
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) {
            result |= InputEvent.CTRL_MASK;
        }
        if ((mods & GLFW.GLFW_MOD_ALT) != 0) {
            result |= InputEvent.ALT_MASK;
        }
        if ((mods & GLFW.GLFW_MOD_SUPER) != 0) {
            result |= InputEvent.META_MASK;
        }
        return result;
    }

    private int toTerminalKey(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> KeyEvent.VK_ENTER;
            case GLFW.GLFW_KEY_BACKSPACE -> KeyEvent.VK_BACK_SPACE;
            case GLFW.GLFW_KEY_TAB -> KeyEvent.VK_TAB;
            case GLFW.GLFW_KEY_ESCAPE -> KeyEvent.VK_ESCAPE;
            case GLFW.GLFW_KEY_UP -> KeyEvent.VK_UP;
            case GLFW.GLFW_KEY_DOWN -> KeyEvent.VK_DOWN;
            case GLFW.GLFW_KEY_LEFT -> KeyEvent.VK_LEFT;
            case GLFW.GLFW_KEY_RIGHT -> KeyEvent.VK_RIGHT;
            case GLFW.GLFW_KEY_HOME -> KeyEvent.VK_HOME;
            case GLFW.GLFW_KEY_END -> KeyEvent.VK_END;
            case GLFW.GLFW_KEY_PAGE_UP -> KeyEvent.VK_PAGE_UP;
            case GLFW.GLFW_KEY_PAGE_DOWN -> KeyEvent.VK_PAGE_DOWN;
            case GLFW.GLFW_KEY_INSERT -> KeyEvent.VK_INSERT;
            case GLFW.GLFW_KEY_DELETE -> KeyEvent.VK_DELETE;
            case GLFW.GLFW_KEY_F1 -> KeyEvent.VK_F1;
            case GLFW.GLFW_KEY_F2 -> KeyEvent.VK_F2;
            case GLFW.GLFW_KEY_F3 -> KeyEvent.VK_F3;
            case GLFW.GLFW_KEY_F4 -> KeyEvent.VK_F4;
            case GLFW.GLFW_KEY_F5 -> KeyEvent.VK_F5;
            case GLFW.GLFW_KEY_F6 -> KeyEvent.VK_F6;
            case GLFW.GLFW_KEY_F7 -> KeyEvent.VK_F7;
            case GLFW.GLFW_KEY_F8 -> KeyEvent.VK_F8;
            case GLFW.GLFW_KEY_F9 -> KeyEvent.VK_F9;
            case GLFW.GLFW_KEY_F10 -> KeyEvent.VK_F10;
            case GLFW.GLFW_KEY_F11 -> KeyEvent.VK_F11;
            case GLFW.GLFW_KEY_F12 -> KeyEvent.VK_F12;
            default -> -1;
        };
    }

    private int toArgb(@NotNull Color color) {
        return color.getRGB();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record CellStyle(int foreground, int background) {}

    private static final class NativeDisplay implements TerminalDisplay {
        private int cursorX;
        private int cursorY = 1;
        private boolean cursorVisible = true;
        private @Nullable CursorShape cursorShape;
        private @NotNull String title = "PGE";
        private @NotNull MouseMode mouseMode = MouseMode.MOUSE_REPORTING_NONE;
        private @NotNull MouseFormat mouseFormat = MouseFormat.MOUSE_FORMAT_XTERM;

        @Override
        public void setCursor(int x, int y) {
            cursorX = x;
            cursorY = y;
        }

        @Override
        public void setCursorShape(@Nullable CursorShape cursorShape) {
            this.cursorShape = cursorShape;
        }

        @Override
        public void beep() {
        }

        @Override
        public void scrollArea(int scrollRegionTop, int scrollRegionSize, int dy) {
        }

        @Override
        public void setCursorVisible(boolean isCursorVisible) {
            cursorVisible = isCursorVisible;
        }

        @Override
        public void useAlternateScreenBuffer(boolean useAlternateScreenBuffer) {
        }

        @Override
        public String getWindowTitle() {
            return title;
        }

        @Override
        public void setWindowTitle(@NotNull String windowTitle) {
            title = windowTitle;
        }

        @Override
        public @Nullable com.jediterm.terminal.model.TerminalSelection getSelection() {
            return null;
        }

        @Override
        public void terminalMouseModeSet(@NotNull MouseMode mouseMode) {
            this.mouseMode = mouseMode;
        }

        @Override
        public void setMouseFormat(@NotNull MouseFormat mouseFormat) {
            this.mouseFormat = mouseFormat;
        }

        @Override
        public boolean ambiguousCharsAreDoubleWidth() {
            return false;
        }
    }

    private static final class NoOpTypeAheadModel implements TypeAheadTerminalModel {
        private final JediTerminal terminal;
        private final TerminalTextBuffer buffer;

        private NoOpTypeAheadModel(JediTerminal terminal, TerminalTextBuffer buffer) {
            this.terminal = terminal;
            this.buffer = buffer;
        }

        @Override
        public void insertCharacter(char ch, int index) {
        }

        @Override
        public void removeCharacters(int from, int count) {
        }

        @Override
        public void moveCursor(int index) {
        }

        @Override
        public void forceRedraw() {
        }

        @Override
        public void clearPredictions() {
        }

        @Override
        public void lock() {
            buffer.lock();
        }

        @Override
        public void unlock() {
            buffer.unlock();
        }

        @Override
        public boolean isUsingAlternateBuffer() {
            return buffer.isUsingAlternateBuffer();
        }

        @Override
        public @NotNull LineWithCursorX getCurrentLineWithCursor() {
            return new LineWithCursorX(new StringBuffer(buffer.getLine(terminal.getCursorY() - 1).getText()), terminal.getCursorX() - 1);
        }

        @Override
        public int getTerminalWidth() {
            return terminal.getTerminalWidth();
        }

        @Override
        public boolean isTypeAheadEnabled() {
            return false;
        }

        @Override
        public long getLatencyThreshold() {
            return TerminalTypeAheadSettings.DEFAULT.getLatencyThreshold();
        }

        @Override
        public ShellType getShellType() {
            return ShellType.Unknown;
        }
    }
}
