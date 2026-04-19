package work.stdpi.pge.editor.render.canvas

import net.minecraft.client.gui.DrawContext

interface CanvasEntity {
  fun render(context: DrawContext, bounds: CanvasRect)
}
