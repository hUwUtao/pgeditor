package work.stdpi.pge.editor.mixin;

import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import work.stdpi.pge.editor.logic.ViewportController;
import work.stdpi.pge.editor.render.EditorUI;
import work.stdpi.pge.editor.WindowAccessor;

public class RenderMixin {
    @Mixin(GameRenderer.class)
    public static class GameRendererMixin {
        private boolean pge$useViewportMetrics() {
            MinecraftClient client = MinecraftClient.getInstance();
            return ViewportController.INSTANCE.isActive() && client.currentScreen == null;
        }

        @Inject(method = "getBasicProjectionMatrix", at = @At("HEAD"), cancellable = true)
        private void overrideProjectionMatrix(float fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
            if (!pge$useViewportMetrics()) {
                return;
            }

            float viewportWidth = Math.max(1, ViewportController.INSTANCE.getWidth());
            float viewportHeight = Math.max(1, ViewportController.INSTANCE.getHeight());
            Matrix4f projection = new Matrix4f().perspective(
                fovDegrees * 0.017453292f,
                viewportWidth / viewportHeight,
                0.05f,
                ((GameRenderer) (Object) this).getFarPlaneDistance()
            );
            cir.setReturnValue(projection);
        }

        @Inject(method = "render", at = @At("HEAD"))
        private void beforeRender(RenderTickCounter d, boolean t, CallbackInfo ci) {
            ViewportController.INSTANCE.setWindowMetricsOverridden(pge$useViewportMetrics());
            if (ViewportController.INSTANCE.isActive()) {
                var win = MinecraftClient.getInstance().getWindow();
                int rw = ((WindowAccessor) (Object) win).pge$getRealFramebufferWidth();
                int rh = ((WindowAccessor) (Object) win).pge$getRealFramebufferHeight();
                if (pge$useViewportMetrics()) {
                    ViewportController.INSTANCE.apply(win);
                } else {
                    GlStateManager._viewport(0, 0, rw, rh);
                }
            }
        }

        @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V"))
        private void beforeWorldRender(RenderTickCounter d, boolean t, CallbackInfo ci) {
            if (pge$useViewportMetrics()) {
                ViewportController.INSTANCE.apply(MinecraftClient.getInstance().getWindow());
            }
        }

        @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V", shift = At.Shift.AFTER))
        private void fixWorldViewport(RenderTickCounter d, boolean t, CallbackInfo ci) {
            if (pge$useViewportMetrics()) {
                ViewportController.INSTANCE.apply(MinecraftClient.getInstance().getWindow());
            }
        }

        @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"))
        private void beforeHud(RenderTickCounter d, boolean t, CallbackInfo ci) {
            if (pge$useViewportMetrics()) {
                ViewportController.INSTANCE.apply(MinecraftClient.getInstance().getWindow());
            }
        }

        @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", shift = At.Shift.AFTER))
        private void afterHud(RenderTickCounter d, boolean t, CallbackInfo ci) {
            ViewportController.INSTANCE.setWindowMetricsOverridden(false);
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
                ViewportController.INSTANCE.setWindowMetricsOverridden(false);
                var win = MinecraftClient.getInstance().getWindow();
                int rw = ((WindowAccessor) (Object) win).pge$getRealFramebufferWidth();
                int rh = ((WindowAccessor) (Object) win).pge$getRealFramebufferHeight();
                GlStateManager._viewport(0, 0, rw, rh);
                EditorUI.INSTANCE.render(g);
            }
        }
    }
}
