package red.jackf.whereisit.client.render;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

public class WhereIsItPipelines {
    // RenderPipeline
    public static RenderPipeline DEBUG_QUADS_NO_DEPTH_PIPELINE;
    public static RenderPipeline DEBUG_QUADS_LEQUAL_DEPTH_PIPELINE;

    // RenderType
    public static RenderType DEBUG_QUADS_NO_DEPTH;
    public static RenderType DEBUG_QUADS_LEQUAL_DEPTH;

    public static void initRenderTypes() {
        DEBUG_QUADS_NO_DEPTH = RenderType.create(
                "whereisit_debug_quads_no_depth",
                RenderSetup.builder(DEBUG_QUADS_NO_DEPTH_PIPELINE)
                        .createRenderSetup()
        );

        DEBUG_QUADS_LEQUAL_DEPTH = RenderType.create(
                "whereisit_debug_quads_lequal",
                RenderSetup.builder(DEBUG_QUADS_LEQUAL_DEPTH_PIPELINE)
                        .createRenderSetup()
        );
    }
}
