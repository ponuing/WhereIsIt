package red.jackf.whereisit.client.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import red.jackf.whereisit.client.render.WhereIsItPipelines;

import java.util.Optional;

@Mixin(RenderPipelines.class)
public class WhereIsItRenderPipelinesMixin {
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void whereisit_onRegisterPipelines(CallbackInfo ci) {
        WhereIsItPipelines.DEBUG_QUADS_NO_DEPTH_PIPELINE = RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                        .withLocation(Identifier.fromNamespaceAndPath("whereisit", "pipeline/debug_quads_no_depth"))
                        .withDepthStencilState(Optional.empty())
                        .build()
        );
        WhereIsItPipelines.DEBUG_QUADS_LEQUAL_DEPTH_PIPELINE = RenderPipelines.DEBUG_QUADS;
        WhereIsItPipelines.initRenderTypes();
    }
}
