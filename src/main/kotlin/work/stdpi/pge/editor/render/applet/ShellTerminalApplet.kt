package work.stdpi.pge.editor.render.applet

import work.stdpi.pge.editor.terminal.PtyTerminalIo

class ShellTerminalApplet : AbstractTerminalApplet(
    id = "terminal",
    displayName = "Terminal",
    terminalIo = PtyTerminalIo()
)
