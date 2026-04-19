package work.stdpi.pge.editor

interface WindowAccessor {
  fun `pge$getRealWindowWidth`(): Int

  fun `pge$getRealWindowHeight`(): Int

  fun `pge$getRealFramebufferWidth`(): Int

  fun `pge$getRealFramebufferHeight`(): Int

  fun `pge$getRealScaledWidth`(): Int

  fun `pge$getRealScaledHeight`(): Int
}
