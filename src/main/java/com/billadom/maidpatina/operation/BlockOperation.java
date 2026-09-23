package com.billadom.maidpatina.operation;

import com.billadom.maidpatina.ModTags;
import com.billadom.maidpatina.compat.CreatePatinaCompat;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.items.IItemHandler;

import java.util.Optional;

public enum BlockOperation {
    RUST_REMOVAL {
        @Override
        public boolean isTool(ItemStack stack) {
            return stack.is(ModTags.RUST_REMOVAL_TOOLS) || stack.canPerformAction(ToolActions.AXE_SCRAPE);
        }

        @Override
        public Optional<BlockState> result(BlockState state) {
            // verified: Minecraft 1.20.1 WeatheringCopper#getPrevious(BlockState), 2026-08-03
            // The lookup uses the static NEXT_BY_BLOCK/PREVIOUS_BY_BLOCK pair. Create replaces
            // the memoized delegate at construction time (CopperRegistries#inject) and Create:
            // Patina registers every weathered machine family into it via CopperRegistries
            // #addWeathering, so its blocks (fluid pipes, tanks, valve handles, ...) resolve here too.
            // verified: create-1.20.1-6.0.8-291 CopperRegistries, create-patina-1.1.2-forge PatinaSetBuilder, 2026-09-23
            return WeatheringCopper.getPrevious(state);
        }

        @Override
        protected void playEffect(Level level, BlockPos pos, EntityMaid maid) {
            level.playSound(null, pos, SoundEvents.AXE_SCRAPE, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.levelEvent(null, LevelEvent.PARTICLES_SCRAPE, pos, 0);
        }
    },
    WAXING {
        @Override
        public boolean isTool(ItemStack stack) {
            return stack.is(Items.HONEYCOMB) || stack.is(ModTags.WAXING_ITEMS);
        }

        @Override
        public Optional<BlockState> result(BlockState state) {
            // Create 1.20.1 injects its copper blocks into this vanilla map during common setup.
            // verified: Create foundation/block/CopperRegistries#inject, local 1.20.1 source, 2026-08-03
            // Create: Patina feeds the waxed/unwaxed pairs of every machine family through the
            // same CopperRegistries#addWaxable hook, so its blocks resolve here too.
            // verified: create-patina-1.1.2-forge PatinaSetBuilder, 2026-09-23
            return Optional.ofNullable(HoneycombItem.WAXABLES.get().get(state.getBlock()))
                    .map(block -> block.withPropertiesOf(state));
        }

        @Override
        protected void playEffect(Level level, BlockPos pos, EntityMaid maid) {
            level.levelEvent(null, LevelEvent.PARTICLES_AND_SOUND_WAX_ON, pos, 0);
        }
    };

    public abstract boolean isTool(ItemStack stack);

    public abstract Optional<BlockState> result(BlockState state);

    protected abstract void playEffect(Level level, BlockPos pos, EntityMaid maid);

    public boolean canApply(Level level, BlockPos pos) {
        return result(level.getBlockState(pos)).isPresent();
    }

    public ItemStack findTool(EntityMaid maid) {
        IItemHandler inventory = maid.getAvailableInv(false);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (isTool(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public boolean apply(EntityMaid maid, BlockPos pos, ItemStack tool) {
        Level level = maid.level();
        BlockState oldState = level.getBlockState(pos);
        Optional<BlockState> newState = result(oldState);
        if (newState.isEmpty() || !maid.canDestroyBlock(pos)) {
            return false;
        }

        // Create: Patina converts the whole fluid tank when its tool-weathering config is enabled,
        // otherwise it still swaps the single tank block while preserving the block entity, matching
        // what its AxeItem/HoneycombItem/SandPaperItem mixins do for players.
        // verified: create-patina-1.1.2-forge OxidizeUtil#applyToolWeathering, PatinaConfig, 2026-09-23
        if (CreatePatinaCompat.isFluidTank(level.getBlockEntity(pos))
                && CreatePatinaCompat.applyToolWeathering(oldState, newState.get(), level, pos,
                CreatePatinaCompat.isWholeTankToolWeatheringEnabled())) {
            playEffect(level, pos, maid);
            maid.swing(InteractionHand.MAIN_HAND);
            consume(tool, maid);
            return true;
        }

        // Create: Patina reuses the original block entity class under a different registered type,
        // so swapping the state can rebuild an empty block entity. Save the data first and restore
        // it below, mirroring OxidizeUtil#restoreOriginalBlockEntity.
        // verified: create-patina-1.1.2-forge OxidizeUtil, 2026-09-23
        BlockEntity oldEntity = level.getBlockEntity(pos);
        CompoundTag savedEntity = oldEntity != null ? oldEntity.saveWithFullMetadata() : null;

        if (!level.setBlock(pos, newState.get(), Block.UPDATE_ALL_IMMEDIATE)) {
            return false;
        }

        if (savedEntity != null) {
            BlockEntity newEntity = level.getBlockEntity(pos);
            // Vanilla keeps the same instance when the new state accepts the old type; only reload
            // when the state swap created a different block entity of the same class.
            if (newEntity != null && newEntity != oldEntity && newEntity.getClass() == oldEntity.getClass()) {
                newEntity.load(savedEntity);
                newEntity.setChanged();
            }
        }

        playEffect(level, pos, maid);
        maid.swing(InteractionHand.MAIN_HAND);
        consume(tool, maid);
        return true;
    }

    private static void consume(ItemStack stack, EntityMaid maid) {
        if (stack.isDamageableItem()) {
            stack.hurtAndBreak(1, maid, entity -> entity.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        } else {
            stack.shrink(1);
        }
    }
}
