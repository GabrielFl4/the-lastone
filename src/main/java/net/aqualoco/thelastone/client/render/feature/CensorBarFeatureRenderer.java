package net.aqualoco.thelastone.client.render.feature;

import net.aqualoco.thelastone.Thelastone;
import net.aqualoco.thelastone.client.render.state.CensorRenderState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;

public class CensorBarFeatureRenderer extends FeatureRenderer<CensorRenderState, PlayerEntityModel> {

    private static final Identifier BAR_TEXTURE = Identifier.of(Thelastone.MOD_ID, "textures/entity/censor.png");

    public CensorBarFeatureRenderer(FeatureRendererContext<CensorRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CensorRenderState state, float limbAngle, float limbDistance) {
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(state.relativeHeadYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(state.pitch));

        // um pouco à frente do rosto
        matrices.translate(0.0, -0.20, -0.29);

        float t = state.jitterTime;
        int frame = (int) (t * 30.0f);
        Random r = Random.create(frame * 9973L);

        float jx = (r.nextFloat() - 0.5f) * 0.10f + MathHelper.sin(t * 60.0f) * 0.02f;
        float jy = (r.nextFloat() - 0.5f) * 0.08f + MathHelper.cos(t * 73.0f) * 0.015f;

        matrices.translate(jx, jy, 0.0);

        float s = 0.25f; // metade do lado do quadrado (ajuste: 0.18..0.35)

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(BAR_TEXTURE));
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f mat = entry.getPositionMatrix();

        // quad quadrado (usa a textura inteira 0..1)
        put(vc, mat, -s, -s, 0f, 1f, light);
        put(vc, mat,  s, -s, 1f, 1f, light);
        put(vc, mat,  s,  s, 1f, 0f, light);
        put(vc, mat, -s,  s, 0f, 0f, light);

        matrices.pop();
    }

    private static void put(VertexConsumer vc, Matrix4f mat, float x, float y, float u, float v, int light) {
    vc.vertex(mat, x, y, 0.0f)
            .color(255, 255, 255, 255)
            .texture(u, v)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(light)
            .normal(0f, 0f, 1f);
    }
}
