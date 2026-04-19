package work.stdpi.pge.editor.render.applet

import work.stdpi.pge.editor.render.canvas.CanvasEntity
import work.stdpi.pge.editor.render.canvas.CanvasSpace
import work.stdpi.pge.editor.render.canvas.input.ICanvasInputHandler

interface IEditorApplet : CanvasEntity, ICanvasInputHandler {
  val id: String
  val displayName: String
  val space: CanvasSpace
}
