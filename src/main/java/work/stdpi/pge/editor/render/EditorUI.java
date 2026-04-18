package work.stdpi.pge.editor.render;

import com.mojang.blaze3d.systems.RenderSystem;
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

        // Calculate Editor Rect in REAL scaled coordinates
        int gw = ViewportController.INSTANCE.getWidth();
        int gh = ViewportController.INSTANCE.getHeight();
        int gx = ViewportController.INSTANCE.getX();
        int gy = ViewportController.INSTANCE.getY();

        switch (EditorManager.INSTANCE.getSide()) {
            case LEFT ->  { ex = 0; ey = 0; ew = gx; eh = rsh; }
            case RIGHT -> { ex = gw; ey = 0; ew = rsw - gw; eh = rsh; }
            case TOP ->   { ex = 0; ey = 0; ew = rsw; eh = gy; }
            case BOTTOM ->{ ex = 0; ey = gh; ew = rsw; eh = rsh - gh; }
        }

        if (ew <= 0 || eh <= 0) return;

        // Note: Viewport is ALREADY full (from Mixin). 
        // We just need to ensure the DrawContext doesn't clip us.
        // DrawContext uses the window's scaledWidth/Height for its projection.
        
        // We use a trick: DrawContext doesn't actually clip! 
        // It just passes coordinates to a vertex buffer.
        // The clipping happens at the GPU level based on the projection matrix.
        
        // Since we can't easily change the projection matrix in 1.21.11,
        // we'll use raw RenderSystem calls if DrawContext fails.
        
        // But let's try DrawContext first with ABSOLUTE coordinates.
        // We need to counteract the matrix stack scaling.
        float liedScaleX = (float) win.getScaledWidth() / rsw;
        float liedScaleY = (float) win.getScaledHeight() / rsh;
        
        context.getMatrices().pushMatrix();
        // Scale matrices so that coordinates match REAL scaled pixels
        context.getMatrices().scale(liedScaleX, liedScaleY);
        
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
