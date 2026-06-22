package dev.compat.cosmonauticsdeepair.util;

import com.maxenonyme.createsubmarine.submarine.block.entity.OxygeneDiffuserBlockEntity;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentDetector;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.system.SubmarinePressureSystem;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
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

    /** Per-room check — breathable only if the entity is inside a sealed,
     *  uncompromised compartment that contains an active oxygen diffuser. */
    public static LifeSupportStatus of(UUID subLevelId, Vector3d localPos, long gameTick) {
        boolean sealed = CompartmentTracker.hasAnySealed(subLevelId);
        boolean breached = SubmarinePressureSystem.isBreached(subLevelId);

        Level plotLevel = SubLevelRegistry.getLevel(subLevelId);

        boolean inOxygenatedRoom = false;
        BlockPos entityPlotPos = BlockPos.containing(localPos.x, localPos.y, localPos.z);

        for (CompartmentDetector.Component c : CompartmentTracker.getCompartments(subLevelId)) {
            if (!c.sealed() || CompartmentTracker.isCompromised(subLevelId, c.anchor()))
                continue;
            if (!c.internal().contains(entityPlotPos))
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