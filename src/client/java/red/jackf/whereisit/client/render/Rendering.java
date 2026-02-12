package red.jackf.whereisit.client.render;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.InvalidateRenderStateCallback;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import red.jackf.whereisit.api.SearchRequest;
import red.jackf.whereisit.api.SearchResult;
import red.jackf.whereisit.config.WhereIsItConfig;

import java.util.*;

import static red.jackf.whereisit.client.render.WhereIsItPipelines.*;

@SuppressWarnings("resource")
public class Rendering {

    private static final Map<BlockPos, SearchResult> results = new HashMap<>();
    private static final Map<BlockPos, SearchResult> namedResults = new HashMap<>();
    private static final List<ScheduledLabel> scheduledLabels = new ArrayList<>();

    private record ScheduledLabel(Vec3 position, Component text, boolean seeThrough) {}

    private static long ticksSinceSearch = 0;
    @Nullable
    private static SearchRequest lastRequest = null;

    public static void setup() {
        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
            if (!shouldBeRendering() || !WhereIsItConfig.INSTANCE.instance().getClient().showContainerNamesInResults)
                return;

            for (SearchResult value : namedResults.values()) {
                scheduleLabel(value.pos().getCenter().add(value.nameOffset()), value.name(),
                        WhereIsItConfig.INSTANCE.instance().getCommon().debug.labelsAreSeeThrough);
            }
        });

        InvalidateRenderStateCallback.EVENT.register(scheduledLabels::clear);
    }

    public static boolean shouldBeRendering() {
        return ticksSinceSearch <= WhereIsItConfig.INSTANCE.instance().getCommon().fadeoutTimeTicks;
    }

    public static void addResults(Collection<SearchResult> newResults) {
        for (SearchResult result : newResults) {
            results.put(result.pos(), result);
            if (result.name() != null) namedResults.put(result.pos(), result);
        }
    }

    public static void clearResults() {
        lastRequest = null;
        results.clear();
        namedResults.clear();
    }

    public static void setLastRequest(@Nullable SearchRequest request) {
        lastRequest = request;
    }

    public static long getTicksSinceSearch() { return ticksSinceSearch; }
    public static void incrementTicksSinceSearch() { ticksSinceSearch++; }
    public static void resetSearchTime() { ticksSinceSearch = 0; }
    public static Map<BlockPos, SearchResult> getResults() { return results; }
    public static Map<BlockPos, SearchResult> getNamedResults() { return namedResults; }

    // ----------------------------
    // SLOT HIGHLIGHTING (in AbstractContainerScreenMixin Mixin)
    // ----------------------------
    public static void renderSlotHighlight(AbstractContainerScreen<?> screen, GuiGraphics graphics, float tickDelta, boolean applyTransparency, int mouseX, int mouseY) {
        if (!shouldBeRendering() || lastRequest == null) return;

        float time = getBaseProgress(ticksSinceSearch, tickDelta);

        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive() || !slot.hasItem()) continue;
            if (!SearchRequest.check(slot.getItem(), lastRequest)) continue;

            int x = slot.x;
            int y = slot.y;

            float progress = time;
            progress += (slot.x / 256f) * WhereIsItConfig.INSTANCE.instance().getClient().slotHighlightXFactor;
            progress -= ((mouseX + mouseY) / 1280f) * WhereIsItConfig.INSTANCE.instance().getClient().slotHighlightMouseFactor;
            int colour = CurrentGradientHolder.getColour(progress);
            if (applyTransparency) {
                // Applying transparency to render items
                int alpha = WhereIsItConfig.INSTANCE.instance().getClient().highlightOpacity & 0xFF;
                int transparentColour = (colour & 0x00FFFFFF) | (alpha << 24);
                graphics.fill(x, y, x + 16, y + 16, transparentColour);
            } else {
                // Full opacity for rendering under items
                graphics.fill(x, y, x+16, y+16, colour);
            }
        }
    }

    // ----------------------------
    // LABEL RENDERING
    // ----------------------------
    public static void scheduleLabel(Vec3 pos, Component name, boolean seeThrough) {
        if (pos == null || name == null) return;
        scheduledLabels.add(new ScheduledLabel(pos, name, seeThrough));
    }

    public static void renderLabels(PoseStack ignoredPoseStack, Camera camera, MultiBufferSource consumers) {
        if (scheduledLabels.isEmpty()) return;

        Vec3 camPos = camera.position();

        // Create a PoseStack WITH CAMERA ROTATIONS
        PoseStack pose = new PoseStack();
        pose.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        pose.mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180f));

        scheduledLabels.stream()
                .sorted(Comparator.comparingDouble(label -> -camPos.distanceToSqr(label.position)))
                .forEach(label -> renderLabel(label, pose, camera, camPos, consumers));

        scheduledLabels.clear();
    }

    private static void renderLabel(ScheduledLabel label, PoseStack pose, Camera camera, Vec3 camPos, MultiBufferSource consumers) {
        pose.pushPose();

        // Offset from the camera
        final double xOffset = label.position.x - camPos.x;
        final double yOffset = label.position.y + WhereIsItConfig.INSTANCE.instance().getClient().Ypositiontext - camPos.y;
        final double zOffset = label.position.z - camPos.z;
        pose.translate(xOffset, yOffset, zOffset);

        pose.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));

        // Scale
        float scale = 0.025f * WhereIsItConfig.INSTANCE.instance().getClient().containerNameLabelScale;
        pose.scale(-scale, -scale, scale);

        Matrix4f matrix = pose.last().pose();
        int width = Minecraft.getInstance().font.width(label.text);
        float x = -width / 2f;

        // Background
        VertexConsumer bgBuffer = consumers.getBuffer(TEXT_BACKGROUND_NO_DEPTH);
        int bgColour = ((int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25F) * 255F)) << 24;
        bgBuffer.addVertex(matrix, x - 1, -1f, 0).setColor(bgColour).setLight(LightTexture.FULL_BRIGHT);
        bgBuffer.addVertex(matrix, x - 1, 10f, 0).setColor(bgColour).setLight(LightTexture.FULL_BRIGHT);
        bgBuffer.addVertex(matrix, x + width, 10f, 0).setColor(bgColour).setLight(LightTexture.FULL_BRIGHT);
        bgBuffer.addVertex(matrix, x + width, -1f, 0).setColor(bgColour).setLight(LightTexture.FULL_BRIGHT);

        //GL11.glDisable(GL11.GL_DEPTH_TEST);
        //GL11.glDepthFunc(GL11.GL_ALWAYS);

        Font.DisplayMode mode = Font.DisplayMode.SEE_THROUGH;
        Minecraft.getInstance().font.drawInBatch(label.text, x, 0, 0xFFFFFFFF, false, matrix, consumers, mode, 0, LightTexture.FULL_BRIGHT);

        //GL11.glDepthFunc(GL11.GL_LEQUAL);
        //GL11.glEnable(GL11.GL_DEPTH_TEST);

        pose.popPose();
    }

    // ----------------------------
    // BLOCK BOX RENDERING (FILLED CUBES)
    // ----------------------------
    public static void renderBoxes(MultiBufferSource.BufferSource bufferSource, Camera camera, float tickDelta) {
        if (results.isEmpty()) return;

        Vec3 camPos = camera.position();

        // Create a new PoseStack and apply camera rotation
        PoseStack pose = new PoseStack();
        pose.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        pose.mulPose(Axis.YP.rotationDegrees(camera.getYRot() - 180f));

        VertexConsumer consumer = bufferSource.getBuffer(DEBUG_QUADS_NO_DEPTH);

        // Get progress for RGB animation
        float progress = getRenderingProgress(tickDelta);

        // Get RGB color from gradient
        int rgbColor = CurrentGradientHolder.getColour(getBaseProgress(ticksSinceSearch, tickDelta));
        float r = ARGB.red(rgbColor) / 255f;
        float g = ARGB.green(rgbColor) / 255f;
        float b = ARGB.blue(rgbColor) / 255f;

        // Alpha with fadeout
        float baseAlpha = 0.4f;
        float alpha = baseAlpha - (progress * baseAlpha / 2f);

        // Scale for animation
        float scale = easingFunc(progress);

        for (SearchResult result : getResults().values()) {
            // Render the main box with RGB color.
            renderBox(camPos, result.pos(), consumer, pose, r, g, b, alpha, scale);

            // Rendering additional positions (for double chests)
            for (BlockPos otherPos : result.otherPositions()) {
                renderBox(camPos, otherPos, consumer, pose, r, g, b, alpha, scale);
            }
        }

        bufferSource.endBatch(DEBUG_QUADS_NO_DEPTH);
    }

    // Rendering progress for fadeout
    private static float getRenderingProgress(float tickDelta) {
        return Math.min((getTicksSinceSearch() + tickDelta) / WhereIsItConfig.INSTANCE.instance().getCommon().fadeoutTimeTicks, 1f);
    }

    // Basic progress for RGB animation (as in the old code)
    private static float getBaseProgress(long ticks, float delta) {
        float base = ticks + delta;
        base *= WhereIsItConfig.INSTANCE.instance().getClient().highlightTimeFactor;
        return (base % 80) / 80;
    }

    // Smoothing function for scale animation (as in the old code)
    private static float easingFunc(float progress) {
        var power = 32f;
        return (float) ((1 - Math.pow(progress, power)) * (1 - Math.pow(1 - progress, power)) * (1 - (progress / 4f)));
    }

    // Updated renderBox method with scale support
    private static void renderBox(Vec3 cameraPos, BlockPos pos, VertexConsumer consumer,
                                  PoseStack pose, float r, float g, float b, float a, float scale) {
        pose.pushPose();

        // Offset from the camera for the correct position
        final double xOffset = pos.getX() + (0.5 - cameraPos.x);
        final double yOffset = pos.getY() + (0.5 - cameraPos.y);
        final double zOffset = pos.getZ() + (0.5 - cameraPos.z);
        pose.translate(xOffset, yOffset, zOffset);

        // Scaling a cube with animation
        pose.scale(scale * 0.5f, scale * 0.5f, scale * 0.5f);

        Matrix4f matrix = pose.last().pose();
        int color = ARGB.color((int)(a * 255), (int)(r * 255), (int)(g * 255), (int)(b * 255));

        // -Z
        consumer.addVertex(matrix, -1, -1, -1).setColor(color);
        consumer.addVertex(matrix, -1, 1, -1).setColor(color);
        consumer.addVertex(matrix, 1, 1, -1).setColor(color);
        consumer.addVertex(matrix, 1, -1, -1).setColor(color);

        // +Z
        consumer.addVertex(matrix, -1, -1, 1).setColor(color);
        consumer.addVertex(matrix, 1, -1, 1).setColor(color);
        consumer.addVertex(matrix, 1, 1, 1).setColor(color);
        consumer.addVertex(matrix, -1, 1, 1).setColor(color);

        // -Y
        consumer.addVertex(matrix, -1, -1, -1).setColor(color);
        consumer.addVertex(matrix, 1, -1, -1).setColor(color);
        consumer.addVertex(matrix, 1, -1, 1).setColor(color);
        consumer.addVertex(matrix, -1, -1, 1).setColor(color);

        // +Y
        consumer.addVertex(matrix, -1, 1, -1).setColor(color);
        consumer.addVertex(matrix, -1, 1, 1).setColor(color);
        consumer.addVertex(matrix, 1, 1, 1).setColor(color);
        consumer.addVertex(matrix, 1, 1, -1).setColor(color);

        // -X
        consumer.addVertex(matrix, -1, -1, -1).setColor(color);
        consumer.addVertex(matrix, -1, -1, 1).setColor(color);
        consumer.addVertex(matrix, -1, 1, 1).setColor(color);
        consumer.addVertex(matrix, -1, 1, -1).setColor(color);

        // +X
        consumer.addVertex(matrix, 1, -1, -1).setColor(color);
        consumer.addVertex(matrix, 1, 1, -1).setColor(color);
        consumer.addVertex(matrix, 1, 1, 1).setColor(color);
        consumer.addVertex(matrix, 1, -1, 1).setColor(color);

        pose.popPose();
    }
}
