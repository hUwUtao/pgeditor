package work.stdpi.pge.editor.terminal

import com.jediterm.terminal.TtyConnector

interface ITerminalHandle {
  val ttyConnector: TtyConnector

  fun resize(columns: Int, rows: Int)
}
