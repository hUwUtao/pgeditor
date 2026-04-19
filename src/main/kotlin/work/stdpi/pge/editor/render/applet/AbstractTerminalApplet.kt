package work.stdpi.pge.editor.render.applet

import net.minecraft.client.gui.DrawContext
import work.stdpi.pge.editor.render.TerminalRenderer
import work.stdpi.pge.editor.render.canvas.CanvasRect
import work.stdpi.pge.editor.render.canvas.CanvasSpace
import work.stdpi.pge.editor.render.canvas.input.CanvasCharEvent
import work.stdpi.pge.editor.render.canvas.input.CanvasKeyEvent
import work.stdpi.pge.editor.render.canvas.input.CanvasPointerEvent
import work.stdpi.pge.editor.render.canvas.input.CanvasScrollEvent
import work.stdpi.pge.editor.terminal.ITerminalIo

abstract class AbstractTerminalApplet(
    final override val id: String,
    final override val displayName: String,
    val terminalIo: ITerminalIo,
    private val renderer: TerminalRenderer = TerminalRenderer(terminalIo)
) : IEditorApplet {
  final override val space: CanvasSpace = CanvasSpace.FRAMEBUFFER

  final override fun render(context: DrawContext, bounds: CanvasRect) {
    renderer.render(context, bounds.x, bounds.y, bounds.width, bounds.height)
  }

  final override fun onCanvasFocusGained() {
    renderer.focus()
  }

  final override fun onCanvasPointerButton(event: CanvasPointerEvent): Boolean {
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

  final override fun onCanvasPointerMove(event: CanvasPointerEvent) {
    renderer.onMove(event.localX, event.localY, event.bounds.width, event.bounds.height)
  }

  final override fun onCanvasScroll(event: CanvasScrollEvent): Boolean =
      renderer.onScroll(
          event.localX,
          event.localY,
          event.horizontalAmount,
          event.verticalAmount,
          event.mods,
          event.bounds.width,
          event.bounds.height)

  final override fun onCanvasKey(event: CanvasKeyEvent): Boolean =
      renderer.onKey(event.key, event.action, event.mods)

  final override fun onCanvasChar(event: CanvasCharEvent): Boolean = renderer.onChar(event.codepoint)
}
