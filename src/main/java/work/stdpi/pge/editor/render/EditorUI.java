package work.stdpi.pge.editor.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import work.stdpi.pge.editor.logic.EditorManager;
import work.stdpi.pge.editor.logic.ViewportController;
import work.stdpi.pge.editor.logic.InputDispatcher;

public class EditorUI {
    public static final EditorUI INSTANCE = new EditorUI();
    private final TerminalRenderer term = new TerminalRenderer();
    private boolean init = false;
    private boolean focused = false;
    private int ex, ey, ew, eh;

    public void render(DrawContext context) {
        if (!EditorManager.INSTANCE.isEnabled()) return;
        var win = MinecraftClient.getInstance().getWindow();
        int sw = win.getScaledWidth(), sh = win.getScaledHeight();
        int gw = ViewportController.INSTANCE.getWidth(), gh = ViewportController.INSTANCE.getHeight();
        int gx = ViewportController.INSTANCE.getX(), gy = ViewportController.INSTANCE.getY();

        switch (EditorManager.INSTANCE.getSide()) {
            case LEFT ->  { ex = 0; ey = 0; ew = gx; eh = sh; }
            case RIGHT -> { ex = gw; ey = 0; ew = sw - gw; eh = sh; }
            case TOP ->   { ex = 0; ey = 0; ew = sw; eh = gy; }
            case BOTTOM ->{ ex = 0; ey = gh; ew = sw; eh = sh - gh; }
        }

        if (ew <= 0 || eh <= 0) return;
        if (!init) { term.init(ew, eh); init = true; } else { term.resize(ew, eh); }
        term.update();

        var tex = term.getTexture();
        if (tex == null) return;
        var id = Identifier.of("pge-editor", "term");
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
        
        // In 1.21.11, DrawContext.drawTexturedQuad handles binding and rendering
        context.drawTexturedQuad(id, ex, ey, ew, eh, 0f, 1f, 0f, 1f);
    }

    public boolean onMouse(int b, int a, int m) {
        if (!EditorManager.INSTANCE.isEnabled()) return false;
        var mc = MinecraftClient.getInstance();
        var win = mc.getWindow();
        double s = (double) win.getFramebufferWidth() / win.getScaledWidth();
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
        var win = MinecraftClient.getInstance().getWindow();
        double s = (double) win.getFramebufferWidth() / win.getScaledWidth();
        InputDispatcher.dispatchMove(term.getWidget(), (int)(x / s) - ex, (int)(y / s) - ey);
    }

    public boolean onKey(int k, int a, int m) {
        if (EditorManager.INSTANCE.isEnabled() && focused) {
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
