package net.aqualoco.thelastone.client.render;

import net.aqualoco.thelastone.client.render.feature.CensorBarFeatureRenderer;
import net.aqualoco.thelastone.client.render.state.CensorRenderState;
import net.aqualoco.thelastone.entity.MolestadorEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;

public class MolestadorEntityRenderer extends LivingEntityRenderer<MolestadorEntity, CensorRenderState, PlayerEntityModel> {

    private final PlayerEntityModel classicModel;
    private final PlayerEntityModel slimModel;

    public MolestadorEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        this.classicModel = new PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER), false);
        this.slimModel = new PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER_SLIM), true);
        this.addFeature(new CensorBarFeatureRenderer(this));
    }

    @Override
    public CensorRenderState createRenderState() {
        return new CensorRenderState();
    }

    @Override
    public void updateRenderState(MolestadorEntity entity, CensorRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);

        var client = MinecraftClient.getInstance();
        SkinTextures skin = client.player != null ? client.getSkinProvider().getSkinTextures(client.player.getGameProfile()) : null;
        state.skinTextures = skin != null ? skin : DefaultSkinHelper.getSteve();
        state.texture = state.skinTextures.texture();
        state.jitterTime = entity.age + tickDelta + entity.getJitterSeed() * 0.5f;

        state.hatVisible = true;
        state.capeVisible = true;
        state.jacketVisible = true;
        state.leftSleeveVisible = true;
        state.rightSleeveVisible = true;
        state.leftPantsLegVisible = true;
        state.rightPantsLegVisible = true;

        this.model = state.skinTextures.model() == SkinTextures.Model.SLIM ? slimModel : classicModel;
    }

    @Override
    public Identifier getTexture(CensorRenderState state) {
        return state.texture != null ? state.texture : DefaultSkinHelper.getTexture();
    }

    @Override
    protected boolean hasLabel(MolestadorEntity livingEntity, double d) {
        return false;
    }
}
