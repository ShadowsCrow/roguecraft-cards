package com.roguecards.client;

import com.roguecards.xp.BlueXpOrb;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Desenha a esfera azul. Usa a mesma imagem da esfera normal do jogo
 * e pinta de azul na hora de desenhar, com brilho pulsando entre azul-claro e ciano.
 */
public class BlueXpOrbRenderer extends EntityRenderer<BlueXpOrb> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/experience_orb.png");
    private static final RenderType RENDER_TYPE = RenderType.itemEntityTranslucentCull(TEXTURE);

    public BlueXpOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.15F;
        this.shadowStrength = 0.75F;
    }

    @Override
    protected int getBlockLightLevel(BlueXpOrb entity, BlockPos pos) {
        return Mth.clamp(super.getBlockLightLevel(entity, pos) + 7, 0, 15);
    }

    @Override
    public void render(BlueXpOrb entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        int icon = entity.getIcon();
        float u0 = (icon % 4 * 16) / 64.0F;
        float u1 = (icon % 4 * 16 + 16) / 64.0F;
        float v0 = (icon / 4 * 16) / 64.0F;
        float v1 = (icon / 4 * 16 + 16) / 64.0F;
        float time = (entity.tickCount + partialTicks) / 2.0F;
        int red = (int) ((Mth.sin(time + (float) (Math.PI * 4.0 / 3.0)) + 1.0F) * 0.12F * 255.0F);
        int green = 90 + (int) ((Mth.sin(time) + 1.0F) * 0.5F * 140.0F);
        int blue = 255;
        poseStack.translate(0.0F, 0.1F, 0.0F);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(0.3F, 0.3F, 0.3F);
        VertexConsumer consumer = buffer.getBuffer(RENDER_TYPE);
        PoseStack.Pose pose = poseStack.last();
        vertex(consumer, pose, -0.5F, -0.25F, red, green, blue, u0, v1, packedLight);
        vertex(consumer, pose, 0.5F, -0.25F, red, green, blue, u1, v1, packedLight);
        vertex(consumer, pose, 0.5F, 0.75F, red, green, blue, u1, v0, packedLight);
        vertex(consumer, pose, -0.5F, 0.75F, red, green, blue, u0, v0, packedLight);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, int r, int g, int b, float u, float v, int light) {
        consumer.addVertex(pose, x, y, 0.0F)
                .setColor(r, g, b, 128)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(BlueXpOrb entity) {
        return TEXTURE;
    }
}
