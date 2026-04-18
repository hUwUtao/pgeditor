package work.stdpi.pge.editor.modmenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import work.stdpi.pge.editor.logic.EditorManager;

public class PgeSettingsScreen extends Screen {

    private final Screen parent;

    public PgeSettingsScreen(Screen parent) {
        super(Text.literal("Paper Engine Editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int top = this.height / 2 - 34;

        this.addDrawableChild(new GlyphSizeSlider(centerX - 100, top, 200, 20));
        this.addDrawableChild(
            new GlyphRectScaleSlider(centerX - 100, top + 24, 200, 20)
        );
        this.addDrawableChild(
            ButtonWidget.builder(Text.literal("Done"), button -> close())
                .dimensions(centerX - 100, top + 54, 200, 20)
                .build()
        );
    }

    @Override
    public void render(
        DrawContext context,
        int mouseX,
        int mouseY,
        float deltaTicks
    ) {
        this.renderBackground(context, mouseX, mouseY, deltaTicks);
        super.render(context, mouseX, mouseY, deltaTicks);
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            this.title,
            this.width / 2,
            this.height / 2 - 48,
            0xFFFFFF
        );
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal(
                "Tune terminal glyph size for the native Jedi renderer."
            ),
            this.width / 2,
            this.height / 2 - 34,
            0xA0A0A0
        );
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    private static final class GlyphSizeSlider extends SliderWidget {

        private static final int MIN_SIZE = 1;
        private static final int MAX_SIZE = 14;

        private GlyphSizeSlider(int x, int y, int width, int height) {
            super(
                x,
                y,
                width,
                height,
                Text.empty(),
                toValue(EditorManager.INSTANCE.getTerminalFontSize())
            );
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int size = getGlyphSize();
            this.setMessage(Text.literal("Terminal Glyph Size: " + size));
        }

        @Override
        protected void applyValue() {
            int target = getGlyphSize();
            int current = EditorManager.INSTANCE.getTerminalFontSize();
            EditorManager.INSTANCE.adjustTerminalFontSize(target - current);
        }

        private int getGlyphSize() {
            return (
                MIN_SIZE + (int) Math.round(this.value * (MAX_SIZE - MIN_SIZE))
            );
        }

        private static double toValue(int size) {
            return (double) (size - MIN_SIZE) / (double) (MAX_SIZE - MIN_SIZE);
        }
    }

    private static final class GlyphRectScaleSlider extends SliderWidget {

        private static final int MIN_SCALE = 70;
        private static final int MAX_SCALE = 130;

        private GlyphRectScaleSlider(int x, int y, int width, int height) {
            super(
                x,
                y,
                width,
                height,
                Text.empty(),
                toValue(EditorManager.INSTANCE.getTerminalCellScalePercent())
            );
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int scale = getScalePercent();
            this.setMessage(Text.literal("Glyph Rect Scale: " + scale + "%"));
        }

        @Override
        protected void applyValue() {
            EditorManager.INSTANCE.setTerminalCellScalePercent(
                getScalePercent()
            );
        }

        private int getScalePercent() {
            return (
                MIN_SCALE +
                (int) Math.round(this.value * (MAX_SCALE - MIN_SCALE))
            );
        }

        private static double toValue(int scale) {
            return (
                (double) (scale - MIN_SCALE) / (double) (MAX_SCALE - MIN_SCALE)
            );
        }
    }
}
