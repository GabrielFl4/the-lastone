package net.aqualoco.thelastone.client;

import net.aqualoco.thelastone.client.render.MolestadorEntityRenderer;
import net.aqualoco.thelastone.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class ThelastoneClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.MOLESTADOR, MolestadorEntityRenderer::new);
    }
}
