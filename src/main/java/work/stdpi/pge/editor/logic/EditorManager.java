package work.stdpi.pge.editor.logic;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class EditorManager {
    public static final EditorManager INSTANCE = new EditorManager();

    public enum DockSide { LEFT, RIGHT, TOP, BOTTOM }

    private DockSide side = DockSide.LEFT;
    private float percent = 0.3f;
    private boolean enabled = false;
    private KeyBinding toggleKey;

    private EditorManager() {}

    public void init() {
        // In 1.21.11, the constructor changed to take KeyBinding.Category instead of String
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.pge-editor.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_BACKSLASH,
            KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                setEnabled(!enabled);
            }
            if (enabled) update();
        });
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        ViewportController.INSTANCE.setActive(enabled);
        MinecraftClient client = MinecraftClient.getInstance();
        if (enabled) {
            update();
            client.mouse.unlockCursor();
        } else {
            client.mouse.lockCursor();
        }
        client.onResolutionChanged();
    }

    public void update() {
        ViewportController.INSTANCE.calculate(MinecraftClient.getInstance().getWindow(), side, percent);
    }

    public boolean isEnabled() { return enabled; }
    public DockSide getSide() { return side; }
}
