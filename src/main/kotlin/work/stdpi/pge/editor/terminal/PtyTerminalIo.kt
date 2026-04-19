package work.stdpi.pge.editor.terminal

import com.jediterm.terminal.ProcessTtyConnector
import com.pty4j.PtyProcessBuilder
import java.nio.charset.StandardCharsets
import java.util.Locale

class PtyTerminalIo : ITerminalIo {
  override fun open(columns: Int, rows: Int): ITerminalHandle {
    val connector =
        object : ProcessTtyConnector(createProcessBuilder(columns, rows).start(), StandardCharsets.UTF_8) {
          override fun getName(): String {
            return "PGE"
          }
        }
    return PtyTerminalHandle.from(connector)
  }

  private fun createProcessBuilder(columns: Int, rows: Int): PtyProcessBuilder {
    val env = HashMap(System.getenv())
    env["TERM"] = "xterm-256color"
    env["COLORTERM"] = "truecolor"
    env["TERM_PROGRAM"] = "pge-editor"
    env["TERM_PROGRAM_VERSION"] = "dev"

    return PtyProcessBuilder(buildShellCommand())
        .setDirectory(System.getProperty("user.home"))
        .setEnvironment(env)
        .setInitialColumns(columns)
        .setInitialRows(rows)
  }

  private fun buildShellCommand(): Array<String> {
    if (System.getProperty("os.name").lowercase(Locale.getDefault()).contains("win")) {
      return arrayOf("cmd.exe")
    }

    val shell = System.getenv("SHELL")
    if (!shell.isNullOrBlank()) {
      return arrayOf(shell, "-i")
    }
    return arrayOf("/bin/bash", "--login")
  }
}
