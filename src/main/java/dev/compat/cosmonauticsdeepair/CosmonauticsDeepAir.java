package dev.compat.cosmonauticsdeepair;

import dev.compat.cosmonauticsdeepair.event.CosmonauticsDeepAirEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(CosmonauticsDeepAir.MODID)
public final class CosmonauticsDeepAir {

    public static final String MODID = "cosmonauticsdeepair";

    public CosmonauticsDeepAir(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(new CosmonauticsDeepAirEvents());
    }
}