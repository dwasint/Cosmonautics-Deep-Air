package dev.compat.cosmonauticsdeepair.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.compat.cosmonauticsdeepair.util.LifeSupportStatus;
import dev.compat.cosmonauticsdeepair.util.SubmarineLocator;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

@Mixin(value = Entity.class, priority = 1100)
public abstract class EntitySubmarineGravityMixin {

    @Shadow public abstract Level level();
    @Shadow public abstract double getX();
    @Shadow public abstract double getY();
    @Shadow public abstract double getZ();

    @ModifyReturnValue(method = "getGravity", at = @At("RETURN"))
    private double cosmonautics$restoreGravityInBreathableCompartment(double modified) {

        SubLevel sub = SubmarineLocator.findContaining(level(), getX(), getY(), getZ());
        if (sub == null) return modified;

        Vector3d localPos = new Vector3d(getX(), getY(), getZ());
        try {
            sub.logicalPose().transformPositionInverse(localPos);
        } catch (Throwable t) {
            return modified;
        }

        UUID subId = sub.getUniqueId();
        LifeSupportStatus status = LifeSupportStatus.of(subId, localPos, level().getGameTime());
        
        // Changed condition to depend entirely on the operational generator status!
        return status.hasGravity() ? 0.06 : modified; 
    }
}