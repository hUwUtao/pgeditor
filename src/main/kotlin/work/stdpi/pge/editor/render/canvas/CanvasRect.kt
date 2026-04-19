package work.stdpi.pge.editor.render.canvas

data class CanvasRect(val x: Int, val y: Int, val width: Int, val height: Int) {
  val isVisible: Boolean
    get() = width > 0 && height > 0

  fun contains(px: Int, py: Int): Boolean = px >= x && px < x + width && py >= y && py < y + height

  companion object {
    val ZERO = CanvasRect(0, 0, 0, 0)
  }
}
