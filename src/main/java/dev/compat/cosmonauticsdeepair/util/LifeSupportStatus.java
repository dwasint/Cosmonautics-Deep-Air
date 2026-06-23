package dev.compat.cosmonauticsdeepair.util;

import com.maxenonyme.createsubmarine.submarine.block.entity.OxygeneDiffuserBlockEntity;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentDetector;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.system.SubmarinePressureSystem;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public record LifeSupportStatus(boolean sealed, boolean breached, boolean breathable) {

    private static final Map<UUID, Map<BlockPos, Boolean>> DIFFUSER_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> CACHE_TIMESTAMPS = new ConcurrentHashMap<>();
    private static final int CACHE_TTL = 40;

    /** Original global check — kept for anything else that may use it. */
    public static LifeSupportStatus of(UUID subLevelId) {
        boolean sealed = CompartmentTracker.hasAnySealed(subLevelId);
        boolean breached = SubmarinePressureSystem.isBreached(subLevelId);
        boolean breathable = sealed && !breached;
        return new LifeSupportStatus(sealed, breached, breathable);
    }

    public static LifeSupportStatus of(UUID subLevelId, Vector3d localPos, long gameTick) {
        boolean sealed = CompartmentTracker.hasAnySealed(subLevelId);
        boolean breached = SubmarinePressureSystem.isBreached(subLevelId);

        Level plotLevel = SubLevelRegistry.getLevel(subLevelId);

        boolean inOxygenatedRoom = false;

        double x = localPos.x;
        double y = localPos.y;
        double z = localPos.z;
        
        BlockPos[] checkPositions = new BlockPos[] {
            BlockPos.containing(x, y + 0.1, z),       // Center feet
            BlockPos.containing(x, y + 0.8, z),       // Center torso
            BlockPos.containing(x + 0.2, y + 0.1, z), // East offset
            BlockPos.containing(x - 0.2, y + 0.1, z), // West offset
            BlockPos.containing(x, y + 0.1, z + 0.2), // South offset
            BlockPos.containing(x, y + 0.1, z - 0.2)  // North offset
        };

        for (CompartmentDetector.Component c : CompartmentTracker.getCompartments(subLevelId)) {
            boolean isCompromised = false;
            try {
                isCompromised = CompartmentTracker.isCompromised(subLevelId, c.anchor());
            } catch (NullPointerException e) {
                isCompromised = false; 
            }

            if (!c.sealed() || isCompromised)
                continue;

            boolean inside = false;
            for (BlockPos pos : checkPositions) {
                // Scenario A: Standard clear interior air block
                if (c.internal().contains(pos)) {
                    inside = true;
                    break;
                }
                
                // Scenario B: The position maps to the hull set because a lever/button is attached there.
                // We check if the player is technically occupying it and if it's a passable/non-suffocating block.
                if (plotLevel != null && c.hull().contains(pos)) {
                    BlockState state = plotLevel.getBlockState(pos);
                    // If the block doesn't suffocate the player (levers, buttons, torches, air, etc.), 
                    // it's a partial block inside the room boundary, actually disgusting
                    if (!state.isSuffocating(plotLevel, pos)) {
                        inside = true;
                        break;
                    }
                }
            }

            if (!inside)
                continue;

            if (compartmentHasDiffuser(subLevelId, c, plotLevel, gameTick))
                inOxygenatedRoom = true;
            break;
        }

        boolean breathable = inOxygenatedRoom && !breached;
        return new LifeSupportStatus(sealed, breached, breathable);
    }

    private static boolean compartmentHasDiffuser(UUID subLevelId, CompartmentDetector.Component c,
                                                   Level plotLevel, long gameTick) {
        if (plotLevel == null) return false;

        Long lastCheck = CACHE_TIMESTAMPS.get(subLevelId);
        Map<BlockPos, Boolean> cache = DIFFUSER_CACHE.computeIfAbsent(subLevelId, k -> new ConcurrentHashMap<>());

        if (lastCheck == null || gameTick - lastCheck >= CACHE_TTL) {
            if (CACHE_TIMESTAMPS.size() > 64) {
                long cutoff = gameTick - 200;
                CACHE_TIMESTAMPS.entrySet().removeIf(e -> e.getValue() < cutoff);
                DIFFUSER_CACHE.keySet().removeIf(k -> !CACHE_TIMESTAMPS.containsKey(k));
            }

            cache.clear();
            for (CompartmentDetector.Component comp : CompartmentTracker.getCompartments(subLevelId)) {
                boolean found = false;
                for (BlockPos p : comp.internal()) {
                    if (plotLevel.getBlockEntity(p) instanceof OxygeneDiffuserBlockEntity diffuser
                            && diffuser.oxygenTank.getFluidAmount() > 0
                            && plotLevel.hasNeighborSignal(p)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    for (BlockPos p : comp.hull()) {
                        if (plotLevel.getBlockEntity(p) instanceof OxygeneDiffuserBlockEntity diffuser
                                && diffuser.oxygenTank.getFluidAmount() > 0
                                && plotLevel.hasNeighborSignal(p)) {
                            found = true;
                            break;
                        }
                    }
                }
                cache.put(comp.anchor(), found);
            }
            CACHE_TIMESTAMPS.put(subLevelId, gameTick);
        }

        return cache.getOrDefault(c.anchor(), false);
    }
}