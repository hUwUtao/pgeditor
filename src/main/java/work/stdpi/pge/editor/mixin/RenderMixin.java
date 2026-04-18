package work.stdpi.pge.editor.mixin;

import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import work.stdpi.pge.editor.logic.ViewportController;
import work.stdpi.pge.editor.render.EditorUI;
import work.stdpi.pge.editor.WindowAccessor;

public class RenderMixin {
    @Mixin(GameRenderer.class)
    public static class GameRendererMixin {
        @Inject(method = "render", at = @At("HEAD"))
        private void beforeRender(RenderTickCounter d, boolean t, CallbackInfo ci) {
            if (ViewportController.INSTANCE.isActive()) {
                var win = MinecraftClient.getInstance().getWindow();
                int rw = ((WindowAccessor) (Object) win).pge$getRealFramebufferWidth();
                int rh = ((WindowAccessor) (Object) win).pge$getRealFramebufferHeight();
                // Ensure FULL viewport for the CLEAR operation
                GlStateManager._viewport(0, 0, rw, rh);
            }
        }

        @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;viewport(IIII)V", shift = At.Shift.AFTER))
        private void fixWorldViewport(CallbackInfo ci) {
            // GameRenderer.renderWorld calls RenderSystem.viewport(0, 0, smallW, smallH)
            // We must override it immediately to shift the world
            ViewportController.INSTANCE.apply(MinecraftClient.getInstance().getWindow());
        }

        @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"))
        private void beforeHud(CallbackInfo ci) {
            ViewportController.INSTANCE.apply(MinecraftClient.getInstance().getWindow());
        }

        @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", shift = At.Shift.AFTER))
        private void afterHud(CallbackInfo ci) {
            if (ViewportController.INSTANCE.isActive()) {
                var win = MinecraftClient.getInstance().getWindow();
                int rw = ((WindowAccessor) (Object) win).pge$getRealFramebufferWidth();
                int rh = ((WindowAccessor) (Object) win).pge$getRealFramebufferHeight();
                GlStateManager._viewport(0, 0, rw, rh);
            }
        }
    }

    @Mixin(InGameHud.class)
    public static class InGameHudMixin {
        @Inject(method = "render", at = @At("RETURN"))
        private void renderEditor(DrawContext g, RenderTickCounter d, CallbackInfo ci) {
            if (ViewportController.INSTANCE.isActive()) {
                var win = MinecraftClient.getInstance().getWindow();
                int rw = ((WindowAccessor) (Object) win).pge$getRealFramebufferWidth();
                int rh = ((WindowAccessor) (Object) win).pge$getRealFramebufferHeight();
                // Ensure FULL viewport for EditorUI drawing
                GlStateManager._viewport(0, 0, rw, rh);
                EditorUI.INSTANCE.render(g);
                // Re-apply for any subsequent HUD elements
                ViewportController.INSTANCE.apply(win);
            }
        }
    }
}
