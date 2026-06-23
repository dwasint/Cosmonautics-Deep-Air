package dev.compat.cosmonauticsdeepair;

import dev.compat.cosmonauticsdeepair.event.CosmonauticsDeepAirEvents;
import dev.compat.cosmonauticsdeepair.init.ModRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.capabilities.Capabilities;

@Mod(CosmonauticsDeepAir.MODID)
public final class CosmonauticsDeepAir {

    public static final String MODID = "cosmonauticsdeepair";

    public CosmonauticsDeepAir(IEventBus modEventBus) {
        ModRegistries.BLOCKS.register(modEventBus);
        ModRegistries.ITEMS.register(modEventBus);
        ModRegistries.BLOCK_ENTITIES.register(modEventBus);

        modEventBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> {
            event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK, 
                ModRegistries.GRAVITY_GENERATOR_BE.get(), 
                (be, side) -> be.energyStorage
            );
        });

        modEventBus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) {
                event.accept(ModRegistries.GRAVITY_GENERATOR_ITEM.get());
            }
        });

        NeoForge.EVENT_BUS.register(new CosmonauticsDeepAirEvents());
    }
}