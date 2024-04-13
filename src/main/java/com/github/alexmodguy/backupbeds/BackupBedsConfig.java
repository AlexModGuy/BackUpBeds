package com.github.alexmodguy.backupbeds;

import net.minecraftforge.common.ForgeConfigSpec;

public class BackupBedsConfig {

    public final ForgeConfigSpec.IntValue backupBedTrackCount;

    public BackupBedsConfig(final ForgeConfigSpec.Builder builder) {
        backupBedTrackCount = builder.comment("The maximum amount of backup beds tracked by the server for each player.").translation("backup_bed_track_count").defineInRange("backup_bed_track_count", 5, 0, 100);
    }
}