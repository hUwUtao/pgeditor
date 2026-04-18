package work.stdpi.pge.editor.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import work.stdpi.pge.editor.logic.EditorManager;
import work.stdpi.pge.editor.logic.ViewportController;
import work.stdpi.pge.editor.logic.InputDispatcher;
import work.stdpi.pge.editor.WindowAccessor;

public class EditorUI {
    public static final EditorUI INSTANCE = new EditorUI();
    private static final Identifier TERM_TEXTURE_ID = Identifier.of("pge-editor", "term");
    private final TerminalRenderer term = new TerminalRenderer();
    private boolean init = false;
    private boolean focused = false;
    private boolean windowFocused = true;
    private int ex, ey, ew, eh;
    private NativeImageBackedTexture registeredTexture;

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

        boolean nowWindowFocused = GLFW.glfwGetWindowAttrib(win.getHandle(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
        if (nowWindowFocused && !windowFocused && focused) {
            term.focus();
        }
        windowFocused = nowWindowFocused;

        float liedScaleX = (float) win.getScaledWidth() / rsw;
        float liedScaleY = (float) win.getScaledHeight() / rsh;
        
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(liedScaleX, liedScaleY);
        
        // 1. Fill background
        context.fill(ex, ey, ex + ew, ey + eh, 0xFF1E1E1E);

        if (!init) { term.init(ew, eh); init = true; } else { term.resize(ew, eh); }
        term.update();

        var tex = term.getTexture();
        if (tex != null) {
            if (registeredTexture != tex) {
                mc.getTextureManager().registerTexture(TERM_TEXTURE_ID, tex);
                registeredTexture = tex;
            }
            context.drawTexturedQuad(TERM_TEXTURE_ID, ex, ex + ew, ey, ey + eh, 0f, 1f, 0f, 1f);
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
            term.focus();
            InputDispatcher.dispatchMouse(term.getInputTarget(), mx - ex, my - ey, b, a, m);
            return true;
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
            InputDispatcher.dispatchMove(term.getInputTarget(), mx - ex, my - ey);
        }
    }

    public boolean onKey(int k, int a, int m) {
        if (!EditorManager.INSTANCE.isEnabled()) return false;
        if (k == GLFW.GLFW_KEY_BACKSLASH) return false;
        if (focused) {
            InputDispatcher.dispatchKey(term.getInputTarget(), k, a, m);
            return true;
        }
        return false;
    }

    public boolean onChar(int c) {
        if (EditorManager.INSTANCE.isEnabled() && focused) {
            InputDispatcher.dispatchChar(term.getInputTarget(), c);
            return true;
        }
        return false;
    }
}
