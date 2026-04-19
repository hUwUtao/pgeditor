package work.stdpi.pge.editor.logic;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import work.stdpi.pge.editor.config.EditorConfig;

public class EditorManager {
    public static final EditorManager INSTANCE = new EditorManager();

    public enum DockSide { LEFT, TOP, RIGHT, BOTTOM }
    public enum RenderMode { TERMINAL, GIZMO_GRID, MINIGAME }
    public enum TerminalFontWeight { LIGHT, REGULAR, MEDIUM, BOLD }

    private DockSide side = DockSide.RIGHT;
    private RenderMode renderMode = RenderMode.TERMINAL;
    private float percent = 0.52f;
    private int terminalCellWidthPx = 20;
    private TerminalFontWeight terminalFontWeight = TerminalFontWeight.REGULAR;
    private boolean enabled = false;
    private KeyBinding toggleKey;
    private boolean configLoaded;

    private EditorManager() {}

    public void init() {
        loadConfig();
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.pge-editor.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_BACKSLASH,
            KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                // Ctrl + Toggle = Cycle Side
                if (GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                    GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS) {
                    cycleSide();
                } else if (GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS ||
                    GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS) {
                    cycleRenderMode();
                } else {
                    setEnabled(!enabled);
                }
            }
            if (enabled) update();
        });
    }

    private void cycleSide() {
        int next = (side.ordinal() + 1) % DockSide.values().length;
        side = DockSide.values()[next];
        saveConfig();
        if (enabled) update();
    }

    private void cycleRenderMode() {
        int next = (renderMode.ordinal() + 1) % RenderMode.values().length;
        renderMode = RenderMode.values()[next];
        saveConfig();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        MinecraftClient client = MinecraftClient.getInstance();
        if (enabled) {
            update();
            ViewportController.INSTANCE.setActive(true);
            client.mouse.unlockCursor();
        } else {
            ViewportController.INSTANCE.setActive(false);
            client.mouse.lockCursor();
        }
        client.onResolutionChanged();
    }

    public void update() {
        ViewportController.INSTANCE.calculate(MinecraftClient.getInstance().getWindow(), side, percent);
    }

    public boolean isEnabled() { return enabled; }
    public DockSide getSide() { return side; }
    public RenderMode getRenderMode() { return renderMode; }
    public float getPercent() { return percent; }
    public void setPercent(float p) {
        this.percent = p;
        saveConfig();
        if (enabled) update();
    }
    public int getTerminalCellWidthPx() { return terminalCellWidthPx; }
    public void adjustTerminalCellWidthPx(int delta) {
        setTerminalCellWidthPx(terminalCellWidthPx + delta);
    }
    public void setTerminalCellWidthPx(int pixels) {
        terminalCellWidthPx = Math.max(1, Math.min(24, pixels));
        saveConfig();
    }
    public TerminalFontWeight getTerminalFontWeight() {
        return terminalFontWeight;
    }
    public void setTerminalFontWeight(TerminalFontWeight fontWeight) {
        terminalFontWeight = fontWeight != null ? fontWeight : TerminalFontWeight.REGULAR;
        saveConfig();
    }

    private void loadConfig() {
        EditorConfig.Data data = EditorConfig.INSTANCE.load();
        side = parseEnum(data.dockSide(), DockSide.RIGHT);
        renderMode = parseEnum(data.renderMode(), RenderMode.TERMINAL);
        percent = Math.max(0.2f, Math.min(0.8f, data.dockPercent()));
        terminalCellWidthPx = Math.max(1, Math.min(24, data.terminalCellWidthPx()));
        terminalFontWeight = parseEnum(data.terminalFontWeight(), TerminalFontWeight.REGULAR);
        configLoaded = true;
    }

    private void saveConfig() {
        if (!configLoaded) {
            return;
        }
        EditorConfig.INSTANCE.save(new EditorConfig.Data(
            side.name(),
            renderMode.name(),
            percent,
            terminalCellWidthPx,
            terminalFontWeight.name()
        ));
    }

    private static <T extends Enum<T>> T parseEnum(String raw, T fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), raw);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
