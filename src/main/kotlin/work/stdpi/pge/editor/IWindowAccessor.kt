package work.stdpi.pge.editor

interface IWindowAccessor {
  fun `pge$getRealWindowWidth`(): Int

  fun `pge$getRealWindowHeight`(): Int

  fun `pge$getRealFramebufferWidth`(): Int

  fun `pge$getRealFramebufferHeight`(): Int

  fun `pge$getRealScaledWidth`(): Int

  fun `pge$getRealScaledHeight`(): Int
}
