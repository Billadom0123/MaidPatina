package com.billadom.maidpatina.task;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;

public final class MaidPatinaCoordinationTask extends MaidCheckRateTask {
    private final BlockOperation operation;

    public MaidPatinaCoordinationTask(BlockOperation operation) {
        // verified: maid_code EntityMaid work goals use MaidCheckRateTask behaviors, 2026-08-03
        super(ImmutableMap.of());
        this.operation = operation;
        setMaxCheckRate(1);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        PatinaCoordinationManager.tick(level, maid, operation, gameTime);
    }
}
