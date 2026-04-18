package work.stdpi.pge.editor.logic;

import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.util.Window;
import work.stdpi.pge.editor.WindowAccessor;

public class ViewportController {
    public static final ViewportController INSTANCE = new ViewportController();
    
    private boolean active = false;
    private boolean windowMetricsOverridden = false;
    private int x, y, width, height;

    private ViewportController() {}

    public void calculate(Window window, EditorManager.DockSide side, float percent) {
        int sw = ((WindowAccessor) (Object) window).pge$getRealScaledWidth();
        int sh = ((WindowAccessor) (Object) window).pge$getRealScaledHeight();

        if (sw <= 0 || sh <= 0) return;

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
        int realScaledWidth = ((WindowAccessor) (Object) window).pge$getRealScaledWidth();
        int realFramebufferWidth = ((WindowAccessor) (Object) window).pge$getRealFramebufferWidth();
        int realScaledHeight = ((WindowAccessor) (Object) window).pge$getRealScaledHeight();
        if (realScaledWidth <= 0 || realScaledHeight <= 0) return;

        double s = (double) realFramebufferWidth / realScaledWidth;
        int px = (int) (x * s);
        int py = (int) ((realScaledHeight - (y + height)) * s);
        int pw = (int) (width * s);
        int ph = (int) (height * s);
        if (pw > 0 && ph > 0) GlStateManager._viewport(px, py, pw, ph);
    }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isWindowMetricsOverridden() { return windowMetricsOverridden; }
    public void setWindowMetricsOverridden(boolean windowMetricsOverridden) { this.windowMetricsOverridden = windowMetricsOverridden; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
