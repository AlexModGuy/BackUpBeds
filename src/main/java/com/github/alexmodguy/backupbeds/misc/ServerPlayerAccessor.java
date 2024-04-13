package com.github.alexmodguy.backupbeds.misc;

import net.minecraft.core.GlobalPos;

import java.util.Stack;

public interface ServerPlayerAccessor {

    Stack<GlobalPos> getBackupBeds();
}
