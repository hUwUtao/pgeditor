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

        this.addDrawableChild(
            new FontWeightSlider(centerX - 100, top, 200, 20)
        );
        this.addDrawableChild(
            new GlyphWidthSlider(centerX - 100, top + 24, 200, 20)
        );
        this.addDrawableChild(
            ButtonWidget.builder(Text.literal("Done"), button -> close())
                .dimensions(centerX - 100, top + 52, 200, 20)
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
                "Embedded Intel One Mono with persistent terminal tuning."
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

    private static final class FontWeightSlider extends SliderWidget {

        private FontWeightSlider(int x, int y, int width, int height) {
            super(
                x,
                y,
                width,
                height,
                Text.empty(),
                toValue(EditorManager.INSTANCE.getTerminalFontWeight())
            );
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            EditorManager.TerminalFontWeight weight = getFontWeight();
            this.setMessage(Text.literal("Font Weight: " + formatWeight(weight)));
        }

        @Override
        protected void applyValue() {
            EditorManager.INSTANCE.setTerminalFontWeight(getFontWeight());
        }

        private EditorManager.TerminalFontWeight getFontWeight() {
            EditorManager.TerminalFontWeight[] weights = EditorManager.TerminalFontWeight.values();
            int index = (int) Math.round(this.value * (weights.length - 1));
            return weights[Math.max(0, Math.min(weights.length - 1, index))];
        }

        private static double toValue(EditorManager.TerminalFontWeight weight) {
            EditorManager.TerminalFontWeight[] weights = EditorManager.TerminalFontWeight.values();
            return (double) weight.ordinal() / (double) (weights.length - 1);
        }

        private static String formatWeight(EditorManager.TerminalFontWeight weight) {
            return switch (weight) {
                case LIGHT -> "Light";
                case REGULAR -> "Regular";
                case MEDIUM -> "Medium";
                case BOLD -> "Bold";
            };
        }
    }

    private static final class GlyphWidthSlider extends SliderWidget {

        private static final int MIN_WIDTH = 1;
        private static final int MAX_WIDTH = 24;

        private GlyphWidthSlider(int x, int y, int width, int height) {
            super(
                x,
                y,
                width,
                height,
                Text.empty(),
                toValue(EditorManager.INSTANCE.getTerminalCellWidthPx())
            );
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int pixels = getWidthPixels();
            this.setMessage(Text.literal("Terminal Zoom: " + pixels + " px cell width"));
        }

        @Override
        protected void applyValue() {
            EditorManager.INSTANCE.setTerminalCellWidthPx(getWidthPixels());
        }

        private int getWidthPixels() {
            return (
                MIN_WIDTH +
                (int) Math.round(this.value * (MAX_WIDTH - MIN_WIDTH))
            );
        }

        private static double toValue(int width) {
            return (
                (double) (width - MIN_WIDTH) / (double) (MAX_WIDTH - MIN_WIDTH)
            );
        }
    }

}
