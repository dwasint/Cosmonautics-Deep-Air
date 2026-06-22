package dev.compat.cosmonauticsdeepair.util;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

/**
 * Finds the Sable {@link SubLevel} (submarine sub-level) that contains a
 * given world position, using the same local-space bounding-box test that
 * Create Submarine's own "/submarine info" and "/submarine findhole"
 * commands use to figure out which sub-level a player is standing in/on.
 *
 * <p>The world position is transformed into the sub-level's local space via
 * {@code logicalPose().transformPositionInverse(...)} and tested against the
 * plot's {@link BoundingBox3ic}, with a small margin so entities standing on
 * deck or just outside the hull are still recognized.</p>
 */
public final class SubmarineLocator {

    /** Margin (in blocks) added around the plot bounding box for the containment test. */
    private static final double MARGIN = 2.0;

    private SubmarineLocator() {
    }

    /**
     * @return the SubLevel whose plot bounding box contains the given world
     * position, or {@code null} if the entity isn't standing in/on any
     * known submarine sub-level in this dimension.
     */
    public static SubLevel findContaining(Level level, double worldX, double worldY, double worldZ) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }

        Vector3d worldPos = new Vector3d(worldX, worldY, worldZ);

        for (SubLevel sub : container.getAllSubLevels()) {
            if (sub.getPlot() == null) {
                continue;
            }

            BoundingBox3ic bounds = sub.getPlot().getBoundingBox();
            if (bounds == null) {
                continue;
            }

            Vector3d local = new Vector3d(worldPos);
            try {
                sub.logicalPose().transformPositionInverse(local);
            } catch (Throwable t) {
                // Sub-level pose not ready/valid this tick - skip it rather than crash.
                continue;
            }

            if (isWithinBounds(local, bounds)) {
                return sub;
            }
        }

        return null;
    }

    /** Convenience overload that reads the entity's current position. */
    public static SubLevel findContaining(Entity entity) {
        return findContaining(entity.level(), entity.getX(), entity.getY(), entity.getZ());
    }

    private static boolean isWithinBounds(Vector3d local, BoundingBox3ic bounds) {
        return local.x >= bounds.minX() - MARGIN && local.x <= bounds.maxX() + MARGIN
                && local.y >= bounds.minY() - MARGIN && local.y <= bounds.maxY() + MARGIN
                && local.z >= bounds.minZ() - MARGIN && local.z <= bounds.maxZ() + MARGIN;
    }
}
