package com.billadom.maidpatina.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Method;

/**
 * Optional bridge to Create: Patina tool weathering, resolved by class name so this mod
 * compiles and runs without Create or Create: Patina installed.
 */
public final class CreatePatinaCompat {
    private static final String OXIDIZE_UTIL_CLASS = "io.github.mechtasnezhevna.createpatina.util.OxidizeUtil";
    private static final String PATINA_CONFIG_CLASS = "io.github.mechtasnezhevna.createpatina.PatinaConfig";
    private static final String FLUID_TANK_BE_CLASS = "com.simibubi.create.content.fluids.tank.FluidTankBlockEntity";
    private static final String CONFIG_FIELD = "CONFIG";
    private static final String WHOLE_TANK_CONFIG_FIELD = "WEATHER_WHOLE_FLUID_TANK_WITH_TOOLS";
    private static final String APPLY_TOOL_WEATHERING_METHOD = "applyToolWeathering";

    private CreatePatinaCompat() {
    }

    public static boolean isFluidTank(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }
        try {
            return Class.forName(FLUID_TANK_BE_CLASS).isAssignableFrom(blockEntity.getClass());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * @return true when Create: Patina's "weather whole fluid tank with tools" config is on
     */
    public static boolean isWholeTankToolWeatheringEnabled() {
        try {
            // The config values are instance fields on the static PatinaConfig.CONFIG holder,
            // so the field must be read from that instance.
            Class<?> configClass = Class.forName(PATINA_CONFIG_CLASS);
            Object config = configClass.getField(CONFIG_FIELD).get(null);
            Object configValue = configClass.getField(WHOLE_TANK_CONFIG_FIELD).get(config);
            return Boolean.TRUE.equals(configValue.getClass().getMethod("get").invoke(configValue));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Delegates one tool-driven weathering step to Create: Patina, mirroring what its
     * AxeItem/HoneycombItem/SandPaperItem mixins do for players: with wholeTank it converts
     * every part of the fluid tank multiblock, otherwise it replaces the single block while
     * preserving the block entity.
     *
     * @return true when Create: Patina handled the step
     */
    public static boolean applyToolWeathering(BlockState oldState, BlockState newState, Level level, BlockPos pos,
                                              boolean wholeTank) {
        try {
            Class<?> oxidizeUtil = Class.forName(OXIDIZE_UTIL_CLASS);
            Method method = oxidizeUtil.getMethod(APPLY_TOOL_WEATHERING_METHOD,
                    BlockState.class, BlockState.class, Level.class, BlockPos.class, boolean.class);
            method.invoke(null, oldState, newState, level, pos, wholeTank);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
