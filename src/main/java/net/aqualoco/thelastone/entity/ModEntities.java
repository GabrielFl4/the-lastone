package net.aqualoco.thelastone.entity;

import net.aqualoco.thelastone.Thelastone;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModEntities {
    public static final EntityType<MolestadorEntity> MOLESTADOR;

    public static void init() {
        // no-op


    }


    static {
        Identifier id = Identifier.of(Thelastone.MOD_ID, "molestador");
        RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, id);
        MOLESTADOR = Registry.register(
                Registries.ENTITY_TYPE,
                id,
                FabricEntityTypeBuilder
                        .create(SpawnGroup.MISC, MolestadorEntity::new)
                        .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
                        .trackRangeBlocks(64)
                        .trackedUpdateRate(1)
                        .build(key)
        );
    }

}
