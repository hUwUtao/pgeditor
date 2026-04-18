package work.stdpi.pge.editor.logic;

import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.util.Window;

public class ViewportController {
    public static final ViewportController INSTANCE = new ViewportController();
    
    private boolean active = false;
    private int x, y, width, height;

    private ViewportController() {}

    public void calculate(Window window, EditorManager.DockSide side, float percent) {
        int sw = window.getScaledWidth();
        int sh = window.getScaledHeight();

        int editorW = (int) (sw * percent);
        int editorH = (int) (sh * percent);
        
        editorW = Math.max(200, Math.min(editorW, sw - 50));
        editorH = Math.max(200, Math.min(editorH, sh - 50));

        switch (side) {
            case LEFT:   set(editorW, 0, sw - editorW, sh); break;
            case RIGHT:  set(0, 0, sw - editorW, sh); break;
            case TOP:    set(0, editorH, sw, sh - editorH); break;
            case BOTTOM: set(0, 0, sw, sh - editorH); break;
        }
    }

    private void set(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.width = w; this.height = h;
    }

    public void apply(Window window) {
        if (!active) return;
        double s = (double) window.getFramebufferWidth() / window.getScaledWidth();
        int px = (int) (x * s);
        int py = (int) ((window.getScaledHeight() - (y + height)) * s);
        int pw = (int) (width * s);
        int ph = (int) (height * s);
        if (pw > 0 && ph > 0) GlStateManager._viewport(px, py, pw, ph);
    }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
