package dev.compat.cosmonauticsdeepair.init;

import dev.compat.cosmonauticsdeepair.CosmonauticsDeepAir;
import dev.compat.cosmonauticsdeepair.block.GravityGeneratorBlock;
import dev.compat.cosmonauticsdeepair.util.GravityGeneratorBlockEntity;
import net.minecraft.core.registries.Registries; // Make sure this import is added!
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModRegistries {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CosmonauticsDeepAir.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CosmonauticsDeepAir.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CosmonauticsDeepAir.MODID);

    public static final DeferredHolder<Block, GravityGeneratorBlock> GRAVITY_GENERATOR = BLOCKS.register("gravity_generator", 
    () -> new GravityGeneratorBlock());

    public static final DeferredHolder<Item, BlockItem> GRAVITY_GENERATOR_ITEM = ITEMS.register("gravity_generator",
        () -> new BlockItem(GRAVITY_GENERATOR.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GravityGeneratorBlockEntity>> GRAVITY_GENERATOR_BE = 
        BLOCK_ENTITIES.register("gravity_generator", () -> BlockEntityType.Builder.of(
            GravityGeneratorBlockEntity::new, 
            GRAVITY_GENERATOR.get()
        ).build(null));
}