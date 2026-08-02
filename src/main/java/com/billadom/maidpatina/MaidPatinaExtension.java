package com.billadom.maidpatina;

import com.billadom.maidpatina.task.RustRemovalTask;
import com.billadom.maidpatina.task.WaxingTask;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;

@LittleMaidExtension
public final class MaidPatinaExtension implements ILittleMaid {
    @Override
    public void addMaidTask(TaskManager manager) {
        manager.add(new RustRemovalTask());
        manager.add(new WaxingTask());
    }
}
