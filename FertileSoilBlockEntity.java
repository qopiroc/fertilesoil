package com.example.fertilesoil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Хранит игровое время (gameTime), до которого действует костная мука.
 * Ничего не тикает каждый тик — время просто сравнивается при необходимости.
 */
public class FertileSoilBlockEntity extends BlockEntity {
    private long fertilizedUntil = 0L;

    public FertileSoilBlockEntity(BlockPos pos, BlockState state) {
        super(FertileSoilMod.FERTILE_SOIL_BE.get(), pos, state);
    }

    public long getRemainingTicks(long gameTime) {
        return Math.max(0L, fertilizedUntil - gameTime);
    }

    /** @return false, если лимит уже достигнут и костная мука не потрачена */
    public boolean addDuration(long gameTime, long amount, long max) {
        long remaining = getRemainingTicks(gameTime);
        if (remaining >= max) {
            return false;
        }
        fertilizedUntil = gameTime + Math.min(max, remaining + amount);
        setChanged();
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("FertilizedUntil", fertilizedUntil);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fertilizedUntil = tag.getLong("FertilizedUntil");
    }
}
