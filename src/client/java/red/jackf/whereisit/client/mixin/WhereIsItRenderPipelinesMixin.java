package red.jackf.whereisit.client.mixin;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import red.jackf.whereisit.client.render.WhereIsItPipelines;


@Mixin(RenderPipelines.class)
public class WhereIsItRenderPipelinesMixin {

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void whereisit_onRegisterPipelines(CallbackInfo ci) {

        // Регистрируем PIPELINE
        WhereIsItPipelines.DEBUG_QUADS_NO_DEPTH_PIPELINE =
                RenderPipeline.builder(RenderPipelines.MATRICES_COLOR_SNIPPET)
                        .withLocation(ResourceLocation.fromNamespaceAndPath("whereisit", "pipeline/debug_quads_no_depth"))
                        .withVertexShader("core/position_color")
                        .withFragmentShader("core/position_color")
                        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .withDepthWrite(false)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withCull(false)
                        .build();

        WhereIsItPipelines.DEBUG_QUADS_LEQUAL_DEPTH_PIPELINE =
                RenderPipeline.builder(RenderPipelines.MATRICES_COLOR_SNIPPET)
                        .withLocation(ResourceLocation.fromNamespaceAndPath("whereisit", "pipeline/debug_quads_lequal"))
                        .withVertexShader("core/position_color")
                        .withFragmentShader("core/position_color")
                        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .withDepthWrite(false)
                        .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                        .withCull(false)
                        .build();

        WhereIsItPipelines.TEXT_BACKGROUND_NO_DEPTH_PIPELINE =
                RenderPipeline.builder(RenderPipelines.MATRICES_COLOR_SNIPPET)
                        .withLocation(ResourceLocation.fromNamespaceAndPath("whereisit", "pipeline/text_background_no_depth"))
                        .withVertexShader("core/position_color")
                        .withFragmentShader("core/position_color")
                        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .withDepthWrite(false)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withCull(false)
                        .build();

        WhereIsItPipelines.initRenderTypes();
    }
}
