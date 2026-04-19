package work.stdpi.pge.editor.terminal

import com.jediterm.terminal.ProcessTtyConnector
import com.jediterm.terminal.TtyConnector
import com.pty4j.PtyProcess
import com.pty4j.WinSize

class PtyTerminalHandle(
    override val ttyConnector: TtyConnector,
    private val process: PtyProcess?
) : ITerminalHandle {
  override fun resize(columns: Int, rows: Int) {
    process?.winSize = WinSize(columns, rows)
  }

  companion object {
    fun from(connector: ProcessTtyConnector): PtyTerminalHandle =
        PtyTerminalHandle(connector, connector.process as? PtyProcess)
  }
}
