package work.stdpi.pge.editor.modmenu

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.ButtonWidget.PressAction
import net.minecraft.client.gui.widget.SliderWidget
import net.minecraft.text.Text
import work.stdpi.pge.editor.logic.EditorManager
import work.stdpi.pge.editor.logic.EditorManager.TerminalFontWeight
import work.stdpi.pge.editor.logic.EditorManager.TerminalSupersample
import kotlin.math.max
import kotlin.math.min

class PgeSettingsScreen(private val parent: Screen?) : Screen(Text.literal("Paper Engine Editor")) {
  override fun init() {
    val centerX = this.width / 2
    val top = this.height / 2 - 46

    this.addDrawableChild<FontWeightSlider?>(FontWeightSlider(centerX - 100, top, 200, 20))
    this.addDrawableChild<SupersampleSlider?>(SupersampleSlider(centerX - 100, top + 24, 200, 20))
    this.addDrawableChild<GlyphWidthSlider?>(GlyphWidthSlider(centerX - 100, top + 48, 200, 20))
    this.addDrawableChild<ButtonWidget?>(
        ButtonWidget.builder(Text.literal("Done"), PressAction { button: ButtonWidget? -> close() })
            .dimensions(centerX - 100, top + 76, 200, 20)
            .build())
  }

  override fun render(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
    this.renderBackground(context, mouseX, mouseY, deltaTicks)
    super.render(context, mouseX, mouseY, deltaTicks)
    context.drawCenteredTextWithShadow(
        this.textRenderer, this.title, this.width / 2, this.height / 2 - 48, 0xFFFFFF)
    context.drawCenteredTextWithShadow(
        this.textRenderer,
        Text.literal("Embedded Intel One Mono with persistent terminal tuning."),
        this.width / 2,
        this.height / 2 - 46,
        0xA0A0A0)
  }

  override fun close() {
    MinecraftClient.getInstance().setScreen(parent)
  }

  private class FontWeightSlider(x: Int, y: Int, width: Int, height: Int) :
      SliderWidget(
          x,
          y,
          width,
          height,
          Text.empty(),
          toValue(EditorManager.terminalFontWeight)) {
    init {
      updateMessage()
    }

    override fun updateMessage() {
      val weight = this.fontWeight
      this.setMessage(Text.literal("Font Weight: " + formatWeight(weight)))
    }

    override fun applyValue() {
      EditorManager.terminalFontWeight = this.fontWeight
    }

    val fontWeight: TerminalFontWeight
      get() {
        val weights: Array<TerminalFontWeight> = TerminalFontWeight.entries.toTypedArray()
        val index = Math.round(this.value * (weights.size - 1)).toInt()
        return weights[max(0, min(weights.size - 1, index))]
      }

    companion object {
      private fun toValue(weight: TerminalFontWeight): Double {
        val weights: Array<TerminalFontWeight> = TerminalFontWeight.entries.toTypedArray()
        return weight.ordinal.toDouble() / (weights.size - 1).toDouble()
      }

      private fun formatWeight(weight: TerminalFontWeight): String {
        return when (weight) {
          TerminalFontWeight.LIGHT -> "Light"
          TerminalFontWeight.REGULAR -> "Regular"
          TerminalFontWeight.MEDIUM -> "Medium"
          TerminalFontWeight.BOLD -> "Bold"
        }
      }
    }
  }

  private class GlyphWidthSlider(x: Int, y: Int, width: Int, height: Int) :
      SliderWidget(
          x,
          y,
          width,
          height,
          Text.empty(),
          toValue(EditorManager.terminalCellWidthPx)) {
    init {
      updateMessage()
    }

    override fun updateMessage() {
      val pixels = this.widthPixels
      this.setMessage(Text.literal("Terminal Zoom: " + pixels + " px cell width"))
    }

    override fun applyValue() {
      EditorManager.terminalCellWidthPx = this.widthPixels
    }

    val widthPixels: Int
      get() = (MIN_WIDTH + Math.round(this.value * (MAX_WIDTH - MIN_WIDTH)).toInt())

    companion object {
      private const val MIN_WIDTH = 1
      private const val MAX_WIDTH = 24

      private fun toValue(width: Int): Double {
        return ((width - MIN_WIDTH).toDouble() / (MAX_WIDTH - MIN_WIDTH).toDouble())
      }
    }
  }

  private class SupersampleSlider(x: Int, y: Int, width: Int, height: Int) :
      SliderWidget(
          x,
          y,
          width,
          height,
          Text.empty(),
          toValue(EditorManager.terminalSupersample)) {
    init {
      updateMessage()
    }

    override fun updateMessage() {
      val supersample = this.supersample
      this.setMessage(Text.literal("Supersample: " + supersample.value + "x"))
    }

    override fun applyValue() {
      EditorManager.terminalSupersample = this.supersample
    }

    val supersample: TerminalSupersample
      get() {
        val values: Array<TerminalSupersample> = TerminalSupersample.entries.toTypedArray()
        val index = Math.round(this.value * (values.size - 1)).toInt()
        return values[max(0, min(values.size - 1, index))]
      }

    companion object {
      private fun toValue(supersample: TerminalSupersample): Double {
        val values: Array<TerminalSupersample> = TerminalSupersample.entries.toTypedArray()
        return supersample.ordinal.toDouble() / (values.size - 1).toDouble()
      }
    }
  }
}
