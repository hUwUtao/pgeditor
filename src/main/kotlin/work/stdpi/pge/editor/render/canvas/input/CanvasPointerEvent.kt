package work.stdpi.pge.editor.render.canvas.input

import work.stdpi.pge.editor.render.canvas.CanvasRect

data class CanvasPointerEvent(
    val localX: Int,
    val localY: Int,
    val bounds: CanvasRect,
    val button: Int? = null,
    val action: Int? = null,
    val mods: Int = 0
)
