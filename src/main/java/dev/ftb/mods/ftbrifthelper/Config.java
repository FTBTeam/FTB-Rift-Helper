package dev.ftb.mods.ftbrifthelper;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> SPAWN_OFFSET = BUILDER
            .comment("XYZ Offset from the team-specific region's (0,0,0) point where players enter the rift dimension")
            .defineList(List.of("spawn_offset"), () -> List.of(0, 83, 272), null, val -> true, ModConfigSpec.Range.of(3, 3));
    public static final ModConfigSpec.DoubleValue SPAWN_FACING = BUILDER
            .comment("Player facing when entering the dimension (0 = south, 90 = west, 180 = north, 270 = east)")
            .defineInRange("spawn_yaw", 180.0, 0.0, 360.0);
    public static final ModConfigSpec.ConfigValue<String> RIFT_TEMPLATE = BUILDER
            .comment("Name of the MCA template which is relocated (from <instance>/ftbteambases/<template>/*.mca) into the rift dimension for each team")
            .define("rift_template", "the_rift");
    public static final ModConfigSpec.BooleanValue REMOVE_RIFT_MCA_ON_BASE_ARCHIVAL = BUILDER
            .comment("When a team base is archived, should the corresponding rift island MCA's be cleaned up too? Not essential, but helps save on disk space")
            .define("remove_rift_mca_on_base_archival", true);
    public static final ModConfigSpec.ConfigValue<String> TEMPLE_STRUCTURE_NBT = BUILDER
            .comment("Location of the structure NBT for the overworld temple structure which is pasted far below the team base")
            .define("temple_structure_nbt", "ftb:overworld/underwater/portal_temple");
    public static final ModConfigSpec.IntValue TEMPLATE_STRUCTURE_Y = BUILDER
            .comment("Y position to paste the overworld temple structure at")
            .defineInRange("temple_structure_y", -55, Integer.MIN_VALUE, Integer.MAX_VALUE);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static BlockPos getSpawnOffset() {
        List<? extends Integer> l = Config.SPAWN_OFFSET.get();
        return new BlockPos(l.get(0), l.get(1), l.get(2));
    }

    @Nullable
    public static ResourceLocation getTempleStructure() {
        return ResourceLocation.tryParse(TEMPLE_STRUCTURE_NBT.get());
    }
}
