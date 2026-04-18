package work.stdpi.pge.editor.logic;

import org.lwjgl.glfw.GLFW;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;

public class InputDispatcher {
    public static void dispatchMouse(Component widget, int x, int y, int button, int action, int mods) {
        int type = (action == GLFW.GLFW_PRESS) ? MouseEvent.MOUSE_PRESSED : MouseEvent.MOUSE_RELEASED;
        int awtBtn = switch (button) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> MouseEvent.BUTTON1;
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> MouseEvent.BUTTON3;
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> MouseEvent.BUTTON2;
            default -> MouseEvent.NOBUTTON;
        };

        MouseEvent ev = new MouseEvent(widget, type, System.currentTimeMillis(), getMods(mods), x, y, 1, false, awtBtn);
        SwingUtilities.invokeLater(() -> {
            widget.dispatchEvent(ev);
            if (action == GLFW.GLFW_RELEASE) {
                widget.dispatchEvent(new MouseEvent(widget, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), getMods(mods), x, y, 1, false, awtBtn));
            }
        });
    }

    public static void dispatchMove(Component widget, int x, int y) {
        MouseEvent ev = new MouseEvent(widget, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, x, y, 0, false, MouseEvent.NOBUTTON);
        SwingUtilities.invokeLater(() -> widget.dispatchEvent(ev));
    }

    public static void dispatchKey(Component widget, int key, int action, int mods) {
        int type = (action == GLFW.GLFW_RELEASE) ? KeyEvent.KEY_RELEASED : KeyEvent.KEY_PRESSED;
        int awtKey = translate(key);
        if (awtKey == KeyEvent.VK_UNDEFINED) return;
        KeyEvent ev = new KeyEvent(widget, type, System.currentTimeMillis(), getMods(mods), awtKey, KeyEvent.CHAR_UNDEFINED);
        SwingUtilities.invokeLater(() -> widget.dispatchEvent(ev));
    }

    public static void dispatchChar(Component widget, int codepoint) {
        KeyEvent ev = new KeyEvent(widget, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, (char) codepoint);
        SwingUtilities.invokeLater(() -> widget.dispatchEvent(ev));
    }

    private static int getMods(int mods) {
        int m = 0;
        if ((mods & GLFW.GLFW_MOD_SHIFT) != 0) m |= KeyEvent.SHIFT_DOWN_MASK;
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) m |= KeyEvent.CTRL_DOWN_MASK;
        if ((mods & GLFW.GLFW_MOD_ALT) != 0) m |= KeyEvent.ALT_DOWN_MASK;
        return m;
    }

    private static int translate(int k) {
        return switch (k) {
            case GLFW.GLFW_KEY_ENTER -> KeyEvent.VK_ENTER;
            case GLFW.GLFW_KEY_BACKSPACE -> KeyEvent.VK_BACK_SPACE;
            case GLFW.GLFW_KEY_TAB -> KeyEvent.VK_TAB;
            case GLFW.GLFW_KEY_ESCAPE -> KeyEvent.VK_ESCAPE;
            case GLFW.GLFW_KEY_UP -> KeyEvent.VK_UP;
            case GLFW.GLFW_KEY_DOWN -> KeyEvent.VK_DOWN;
            case GLFW.GLFW_KEY_LEFT -> KeyEvent.VK_LEFT;
            case GLFW.GLFW_KEY_RIGHT -> KeyEvent.VK_RIGHT;
            default -> (k >= GLFW.GLFW_KEY_A && k <= GLFW.GLFW_KEY_Z) || (k >= GLFW.GLFW_KEY_0 && k <= GLFW.GLFW_KEY_9) ? k : KeyEvent.VK_UNDEFINED;
        };
    }
}
