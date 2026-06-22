package dev.compat.cosmonauticsdeepair.event;

import dev.compat.cosmonauticsdeepair.util.LifeSupportStatus;
import dev.compat.cosmonauticsdeepair.util.SubmarineLocator;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import org.joml.Vector3d;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public final class CosmonauticsDeepAirEvents {

    public CosmonauticsDeepAirEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingBreathe(LivingBreatheEvent event) {
        LivingEntity entity = event.getEntity();

        SubLevel subLevel = SubmarineLocator.findContaining(
                entity.level(), entity.getX(), entity.getY(), entity.getZ());
        if (subLevel == null) return;

        Vector3d localPos = new Vector3d(entity.getX(), entity.getY(), entity.getZ());
        try {
            subLevel.logicalPose().transformPositionInverse(localPos);
        } catch (Throwable t) {
            return;
        }

        UUID subId = subLevel.getUniqueId();
        // Use the overworld's game time, not the sub-level's
        long gameTick = entity.level().getGameTime();
        LifeSupportStatus status = LifeSupportStatus.of(subId, localPos, gameTick);
        if (status.breathable()) {
            event.setCanBreathe(true);
        }
    }
}