package dev.compat.cosmonauticsdeepair.util;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentDetector;
import dev.compat.cosmonauticsdeepair.init.ModRegistries;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.UUID;

@ParametersAreNonnullByDefault
public class GravityGeneratorBlockEntity extends BlockEntity {

    public final EnergyStorage energyStorage = new EnergyStorage(50000, 2000, 2000);
    
    private UUID currentSubLevelId = null;
    private boolean isEnabled = true;

    public GravityGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.GRAVITY_GENERATOR_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, GravityGeneratorBlockEntity be) {
        if (level == null) return;

        SubLevelAccess subAccess = SableCompanion.INSTANCE.getContaining(level, pos);
        int energyCost = 20; // Default fallback cost

        if (subAccess instanceof SubLevel sub) {
            be.currentSubLevelId = sub.getUniqueId();
            
            //internal block volume affects our cost (love ya deep sea)
            var compartments = CompartmentTracker.getCompartments(be.currentSubLevelId);
            for (CompartmentDetector.Component comp : compartments) {
                if (comp.internal().contains(pos) || comp.hull().contains(pos)) {
                    int roomVolume = comp.internal().size();
                    energyCost = 5 + (int) Math.ceil(roomVolume * 0.5);
                    break;
                }
            }

            boolean canRun = be.isEnabled && level.hasNeighborSignal(pos) && be.energyStorage.getEnergyStored() >= energyCost;

            if (canRun) {
                if (!level.isClientSide) {
                    be.energyStorage.extractEnergy(energyCost, false);
                    be.setChanged(); // Marks chunk dirty so block data persists to disk
                    
                    if (level.getGameTime() % 20 == 0) {
                        level.sendBlockUpdated(pos, state, state, 3);
                    }
                }
            } else {
                be.cleanup();
            }
        } else if (be.currentSubLevelId != null) {
            be.cleanup();
        }
    }

    public boolean isActive() {
        return this.isEnabled && this.currentSubLevelId != null && this.energyStorage.getEnergyStored() > 0;
    }

    private void cleanup() {
        this.currentSubLevelId = null;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        cleanup();
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            loadAdditional(tag, registries);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putBoolean("Enabled", isEnabled);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int stored = tag.getInt("Energy");
        this.energyStorage.extractEnergy(this.energyStorage.getEnergyStored(), false);
        this.energyStorage.receiveEnergy(stored, false);
        this.isEnabled = tag.getBoolean("Enabled");
    }
}