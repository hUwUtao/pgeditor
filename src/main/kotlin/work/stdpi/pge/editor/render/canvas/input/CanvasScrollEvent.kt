package work.stdpi.pge.editor.render.canvas.input

import work.stdpi.pge.editor.render.canvas.CanvasRect

data class CanvasScrollEvent(
    val localX: Int,
    val localY: Int,
    val bounds: CanvasRect,
    val horizontalAmount: Double,
    val verticalAmount: Double,
    val mods: Int = 0
)
