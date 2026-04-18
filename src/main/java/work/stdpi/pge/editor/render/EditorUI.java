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
        int sw = win.getScaledWidth(), sh = win.getScaledHeight();
        
        // Logical start of the GAME area
        int gx = ViewportController.INSTANCE.getX(), gy = ViewportController.INSTANCE.getY();
        int gw = ViewportController.INSTANCE.getWidth(), gh = ViewportController.INSTANCE.getHeight();

        switch (EditorManager.INSTANCE.getSide()) {
            case LEFT ->  { ex = 0; ey = 0; ew = gx; eh = sh; }
            case RIGHT -> { ex = gw; ey = 0; ew = sw - gw; eh = sh; }
            case TOP ->   { ex = 0; ey = 0; ew = sw; eh = gy; }
            case BOTTOM ->{ ex = 0; ey = gh; ew = sw; eh = sh - gh; }
        }

        if (ew <= 0 || eh <= 0) return;
        
        // 1. Draw solid background to cover world residue
        context.fill(ex, ey, ex + ew, ey + eh, 0xFF1E1E1E); // VSCode-ish dark gray

        if (!init) { term.init(ew, eh); init = true; } else { term.resize(ew, eh); }
        term.update();

        var tex = term.getTexture();
        if (tex == null) return;
        var id = Identifier.of("pge-editor", "term");
        mc.getTextureManager().registerTexture(id, tex);
        
        // 2. Draw terminal on top
        context.drawTexturedQuad(id, ex, ex + ew, ey, ey + eh, 0f, 1f, 0f, 1f);
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
