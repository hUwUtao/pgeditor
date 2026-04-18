package work.stdpi.pge.editor.render;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.TerminalPanel;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.jediterm.terminal.ProcessTtyConnector;
import com.pty4j.PtyProcessBuilder;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.nio.charset.StandardCharsets;

public class TerminalRenderer {
    private JediTermWidget widget;
    private NativeImageBackedTexture texture;
    private NativeImage nativeImage;
    private Process process;
    private int w, h;

    public void init(int w, int h) {
        this.w = w; this.h = h;
        try {
            SwingUtilities.invokeAndWait(() -> {
                widget = new JediTermWidget(new DefaultSettingsProvider());
                widget.setFocusable(true);
                widget.setDoubleBuffered(false);
                configureComponent(widget, w, h);
                startSession();
                resizeTerminalOnEdt(w, h);
                focus();
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize terminal renderer", e);
        }
        refreshTexture();
    }

    private void refreshTexture() {
        if (nativeImage != null) {
            nativeImage.close();
            texture.close();
        }
        nativeImage = new NativeImage(w, h, true);
        texture = new NativeImageBackedTexture(() -> "pge_term", nativeImage);
    }

    public void update() {
        if (widget == null || nativeImage == null || texture == null) return;
        try {
            SwingUtilities.invokeAndWait(() -> {
                resizeTerminalOnEdt(w, h);
                var img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                var g = img.createGraphics();

                g.setColor(Color.WHITE);
                g.fillRect(0, 0, w, h);
                widget.paintAll(g);
                g.dispose();
                
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                    int c = img.getRGB(x, y);
                    int a = (c >> 24) & 0xFF;
                    int r = (c >> 16) & 0xFF;
                    int g8 = (c >> 8) & 0xFF;
                    int b = c & 0xFF;
                    nativeImage.setColor(x, y, (a << 24) | (b << 16) | (g8 << 8) | r);
                }
            });
            texture.upload();
        } catch (Exception ignored) {}
    }

    public void resize(int w, int h) {
        if (this.w == w && this.h == h) return;
        this.w = w; this.h = h;
        if (widget != null) {
            SwingUtilities.invokeLater(() -> resizeTerminalOnEdt(w, h));
        }
        refreshTexture();
    }

    public NativeImageBackedTexture getTexture() { return texture; }
    public JediTermWidget getWidget() { return widget; }
    public TerminalPanel getInputTarget() { return widget == null ? null : widget.getTerminalPanel(); }

    public void focus() {
        if (widget == null) return;
        SwingUtilities.invokeLater(() -> {
            widget.requestFocus();
            widget.requestFocusInWindow();
            if (widget.getTerminalPanel() != null) {
                widget.getTerminalPanel().requestFocus();
                widget.getTerminalPanel().requestFocusInWindow();
            }
        });
    }

    private void startSession() {
        try {
            process = createProcessBuilder().start();
            widget.createTerminalSession(new ProcessTtyConnector(process, StandardCharsets.UTF_8) {
                @Override public String getName() { return "PGE"; }
            });
            widget.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start local terminal session", e);
        }
    }

    private PtyProcessBuilder createProcessBuilder() {
        var env = new HashMap<>(System.getenv());
        env.putIfAbsent("TERM", "xterm-256color");
        env.putIfAbsent("COLORTERM", "truecolor");

        return new PtyProcessBuilder(buildShellCommand())
            .setDirectory(System.getProperty("user.home"))
            .setEnvironment(env)
            .setInitialColumns(Math.max(20, w / 9))
            .setInitialRows(Math.max(5, h / 18));
    }

    private String[] buildShellCommand() {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return new String[]{"cmd.exe"};
        }

        String shell = System.getenv("SHELL");
        if (shell != null && !shell.isBlank()) {
            return new String[]{shell, "-i"};
        }
        return new String[]{"/bin/bash", "--login"};
    }

    private void resizeTerminalOnEdt(int width, int height) {
        if (widget == null) return;
        configureComponent(widget, width, height);

        TerminalPanel panel = widget.getTerminalPanel();
        if (panel != null) {
            configureComponent(panel, width, height);
            panel.setBackground(Color.WHITE);
            panel.setForeground(Color.BLACK);
            widget.doLayout();
            widget.validate();
            panel.revalidate();
            panel.repaint();

            TermSize termSize = panel.getTerminalSizeFromComponent();
            if (termSize.getColumns() > 0 && termSize.getRows() > 0) {
                panel.onResize(termSize, RequestOrigin.User);
            }
        }
    }

    private void configureComponent(java.awt.Component component, int width, int height) {
        component.setSize(new Dimension(width, height));
        component.setPreferredSize(new Dimension(width, height));
        component.setMinimumSize(new Dimension(width, height));
    }
}
