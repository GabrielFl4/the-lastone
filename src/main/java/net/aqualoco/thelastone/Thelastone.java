package net.aqualoco.thelastone;

import net.aqualoco.thelastone.ModAttachments;
import net.aqualoco.thelastone.entity.MolestadorController;
import net.aqualoco.thelastone.entity.MolestadorEntity;
import net.aqualoco.thelastone.entity.ModEntities;
import net.aqualoco.thelastone.insomnia.InsomniaHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Thelastone implements ModInitializer {
	public static final String MOD_ID = "the-lastone";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModAttachments.init();
		ModEntities.init();
		FabricDefaultAttributeRegistry.register(ModEntities.MOLESTADOR, MolestadorEntity.createAttributes());
		InsomniaHandler.init();
		MolestadorController.init();
		LOGGER.info("{} loaded", MOD_ID);
	}
}
