package work.stdpi.pge.editor.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.stdpi.pge.editor.logic.EditorManager;
import work.stdpi.pge.editor.logic.ViewportController;
import work.stdpi.pge.editor.WindowAccessor;

public class EditorUI {
    public static final EditorUI INSTANCE = new EditorUI();
    private static final Logger LOGGER = LoggerFactory.getLogger("pge-editor/ui");
    private final TerminalRenderer term = new TerminalRenderer();
    private final GlyphGridRenderer glyphGrid = new GlyphGridRenderer();
    private final MiniGameRenderer miniGame = new MiniGameRenderer();
    private boolean regionLogged = false;
    private boolean focused = false;
    private boolean windowFocused = true;
    private int ex, ey, ew, eh;

    public void render(DrawContext context) {
        if (!EditorManager.INSTANCE.isEnabled()) return;
        var mc = MinecraftClient.getInstance();
        var win = mc.getWindow();
        
        int rsw = ((WindowAccessor) (Object) win).pge$getRealScaledWidth();
        int rsh = ((WindowAccessor) (Object) win).pge$getRealScaledHeight();

        // EDITOR zone
        int gx = ViewportController.INSTANCE.getX();
        int gy = ViewportController.INSTANCE.getY();
        int gw = ViewportController.INSTANCE.getWidth();
        int gh = ViewportController.INSTANCE.getHeight();

        switch (EditorManager.INSTANCE.getSide()) {
            case LEFT ->  { ex = 0; ey = 0; ew = gx; eh = rsh; }
            case RIGHT -> { ex = gx + gw; ey = 0; ew = rsw - ex; eh = rsh; }
            case TOP ->   { ex = 0; ey = 0; ew = rsw; eh = gy; }
            case BOTTOM ->{ ex = 0; ey = gy + gh; ew = rsw; eh = rsh - ey; }
        }

        if (ew <= 0 || eh <= 0) return;

        if (!regionLogged) {
            LOGGER.info(
                "editor region side={} editor=({}, {}) {}x{} viewport=({}, {}) {}x{} screen={}x{}",
                EditorManager.INSTANCE.getSide(),
                ex, ey, ew, eh,
                gx, gy, gw, gh,
                rsw, rsh
            );
            regionLogged = true;
        }

        boolean nowWindowFocused = GLFW.glfwGetWindowAttrib(win.getHandle(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
        if (nowWindowFocused && !windowFocused && focused) {
            LOGGER.info("minecraft window focus restored while terminal focused");
            term.focus();
        }
        windowFocused = nowWindowFocused;

        float liedScaleX = (float) win.getScaledWidth() / rsw;
        float liedScaleY = (float) win.getScaledHeight() / rsh;
        
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(liedScaleX, liedScaleY);
        
        context.fill(ex, ey, ex + ew, ey + eh, 0xFF1E1E1E);

        if (EditorManager.INSTANCE.getRenderMode() == EditorManager.RenderMode.TERMINAL) {
            term.render(context, ex, ey, ew, eh);
        } else if (EditorManager.INSTANCE.getRenderMode() == EditorManager.RenderMode.GIZMO_GRID) {
            glyphGrid.render(context, ex, ey, ew, eh);
        } else {
            miniGame.render(context, ex, ey, ew, eh);
        }

        context.getMatrices().popMatrix();
    }

    public boolean onMouse(int b, int a, int m) {
        if (!EditorManager.INSTANCE.isEnabled()) return false;
        var mc = MinecraftClient.getInstance();
        var win = mc.getWindow();
        double s = (double) ((WindowAccessor)(Object)win).pge$getRealFramebufferWidth() / ((WindowAccessor)(Object)win).pge$getRealScaledWidth();
        int mx = (int)(mc.mouse.getX() / s), my = (int)(mc.mouse.getY() / s);
        
        if (mx >= ex && mx < ex + ew && my >= ey && my < ey + eh) {
            focused = true;
            if (EditorManager.INSTANCE.getRenderMode() == EditorManager.RenderMode.TERMINAL) {
                LOGGER.info("terminal mouse focus acquired at {},{}", mx - ex, my - ey);
                term.focus();
                term.onMouse(mx - ex, my - ey, b, a, m, ew, eh);
            } else if (EditorManager.INSTANCE.getRenderMode() == EditorManager.RenderMode.GIZMO_GRID) {
                glyphGrid.onMouse(mx - ex, my - ey, b, a, m, ew, eh);
            } else {
                miniGame.onMouse(mx - ex, my - ey, b, a, m, ew, eh);
            }
            return true;
        }
        if (focused) {
            LOGGER.info("terminal mouse focus lost");
        }
        focused = false;
        return false;
    }

    public void onMove(double x, double y) {
        if (!EditorManager.INSTANCE.isEnabled()) return;
        var mc = MinecraftClient.getInstance();
        var win = mc.getWindow();
        double s = (double) ((WindowAccessor)(Object)win).pge$getRealFramebufferWidth() / ((WindowAccessor)(Object)win).pge$getRealScaledWidth();
        int mx = (int)(x / s);
        int my = (int)(y / s);
        if (mx >= ex && mx < ex + ew && my >= ey && my < ey + eh) {
            if (EditorManager.INSTANCE.getRenderMode() == EditorManager.RenderMode.TERMINAL) {
                term.onMove(mx - ex, my - ey, ew, eh);
            } else if (EditorManager.INSTANCE.getRenderMode() == EditorManager.RenderMode.GIZMO_GRID) {
                glyphGrid.onMove(mx - ex, my - ey, ew, eh);
            } else {
                miniGame.onMove(mx - ex, my - ey, ew, eh);
            }
        }
    }

    public boolean onKey(int k, int a, int m) {
        if (!EditorManager.INSTANCE.isEnabled()) return false;
        if (k == GLFW.GLFW_KEY_BACKSLASH) return false;
        boolean terminalGameplayCapture =
            EditorManager.INSTANCE.getRenderMode() == EditorManager.RenderMode.TERMINAL
                && MinecraftClient.getInstance().currentScreen == null;
        if (focused || terminalGameplayCapture) {
            if (EditorManager.INSTANCE.getRenderMode() == EditorManager.RenderMode.TERMINAL) {
                LOGGER.info("terminal key key={} action={} mods={}", k, a, m);
                term.onKey(k, a, m);
                return true;
            } else if (EditorManager.INSTANCE.getRenderMode() == EditorManager.RenderMode.GIZMO_GRID) {
                return glyphGrid.onKey(k, a);
            } else {
                return miniGame.onKey(k, a);
            }
        }
        return false;
    }

    public boolean onChar(int c) {
        boolean terminalGameplayCapture =
            EditorManager.INSTANCE.isEnabled()
                && EditorManager.INSTANCE.getRenderMode() == EditorManager.RenderMode.TERMINAL
                && MinecraftClient.getInstance().currentScreen == null;
        if (EditorManager.INSTANCE.isEnabled()
            && EditorManager.INSTANCE.getRenderMode() == EditorManager.RenderMode.TERMINAL
            && (focused || terminalGameplayCapture)) {
            LOGGER.info("terminal char codepoint={}", c);
            term.onChar(c);
            return true;
        }
        return false;
    }
}
