package com.billadom.maidpatina.task;

import com.billadom.maidpatina.ModTags;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolActions;

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

    public boolean apply(EntityMaid maid, BlockPos pos, ItemStack tool) {
        Level level = maid.level();
        BlockState oldState = level.getBlockState(pos);
        Optional<BlockState> newState = result(oldState);
        if (newState.isEmpty() || !maid.canDestroyBlock(pos)) {
            return false;
        }

        if (!level.setBlock(pos, newState.get(), Block.UPDATE_ALL_IMMEDIATE)) {
            return false;
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
