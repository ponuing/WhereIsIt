package red.jackf.whereisit.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import red.jackf.whereisit.client.render.Rendering;

import java.util.Optional;
import java.util.OptionalDouble;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(
            method = "render",
            at = @At("TAIL")
    )
    private void onRenderLevelEnd(
            GraphicsResourceAllocator resourceAllocator,
            boolean renderOutline,
            CameraRenderState cameraState,
            GpuBufferSlice terrainFog,
            Vector4f fogColor,
            boolean shouldRenderSky,
            boolean consistentDepthRequired,
            CallbackInfo ci
    ) {
        if (!Rendering.shouldBeRendering()) return;

        Minecraft minecraft = Minecraft.getInstance();
        float tickDelta = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Camera camera = minecraft.gameRenderer.mainCamera();
        RenderTarget mainTarget = minecraft.gameRenderer.mainRenderTarget();

        // has to be recorded and uploaded before the pass is opened, the upload is a buffer copy
        Rendering.DrawCollector drawCollector = Rendering.prepareWorld(camera, tickDelta);

        if (drawCollector.hasDraws()) {
            RenderPass renderPass = RenderSystem.getDevice()
                    .createCommandEncoder()
                    .createRenderPass(
                            () -> "WhereIsIt",
                            mainTarget.getColorTextureView(),
                            Optional.empty(),
                            mainTarget.getDepthTextureView(),
                            OptionalDouble.of(0.0)
                    );
            try {
                RenderSystem.bindDefaultUniforms(renderPass);
                drawCollector.draw(renderPass);
            } finally {
                renderPass.close();
            }
        }

        // has to happen after the pass is closed, the buffer pools create a fence here
        Rendering.endFrame();
    }
}
