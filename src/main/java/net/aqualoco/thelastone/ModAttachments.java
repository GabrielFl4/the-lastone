package net.aqualoco.thelastone;

import net.aqualoco.thelastone.insomnia.InsomniaState;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.util.Identifier;

public final class ModAttachments {
    public static final AttachmentType<InsomniaState> INSOMNIA_STATE = AttachmentRegistry.create(
            Identifier.of(Thelastone.MOD_ID, "insomnia"),
            builder -> builder
                    .persistent(InsomniaState.CODEC)
                    .copyOnDeath()
                    .initializer(InsomniaState::empty)
    );

    private ModAttachments() {
    }

    public static void init() {
        // static init already registers
    }
}
