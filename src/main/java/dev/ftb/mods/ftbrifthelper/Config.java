package dev.ftb.mods.ftbrifthelper;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> SPAWN_OFFSET = BUILDER
            .comment("XYZ Offset from the team-specific region's (0,0,0) point where players enter the dimension")
            .defineList(List.of("spawn_offset"), () -> List.of(0, 83, 272), null, val -> true, ModConfigSpec.Range.of(3, 3));
    public static final ModConfigSpec.DoubleValue SPAWN_FACING = BUILDER
            .comment("Player facing when entering the dimension (0 = south, 90 = west, 180 = north, 270 = east)")
            .defineInRange("spawn_yaw", 180.0, 0.0, 360.0);
    public static final ModConfigSpec.ConfigValue<String> RIFT_TEMPLATE = BUILDER
            .comment("Name of the MCA template which is relocated (from <instance>/ftbteambases/<template>/*.mca) into the rift dimension for each team")
            .define("rift_template", "the_rift");

    static final ModConfigSpec SPEC = BUILDER.build();

    public static BlockPos getSpawnOffset() {
        List<? extends Integer> l = Config.SPAWN_OFFSET.get();
        return new BlockPos(l.get(0), l.get(1), l.get(2));
    }
}
