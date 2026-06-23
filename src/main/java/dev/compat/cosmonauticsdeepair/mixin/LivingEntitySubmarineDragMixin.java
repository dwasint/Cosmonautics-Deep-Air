package dev.compat.cosmonauticsdeepair.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.compat.cosmonauticsdeepair.util.LifeSupportStatus;
import dev.compat.cosmonauticsdeepair.util.SubmarineLocator;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * RocketNautics' drag math lives entirely inside its own @WrapOperation
 * handler — `pressure` is a local of THAT method, never a local inside
 * LivingEntity#travel() itself. @ModifyVariable on travel() can't see it,
 * which is why the old approach was silently clobbering some unrelated
 * double in vanilla travel() (gravity/vertical-motion territory) — hence
 * the ground-snap on jump.
 *
 * Fix: wrap the exact same setDeltaMovement call (ordinal 3) RocketNautics
 * wraps. Our mixin priority (1100) is higher than their default (1000),
 * so we apply last and sit OUTERMOST in the WrapOperation chain — meaning
 * the x/y/z we receive are pristine, pre-drag values straight out of
 * vanilla travel(). In a breathable compartment we set them directly,
 * which never calls into RocketNautics' handler at all for this tick.
 * Otherwise we forward to original.call(...) so their mod (and anyone
 * else wrapping this call) behaves exactly as before.
 */
@Mixin(value = LivingEntity.class, priority = 1100)
public abstract class LivingEntitySubmarineDragMixin extends Entity {

    public LivingEntitySubmarineDragMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @WrapOperation(
            method = "travel",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V",
                     ordinal = 3)
    )
    private void cosmonautics$bypassPressureDragInBreathable(
            LivingEntity instance, double x, double y, double z, Operation<Void> original) {

        if (cosmonautics$isBreathable(instance)) {
            instance.setDeltaMovement(x, y, z); // pristine values, skip RocketNautics' handler entirely
        } else {
            original.call(instance, x, y, z);
        }
    }

    private boolean cosmonautics$isBreathable(LivingEntity instance) {
        SubLevel sub = SubmarineLocator.findContaining(instance.level(), instance.getX(), instance.getY(), instance.getZ());
        if (sub == null) return false;

        Vector3d localPos = new Vector3d(instance.getX(), instance.getY(), instance.getZ());
        try {
            sub.logicalPose().transformPositionInverse(localPos);
        } catch (Throwable t) {
            return false;
        }

        LifeSupportStatus status = LifeSupportStatus.of(sub.getUniqueId(), localPos, instance.level().getGameTime());
        return status.breathable();
    }
}