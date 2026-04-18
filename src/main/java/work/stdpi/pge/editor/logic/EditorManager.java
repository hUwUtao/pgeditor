package work.stdpi.pge.editor.logic;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class EditorManager {
    public static final EditorManager INSTANCE = new EditorManager();

    public enum DockSide { LEFT, TOP, RIGHT, BOTTOM }

    private DockSide side = DockSide.RIGHT;
    private float percent = 0.3f;
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
    public float getPercent() { return percent; }
    public void setPercent(float p) { this.percent = p; if (enabled) update(); }
}
