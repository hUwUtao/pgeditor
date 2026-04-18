package work.stdpi.pge.editor.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import work.stdpi.pge.editor.logic.EditorManager;
import work.stdpi.pge.editor.logic.ViewportController;
import work.stdpi.pge.editor.logic.InputDispatcher;
import work.stdpi.pge.editor.WindowAccessor;

public class EditorUI {
    public static final EditorUI INSTANCE = new EditorUI();
    private final TerminalRenderer term = new TerminalRenderer();
    private boolean init = false;
    private boolean focused = false;
    private int ex, ey, ew, eh;

    public void render(DrawContext context) {
        if (!EditorManager.INSTANCE.isEnabled()) return;
        var mc = MinecraftClient.getInstance();
        var win = mc.getWindow();
        
        int rsw = ((WindowAccessor) (Object) win).pge$getRealScaledWidth();
        int rsh = ((WindowAccessor) (Object) win).pge$getRealScaledHeight();
        int lsw = win.getScaledWidth();
        int lsh = win.getScaledHeight();

        // Calculate Editor Rect in REAL scaled coordinates
        switch (EditorManager.INSTANCE.getSide()) {
            case LEFT ->  { ex = 0; ey = 0; ew = ViewportController.INSTANCE.getX(); eh = rsh; }
            case RIGHT -> { ex = ViewportController.INSTANCE.getWidth(); ey = 0; ew = rsw - ViewportController.INSTANCE.getWidth(); eh = rsh; }
            case TOP ->   { ex = 0; ey = 0; ew = rsw; eh = ViewportController.INSTANCE.getY(); }
            case BOTTOM ->{ ex = 0; ey = ViewportController.INSTANCE.getHeight(); ew = rsw; eh = rsh - ViewportController.INSTANCE.getHeight(); }
        }

        if (ew <= 0 || eh <= 0) return;

        context.getMatrices().pushMatrix();
        // Counteract the game's lied-about projection matrix
        float invScaleX = (float) lsw / rsw;
        float invScaleY = (float) lsh / rsh;
        context.getMatrices().scale(invScaleX, invScaleY);

        // 1. Background
        context.fill(ex, ey, ex + ew, ey + eh, 0xFF1E1E1E);

        if (!init) { term.init(ew, eh); init = true; } else { term.resize(ew, eh); }
        term.update();

        var tex = term.getTexture();
        if (tex != null) {
            var id = Identifier.of("pge-editor", "term");
            mc.getTextureManager().registerTexture(id, tex);
            context.drawTexturedQuad(id, ex, ex + ew, ey, ey + eh, 0f, 1f, 0f, 1f);
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
            InputDispatcher.dispatchMouse(term.getWidget(), mx - ex, my - ey, b, a, m);
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
        InputDispatcher.dispatchMove(term.getWidget(), (int)(x / s) - ex, (int)(y / s) - ey);
    }

    public boolean onKey(int k, int a, int m) {
        if (!EditorManager.INSTANCE.isEnabled()) return false;
        if (k == GLFW.GLFW_KEY_BACKSLASH) return false;
        if (focused) {
            InputDispatcher.dispatchKey(term.getWidget(), k, a, m);
            return true;
        }
        return false;
    }

    public boolean onChar(int c) {
        if (EditorManager.INSTANCE.isEnabled() && focused) {
            InputDispatcher.dispatchChar(term.getWidget(), c);
            return true;
        }
        return false;
    }
}
