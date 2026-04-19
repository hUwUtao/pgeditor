package work.stdpi.pge.editor.render.canvas.input

interface ICanvasInputHandler {
  fun onCanvasFocusGained() {}

  fun onCanvasFocusLost() {}

  fun onCanvasPointerButton(event: CanvasPointerEvent): Boolean = false

  fun onCanvasPointerMove(event: CanvasPointerEvent) {}

  fun onCanvasScroll(event: CanvasScrollEvent): Boolean = false

  fun onCanvasKey(event: CanvasKeyEvent): Boolean = false

  fun onCanvasChar(event: CanvasCharEvent): Boolean = false
}
