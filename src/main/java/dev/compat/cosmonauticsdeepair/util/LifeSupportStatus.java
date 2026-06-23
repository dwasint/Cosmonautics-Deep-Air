package dev.compat.cosmonauticsdeepair.util;

import com.maxenonyme.createsubmarine.submarine.block.entity.OxygeneDiffuserBlockEntity;
import dev.compat.cosmonauticsdeepair.util.GravityGeneratorBlockEntity;
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

public record LifeSupportStatus(boolean sealed, boolean breached, boolean breathable, boolean hasGravity) {

    private static final Map<UUID, Map<BlockPos, Boolean>> DIFFUSER_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<BlockPos, Boolean>> GRAVITY_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> CACHE_TIMESTAMPS = new ConcurrentHashMap<>();
    private static final int CACHE_TTL = 40;

    /** Original global fallback check */
    public static LifeSupportStatus of(UUID subLevelId) {
        boolean sealed = CompartmentTracker.hasAnySealed(subLevelId);
        boolean breached = SubmarinePressureSystem.isBreached(subLevelId);
        boolean breathable = sealed && !breached;
        return new LifeSupportStatus(sealed, breached, breathable, false);
    }

    public static LifeSupportStatus of(UUID subLevelId, Vector3d localPos, long gameTick) {
        boolean sealed = CompartmentTracker.hasAnySealed(subLevelId);
        boolean breached = SubmarinePressureSystem.isBreached(subLevelId);

        Level plotLevel = SubLevelRegistry.getLevel(subLevelId);
        boolean inOxygenatedRoom = false;
        boolean inGravityRoom = false;

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

            // Gravity only functions if sealed and not breached!
            if (!c.sealed() || isCompromised)
                continue;

            boolean inside = false;
            for (BlockPos pos : checkPositions) {
                if (c.internal().contains(pos)) {
                    inside = true;
                    break;
                }
                
                if (plotLevel != null && c.hull().contains(pos)) {
                    BlockState state = plotLevel.getBlockState(pos);
                    if (!state.isSuffocating(plotLevel, pos)) {
                        inside = true;
                        break;
                    }
                }
            }

            if (!inside)
                continue;

            updateCompartmentCaches(subLevelId, plotLevel, gameTick);

            if (DIFFUSER_CACHE.getOrDefault(subLevelId, Map.of()).getOrDefault(c.anchor(), false)) {
                inOxygenatedRoom = true;
            }
            if (GRAVITY_CACHE.getOrDefault(subLevelId, Map.of()).getOrDefault(c.anchor(), false)) {
                inGravityRoom = true;
            }
            break;
        }

        boolean breathable = inOxygenatedRoom && !breached;
        // Gravity applies if the specific sealed compartment contains an online generator
        boolean hasGravity = inGravityRoom && !breached; 

        return new LifeSupportStatus(sealed, breached, breathable, hasGravity);
    }

    private static void updateCompartmentCaches(UUID subLevelId, Level plotLevel, long gameTick) {
        if (plotLevel == null) return;

        Long lastCheck = CACHE_TIMESTAMPS.get(subLevelId);
        if (lastCheck != null && gameTick - lastCheck < CACHE_TTL) return;

        // Dynamic cleanup
        if (CACHE_TIMESTAMPS.size() > 64) {
            long cutoff = gameTick - 200;
            CACHE_TIMESTAMPS.entrySet().removeIf(e -> e.getValue() < cutoff);
            DIFFUSER_CACHE.keySet().removeIf(k -> !CACHE_TIMESTAMPS.containsKey(k));
            GRAVITY_CACHE.keySet().removeIf(k -> !CACHE_TIMESTAMPS.containsKey(k));
        }

        Map<BlockPos, Boolean> oxyCache = DIFFUSER_CACHE.computeIfAbsent(subLevelId, k -> new ConcurrentHashMap<>());
        Map<BlockPos, Boolean> gravCache = GRAVITY_CACHE.computeIfAbsent(subLevelId, k -> new ConcurrentHashMap<>());

        oxyCache.clear();
        gravCache.clear();

        for (CompartmentDetector.Component comp : CompartmentTracker.getCompartments(subLevelId)) {
            boolean foundOxy = false;
            boolean foundGrav = false;

            // Scan Room Interior
            for (BlockPos p : comp.internal()) {
                var be = plotLevel.getBlockEntity(p);
                if (!foundOxy && be instanceof OxygeneDiffuserBlockEntity diffuser) {
                    if (diffuser.oxygenTank.getFluidAmount() > 0 && plotLevel.hasNeighborSignal(p)) {
                        foundOxy = true;
                    }
                }
                if (!foundGrav && be instanceof GravityGeneratorBlockEntity gravityGen) {
                    if (gravityGen.isActive()) {
                        foundGrav = true;
                    }
                }
                if (foundOxy && foundGrav) break;
            }

            // Scan Room Frame / Hull
            if (!foundOxy || !foundGrav) {
                for (BlockPos p : comp.hull()) {
                    var be = plotLevel.getBlockEntity(p);
                    if (!foundOxy && be instanceof OxygeneDiffuserBlockEntity diffuser) {
                        if (diffuser.oxygenTank.getFluidAmount() > 0 && plotLevel.hasNeighborSignal(p)) {
                            foundOxy = true;
                        }
                    }
                    if (!foundGrav && be instanceof GravityGeneratorBlockEntity gravityGen) {
                        if (gravityGen.isActive()) {
                            foundGrav = true;
                        }
                    }
                    if (foundOxy && foundGrav) break;
                }
            }

            oxyCache.put(comp.anchor(), foundOxy);
            gravCache.put(comp.anchor(), foundGrav);
        }
        CACHE_TIMESTAMPS.put(subLevelId, gameTick);
    }
}