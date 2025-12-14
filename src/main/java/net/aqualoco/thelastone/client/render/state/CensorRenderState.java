package net.aqualoco.thelastone.client.render.state;

import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.Identifier;

/**
 * Render state para a entidade censor, derivado do estado de player.
 * Carrega a textura escolhida e um tempo para jitter.
 */
public class CensorRenderState extends PlayerEntityRenderState {
    public Identifier texture;
    public float jitterTime;
}
