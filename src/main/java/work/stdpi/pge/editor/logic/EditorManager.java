package work.stdpi.pge.editor.logic;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class EditorManager {
    public static final EditorManager INSTANCE = new EditorManager();

    public enum DockSide { LEFT, TOP, RIGHT, BOTTOM }
    public enum RenderMode { TERMINAL, GIZMO_GRID, MINIGAME }

    private DockSide side = DockSide.RIGHT;
    private RenderMode renderMode = RenderMode.TERMINAL;
    private float percent = 0.52f;
    private int terminalFontSize = 8;
    private int terminalCellScalePercent = 100;
    private boolean enabled = false;
    private KeyBinding toggleKey;

    private EditorManager() {}

    public void init() {
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
        if (enabled) update();
    }

    private void cycleRenderMode() {
        int next = (renderMode.ordinal() + 1) % RenderMode.values().length;
        renderMode = RenderMode.values()[next];
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
    public void setPercent(float p) { this.percent = p; if (enabled) update(); }
    public int getTerminalFontSize() { return terminalFontSize; }
    public void adjustTerminalFontSize(int delta) {
        terminalFontSize = Math.max(6, Math.min(14, terminalFontSize + delta));
    }
    public int getTerminalCellScalePercent() { return terminalCellScalePercent; }
    public void setTerminalCellScalePercent(int percent) {
        terminalCellScalePercent = Math.max(70, Math.min(130, percent));
    }
}
