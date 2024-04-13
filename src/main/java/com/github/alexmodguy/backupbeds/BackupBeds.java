package com.github.alexmodguy.backupbeds;

import com.ibm.icu.text.RuleBasedNumberFormat;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Locale;

@Mod("backupbeds")
public class BackupBeds {
    public static final Logger LOGGER = LogManager.getLogger("backupbeds");
    public static final ForgeConfigSpec CONFIG_SPEC;
    public static final BackupBedsConfig CONFIG;

    static {
        {
            final Pair<BackupBedsConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(BackupBedsConfig::new);
            CONFIG = specPair.getLeft();
            CONFIG_SPEC = specPair.getRight();
        }
    }

    public BackupBeds() {
        final ModLoadingContext modLoadingContext = ModLoadingContext.get();
        modLoadingContext.registerConfig(ModConfig.Type.COMMON, CONFIG_SPEC);
    }

    public static String getNumberSuffix(int number) {
        number = number % 10;
        String str = "";
        switch (number) {
            case 1:
                str = "st";
                break;
            case 2:
                str = "nd";
                break;
            case 3:
                str = "rd";
                break;
            default:
                str = "th";
        }
        return str;
    }
}
