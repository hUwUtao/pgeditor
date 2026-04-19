package work.stdpi.pge.editor.terminal

interface ITerminalIo {
  fun open(columns: Int, rows: Int): ITerminalHandle
}
