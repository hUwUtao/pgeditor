package work.stdpi.pge.editor.render.applet

import net.minecraft.client.gui.DrawContext
import work.stdpi.pge.editor.render.GlyphGridRenderer
import work.stdpi.pge.editor.render.canvas.CanvasRect
import work.stdpi.pge.editor.render.canvas.CanvasSpace
import work.stdpi.pge.editor.render.canvas.input.CanvasKeyEvent
import work.stdpi.pge.editor.render.canvas.input.CanvasPointerEvent

object GlyphGridApplet : IEditorApplet {
  private val renderer = GlyphGridRenderer()

  override val id: String = "glyph_grid"
  override val displayName: String = "Glyph Grid"
  override val space: CanvasSpace = CanvasSpace.SCALED

  override fun render(context: DrawContext, bounds: CanvasRect) {
    renderer.render(context, bounds.x, bounds.y, bounds.width, bounds.height)
  }

  override fun onCanvasPointerButton(event: CanvasPointerEvent): Boolean {
    val button = event.button ?: return false
    val action = event.action ?: return false
    return renderer.onMouse(
        event.localX,
        event.localY,
        button,
        action,
        event.mods,
        event.bounds.width,
        event.bounds.height)
  }

  override fun onCanvasPointerMove(event: CanvasPointerEvent) {
    renderer.onMove(event.localX, event.localY, event.bounds.width, event.bounds.height)
  }

  override fun onCanvasKey(event: CanvasKeyEvent): Boolean = renderer.onKey(event.key, event.action)
}
