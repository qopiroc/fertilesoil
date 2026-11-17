package com.example.fertilesoil;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

@Mod(FertileSoilMod.MOD_ID)
public class FertileSoilMod {
    public static final String MOD_ID = "fertilesoil";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);

    public static final DeferredBlock<FertileSoilBlock> FERTILE_SOIL = BLOCKS.registerBlock(
            "fertile_soil",
            FertileSoilBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT)
                    .randomTicks()
                    .strength(0.6F)
                    .sound(SoundType.ROOTED_DIRT));

    public static final DeferredItem<BlockItem> FERTILE_SOIL_ITEM =
            ITEMS.registerSimpleBlockItem("fertile_soil", FERTILE_SOIL);

    public static final Supplier<BlockEntityType<FertileSoilBlockEntity>> FERTILE_SOIL_BE =
            BLOCK_ENTITIES.register("fertile_soil", () ->
                    BlockEntityType.Builder.of(FertileSoilBlockEntity::new, FERTILE_SOIL.get()).build(null));

    public FertileSoilMod(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        modEventBus.addListener(this::addCreative);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(FERTILE_SOIL_ITEM);
        }
    }
}
