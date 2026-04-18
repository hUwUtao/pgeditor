package work.stdpi.pge.editor.render;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.jediterm.terminal.ProcessTtyConnector;
import com.pty4j.PtyProcessBuilder;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Color;
import java.nio.charset.StandardCharsets;

public class TerminalRenderer {
    private JediTermWidget widget;
    private NativeImageBackedTexture texture;
    private NativeImage nativeImage;
    private int w, h;

    public void init(int w, int h) {
        this.w = w; this.h = h;
        widget = new JediTermWidget(new DefaultSettingsProvider());
        widget.setSize(new Dimension(w, h));
        try {
            var proc = new PtyProcessBuilder(System.getProperty("os.name").toLowerCase().contains("win") ? new String[]{"cmd.exe"} : new String[]{"/bin/bash", "--login"}).start();
            widget.createTerminalSession(new ProcessTtyConnector(proc, StandardCharsets.UTF_8) {
                @Override public String getName() { return "PGE"; }
            });
            widget.start();
        } catch (Exception e) { e.printStackTrace(); }
        refreshTexture();
    }

    private void refreshTexture() {
        if (nativeImage != null) { nativeImage.close(); texture.close(); }
        nativeImage = new NativeImage(w, h, true);
        texture = new NativeImageBackedTexture(() -> "pge_term", nativeImage);
    }

    public void update() {
        if (widget == null) return;
        try {
            SwingUtilities.invokeAndWait(() -> {
                var img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var g = img.createGraphics();
                
                // Clear with white so we can see if JediTerm is drawing anything
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, w, h);
                
                widget.paint(g);
                g.dispose();
                
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                    int c = img.getRGB(x, y);
                    // ABGR for NativeImage
                    nativeImage.setColor(x, y, ((c >> 24) & 0xFF) << 24 | (c & 0xFF) << 16 | ((c >> 8) & 0xFF) << 8 | ((c >> 16) & 0xFF));
                }
            });
            texture.upload();
        } catch (Exception ignored) {}
    }

    public void resize(int w, int h) {
        if (this.w == w && this.h == h) return;
        this.w = w; this.h = h;
        widget.setSize(new Dimension(w, h));
        refreshTexture();
    }

    public NativeImageBackedTexture getTexture() { return texture; }
    public JediTermWidget getWidget() { return widget; }
}
