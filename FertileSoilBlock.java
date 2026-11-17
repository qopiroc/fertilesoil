package com.example.fertilesoil;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.TriState;

public class FertileSoilBlock extends Block implements EntityBlock {
    public static final MapCodec<FertileSoilBlock> CODEC = simpleCodec(FertileSoilBlock::new);
    public static final BooleanProperty FERTILIZED = BooleanProperty.create("fertilized");

    // ================== НАСТРОЙКИ ==================
    /** Скорость роста без костной муки (в раз быстрее обычного). */
    public static final double NORMAL_MULTIPLIER = 2.5D;
    /** Скорость роста с костной мукой. */
    public static final double BONE_MEAL_MULTIPLIER = 5.0D;
    /** Сколько тиков работает одна костная мука. 24000 = 1 игровой день (20 минут реального времени). */
    public static final long BONE_MEAL_DURATION_TICKS = 24000L;
    /** Максимальный запас костной муки в одном блоке (в тиках). 7 игровых дней. */
    public static final long MAX_DURATION_TICKS = 7L * 24000L;
    // ===============================================

    public FertileSoilBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FERTILIZED, false));
    }

    @Override
    protected MapCodec<FertileSoilBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FERTILIZED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FertileSoilBlockEntity(pos, state);
    }

    // ---------- Любое растение можно сажать на эту землю ----------
    @Override
    public TriState canSustainPlant(BlockState state, BlockGetter level, BlockPos soilPosition,
                                    Direction facing, BlockState plant) {
        if (facing == Direction.UP && isGrowable(plant)) {
            return TriState.TRUE;
        }
        return TriState.DEFAULT;
    }

    /** Блок, который мы ускоряем: любые кусты/посевы (CropBlock, StemBlock, моды...) и всё из тега crops. */
    private static boolean isGrowable(BlockState state) {
        return state.getBlock() instanceof BushBlock || state.is(BlockTags.CROPS);
    }

    // ---------- Ускорение роста ----------
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean boosted = isBoosted(level, pos);

        // обновляем внешний вид, если костная мука закончилась
        if (state.getValue(FERTILIZED) != boosted) {
            level.setBlock(pos, state.setValue(FERTILIZED, boosted), 2);
        }

        BlockPos above = pos.above();
        BlockState plant = level.getBlockState(above);
        if (!isGrowable(plant) || !plant.isRandomlyTicking()) {
            return;
        }

        // Само растение уже получает свои обычные random tick'и (1x).
        // Нам нужно добавить (множитель - 1) дополнительных тиков в среднем.
        double extra = (boosted ? BONE_MEAL_MULTIPLIER : NORMAL_MULTIPLIER) - 1.0D;
        int times = (int) extra;
        if (random.nextDouble() < extra - times) {
            times++;
        }

        for (int i = 0; i < times; i++) {
            plant = level.getBlockState(above);
            if (!isGrowable(plant) || !plant.isRandomlyTicking()) {
                break; // растение уже созрело/исчезло
            }
            plant.randomTick(level, above, random);
        }
    }

    public static boolean isBoosted(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FertileSoilBlockEntity soil
                && soil.getRemainingTicks(level.getGameTime()) > 0L;
    }

    // ---------- Костная мука ----------
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.BONE_MEAL)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }

        if (level.getBlockEntity(pos) instanceof FertileSoilBlockEntity soil) {
            long now = level.getGameTime();
            if (!soil.addDuration(now, BONE_MEAL_DURATION_TICKS, MAX_DURATION_TICKS)) {
                player.displayClientMessage(Component.translatable("message.fertilesoil.full"), true);
                return ItemInteractionResult.CONSUME;
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            if (!state.getValue(FERTILIZED)) {
                level.setBlock(pos, state.setValue(FERTILIZED, true), 3);
            }
            level.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            ((ServerLevel) level).sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D, 8, 0.3D, 0.2D, 0.3D, 0.0D);
            showRemaining(player, soil.getRemainingTicks(now));
        }
        return ItemInteractionResult.sidedSuccess(false);
    }

    /** Клик пустой рукой показывает, сколько ещё действует костная мука. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof FertileSoilBlockEntity soil) {
            long remaining = soil.getRemainingTicks(level.getGameTime());
            if (remaining > 0) {
                showRemaining(player, remaining);
            } else {
                player.displayClientMessage(Component.translatable("message.fertilesoil.inactive"), true);
            }
        }
        return InteractionResult.CONSUME;
    }

    private static void showRemaining(Player player, long ticks) {
        long seconds = ticks / 20L;
        String time = String.format("%d:%02d", seconds / 60L, seconds % 60L);
        player.displayClientMessage(Component.translatable("message.fertilesoil.remaining", time), true);
    }

    // ---------- Частицы, пока работает костная мука ----------
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(FERTILIZED) && random.nextInt(12) == 0) {
            level.addParticle(ParticleTypes.HAPPY_VILLAGER,
                    pos.getX() + random.nextDouble(), pos.getY() + 1.05D, pos.getZ() + random.nextDouble(),
                    0.0D, 0.0D, 0.0D);
        }
    }
}
