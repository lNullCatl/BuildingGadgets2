package com.direwolf20.buildinggadgets2.client.blockentityrenders;

import com.direwolf20.buildinggadgets2.client.renderer.DireVertexConsumer;
import com.direwolf20.buildinggadgets2.client.renderer.DireVertexConsumerSquished;
import com.direwolf20.buildinggadgets2.client.renderer.OurRenderTypes;
import com.direwolf20.buildinggadgets2.client.renderer.RenderFluidBlock;
import com.direwolf20.buildinggadgets2.common.blockentities.RenderBlockBE;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RenderBlockBER implements BlockEntityRenderer<RenderBlockBE, RenderBlockBERState> {
    public RenderBlockBER(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public RenderBlockBERState createRenderState() {
        return new RenderBlockBERState();
    }

    @Override
    public void extractRenderState(
            RenderBlockBE be,
            RenderBlockBERState state,
            float partialTicks,
            Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
        state.renderType = be.renderType;
        state.renderBlock = be.renderBlock;
        state.shrinking = be.shrinking;
        state.level = (ClientLevel) be.getLevel();

        float nowScale = (float) be.drawSize / (float) be.getMaxSize();
        float nextScale = (float) be.nextDrawSize() / (float) be.getMaxSize();
        state.scale = Mth.clamp(Mth.lerp(partialTicks, nowScale, nextScale), 0f, 1f);

        // Real-world light at the BE's position — base extractRenderState already did this, but it reads the
        // block below us (the RenderBlock) rather than the block we're animating into. Re-sample from the
        // client level directly so the ghost picks up the surrounding light, not the air pocket we're in.
        if (state.level != null) {
            state.lightCoords = LevelRenderer.getLightCoords(state.level, state.blockPos);
        }

        // Resolve biome-aware block tints (grass, leaves, water, etc.). BlockColors.getTintSources
        // returns one BlockTintSource per tint layer the block uses; evaluate each against the
        // real level so the preview picks up biome-correct foliage/grass color.
        if (state.renderBlock != null && state.level != null) {
            java.util.List<BlockTintSource> sources = Minecraft.getInstance().getBlockColors().getTintSources(state.renderBlock);
            if (!sources.isEmpty()) {
                state.tintLayers = new int[sources.size()];
                for (int i = 0; i < sources.size(); i++) {
                    state.tintLayers[i] = sources.get(i).colorInWorld(state.renderBlock, state.level, state.blockPos);
                }
            } else {
                state.tintLayers = new int[]{-1};
            }
        }
    }

    @Override
    public void submit(
            RenderBlockBERState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (state.renderBlock == null) return;
        if (state.level == null) return;

        BlockState renderBlock = state.renderBlock;
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(renderBlock);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(), parts);

        byte type = state.renderType;
        if (type == 0) {
            renderGrow(state, poseStack, collector, renderBlock, parts);
        } else if (type == 1) {
            renderFade(state, poseStack, collector, renderBlock, parts);
        } else if (type == 2 || type == 3 || type == 4) {
            boolean adjustUV = type != 2;
            boolean bottomUp = type == 4;
            renderSquished(state, poseStack, collector, renderBlock, parts, adjustUV, bottomUp);
        } else if (type == 5) {
            renderSquishedSnap(state, poseStack, collector, renderBlock, parts);
        } else {
            renderGrow(state, poseStack, collector, renderBlock, parts);
        }
    }

    /**
     * renderType 0: a simple grow-from-center animation. Non-fluids use submitBlockModel (AO-lit — the
     * vanilla-equivalent code path). Fluids go through submitCustomGeometry because FluidModel lives in
     * a separate model set that submitBlockModel can't consume.
     */
    private void renderGrow(
            RenderBlockBERState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            BlockState renderBlock,
            List<BlockStateModelPart> parts) {
        float scale = state.scale;
        poseStack.pushPose();
        poseStack.translate((1 - scale) / 2, (1 - scale) / 2, (1 - scale) / 2);
        poseStack.scale(scale, scale, scale);

        if (renderBlock.getFluidState().isEmpty()) {
            if (!parts.isEmpty()) {
                collector.submitBlockModel(
                        poseStack,
                        Sheets.cutoutBlockSheet(),
                        parts,
                        state.tintLayers,
                        state.lightCoords,
                        OverlayTexture.NO_OVERLAY,
                        0);
            }
        } else {
            collector.submitCustomGeometry(poseStack, Sheets.cutoutBlockSheet(), (pose, buffer) ->
                    RenderFluidBlock.renderFluidBlock(renderBlock, state.level, state.blockPos, poseStack, buffer, true));
        }

        poseStack.popPose();
    }

    /**
     * renderType 1: fade the block in with per-vertex alpha modulation. submitBlockModel can't do alpha
     * modulation (§11 open question #4 in RENDER_PORTING.md), so we route through submitCustomGeometry and
     * apply the fade by stamping it onto each quad's QuadInstance color — putBakedQuad multiplies
     * instance color × baked vertex color, so an alpha-only instance color (0xAA_FFFFFF) fades without
     * touching RGB.
     */
    private void renderFade(
            RenderBlockBERState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            BlockState renderBlock,
            List<BlockStateModelPart> parts) {
        BlockPos pos = state.blockPos;
        float scale = state.scale;
        float alpha = Mth.lerp(scale, 0.25f, 1f);
        int instanceColor = ARGB.color(Math.round(alpha * 255f), 255, 255, 255);
        boolean isSolid = renderBlock.isSolidRender();
        RenderType renderType = isSolid ? OurRenderTypes.RenderBlockFade : OurRenderTypes.RenderBlockFadeNoCull;

        if (!renderBlock.getFluidState().isEmpty()) {
            collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
                VertexConsumer wrapped = new DireVertexConsumer(buffer, alpha);
                RenderFluidBlock.renderFluidBlock(renderBlock, state.level, pos, poseStack, wrapped, false);
            });
            return;
        }

        if (parts.isEmpty()) return;

        collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
            QuadInstance instance = new QuadInstance();
            instance.setLightCoords(state.lightCoords);
            for (BlockStateModelPart part : parts) {
                writePartQuads(part, buffer, pose, instance, state.tintLayers, instanceColor);
            }
        });
    }

    /**
     * renderType 2/3/4: squish/stretch animation. Needs per-vertex position rewriting + optional UV
     * adjustment that only DireVertexConsumerSquished knows how to do. submitCustomGeometry gives us the
     * raw VertexConsumer we wrap. AO is intentionally dropped on this path (§5.4 — AmbientOcclusionFace
     * is gone, and reimplementing it would be hundreds of lines). Cosmetic regression per Decision B.
     */
    private void renderSquished(
            RenderBlockBERState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            BlockState renderBlock,
            List<BlockStateModelPart> parts,
            boolean adjustUV,
            boolean bottomUp) {
        BlockPos pos = state.blockPos;
        float scale = Mth.lerp(state.scale, 0f, 1f);
        boolean isSolid = renderBlock.isSolidRender();
        RenderType renderType = isSolid ? OurRenderTypes.RenderBlockBackface : OurRenderTypes.RenderBlockFadeNoCull;
        // UV adjust is a solid-only trick (it assumes the block face stretches from minV to maxV across
        // a full texture tile). Transparent blocks get a flat UV pass.
        boolean effectiveAdjustUV = adjustUV && isSolid;

        if (!renderBlock.getFluidState().isEmpty()) {
            collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
                DireVertexConsumerSquished squished = new DireVertexConsumerSquished(
                        buffer, 0, 0, 0, 1, scale, 1, pose.pose());
                squished.adjustUV = effectiveAdjustUV;
                squished.bottomUp = bottomUp;
                RenderFluidBlock.renderFluidBlock(renderBlock, state.level, pos, poseStack, squished, true);
            });
            return;
        }

        if (parts.isEmpty()) return;

        collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
            DireVertexConsumerSquished squished = new DireVertexConsumerSquished(
                    buffer, 0, 0, 0, 1, scale, 1, pose.pose());
            squished.adjustUV = effectiveAdjustUV;
            squished.bottomUp = bottomUp;
            QuadInstance instance = new QuadInstance();
            instance.setLightCoords(state.lightCoords);
            for (BlockStateModelPart part : parts) {
                writePartQuadsSquished(part, squished, pose, instance, state.tintLayers, -1);
            }
        });
    }

    /**
     * renderType 5: "snap" variant of the squish animation with a darkness modulation that fades as the
     * block settles. Darkness is applied via the QuadInstance color (RGB multiplied, alpha left at 255)
     * — same trick as renderFade, inverted. Dropped scale < 0.1f for non-shrinking blocks to match the
     * 1.21.1 behavior.
     */
    private void renderSquishedSnap(
            RenderBlockBERState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            BlockState renderBlock,
            List<BlockStateModelPart> parts) {
        if (!state.shrinking && state.scale < 0.1f) return;
        BlockPos pos = state.blockPos;
        float darkness = Mth.lerp(state.scale, 0.25f, 1f);
        float scale = Mth.lerp(state.scale, 0.75f, 1f);
        int darkChannel = Math.round(darkness * 255f);
        int instanceColor = ARGB.color(255, darkChannel, darkChannel, darkChannel);
        boolean isSolid = renderBlock.isSolidRender();
        RenderType renderType = Sheets.cutoutBlockSheet();
        boolean effectiveAdjustUV = isSolid;

        if (!renderBlock.getFluidState().isEmpty()) {
            collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
                DireVertexConsumerSquished squished = new DireVertexConsumerSquished(
                        buffer, 0, 0, 0, 1, scale, 1, pose.pose());
                squished.adjustUV = effectiveAdjustUV;
                squished.bottomUp = false;
                RenderFluidBlock.renderFluidBlock(renderBlock, state.level, pos, poseStack, squished, true);
            });
            return;
        }

        if (parts.isEmpty()) return;

        collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
            DireVertexConsumerSquished squished = new DireVertexConsumerSquished(
                    buffer, 0, 0, 0, 1, scale, 1, pose.pose());
            squished.adjustUV = effectiveAdjustUV;
            squished.bottomUp = false;
            QuadInstance instance = new QuadInstance();
            instance.setLightCoords(state.lightCoords);
            for (BlockStateModelPart part : parts) {
                writePartQuadsSquished(part, squished, pose, instance, state.tintLayers, instanceColor);
            }
        });
    }

    private static void writePartQuads(
            BlockStateModelPart part,
            VertexConsumer buffer,
            PoseStack.Pose pose,
            QuadInstance instance,
            int[] tintLayers,
            int baseColor) {
        for (Direction dir : Direction.values()) {
            for (BakedQuad quad : part.getQuads(dir)) {
                applyTint(instance, quad, tintLayers, baseColor);
                buffer.putBakedQuad(pose, quad, instance);
            }
        }
        for (BakedQuad quad : part.getQuads(null)) {
            applyTint(instance, quad, tintLayers, baseColor);
            buffer.putBakedQuad(pose, quad, instance);
        }
    }

    private static void applyTint(QuadInstance instance, BakedQuad quad, int[] tintLayers, int baseColor) {
        int tintIndex = quad.materialInfo().tintIndex();
        if (tintIndex != -1 && tintIndex < tintLayers.length) {
            int tint = tintLayers[tintIndex];
            // Multiply tint RGB with baseColor's RGB, keep baseColor's alpha.
            int r = ((tint >> 16) & 0xFF) * ((baseColor >> 16) & 0xFF) / 255;
            int g = ((tint >> 8) & 0xFF) * ((baseColor >> 8) & 0xFF) / 255;
            int b = (tint & 0xFF) * (baseColor & 0xFF) / 255;
            int a = (baseColor >> 24) & 0xFF;
            instance.setColor(ARGB.color(a, r, g, b));
        } else {
            instance.setColor(baseColor);
        }
    }

    /**
     * Squished variant of the quad walker. The squished wrapper intercepts the 3-arg addVertex and
     * setUv calls that VertexConsumer's default putBakedQuad(Pose, quad, instance) drives. The wrapper
     * inverts the provided pose matrix to get back to model-space before applying its squish math, so
     * the matrix we hand it must be the same one putBakedQuad uses — which is {@code pose.pose()}.
     * Sprite + direction must be set before each putBakedQuad so the UV-adjust branch has the data.
     */
    private static void writePartQuadsSquished(
            BlockStateModelPart part,
            DireVertexConsumerSquished squished,
            PoseStack.Pose pose,
            QuadInstance instance,
            int[] tintLayers,
            int baseColor) {
        for (Direction dir : Direction.values()) {
            for (BakedQuad quad : part.getQuads(dir)) {
                applyTint(instance, quad, tintLayers, baseColor);
                squished.setSprite(quad.materialInfo().sprite());
                squished.setDirection(dir);
                squished.putBakedQuad(pose, quad, instance);
            }
        }
        for (BakedQuad quad : part.getQuads(null)) {
            applyTint(instance, quad, tintLayers, baseColor);
            squished.setSprite(quad.materialInfo().sprite());
            squished.setDirection(null);
            squished.putBakedQuad(pose, quad, instance);
        }
    }
}
