package dev.ftb.mods.ftbrifthelper;

import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftbrifthelper.mixin.ChunkMapAccess;
import dev.ftb.mods.ftbteambases.data.bases.BaseInstanceManager;
import dev.ftb.mods.ftbteambases.util.RegionCoords;
import dev.ftb.mods.ftbteambases.util.RegionFileRelocator;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.util.*;

public class RiftHelperUtil {
    static void copyAndRelocateRegions(UUID teamId, RegionCoords rc) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            CommandSourceStack source = server.createCommandSourceStack();
            try {
                RegionFileRelocator relocator = new RegionFileRelocator(source, Config.RIFT_TEMPLATE.get(),
                        FTBRiftHelper.RIFT_DIMENSION, XZ.of(rc.x(), rc.z()), true);
                relocator.start(success -> {
                    if (success) {
                        RiftRegionManager mgr = RiftRegionManager.getInstance();
                        mgr.clearPendingRefresh(teamId);
                        relocator.getRelocationData().values()
                                .forEach(data -> mgr.addRegion(teamId, data.orig().offsetBy(data.regionOffset())));
                        FTBRiftHelper.LOGGER.info("relocated rift template for team {} to {} in rift dimension", teamId, rc);
                    } else {
                        FTBRiftHelper.LOGGER.error("failed to relocate rift template for team {} to {} in rift dimension", teamId, rc);
                    }
                });
            } catch (IOException e) {
                FTBRiftHelper.LOGGER.error("Error relocating rift template for team {} to {}: {}", teamId, rc, e.getMessage());
            }
        }
    }

    static void sendPlayerToRift(ServerPlayer player, int playerRadius) {
        ServerLevel riftDimension = player.getServer().getLevel(FTBRiftHelper.RIFT_DIMENSION);

        if (riftDimension != null) {
            List<Player> allPlayers = playerRadius > 0 ?
                    player.level().getNearbyPlayers(TargetingConditions.forNonCombat(), player, player.getBoundingBox().inflate(playerRadius)) :
                    List.of();

            getZoneInPoint(player).ifPresent(pos -> {
                Vec3 vec = Vec3.atBottomCenterOf(pos);
                allPlayers.forEach(p -> p.teleportTo(riftDimension, vec.x, vec.y, vec.z, Set.of(), 0f, 0f));
                player.teleportTo(riftDimension, vec.x, vec.y, vec.z, Set.of(), Config.SPAWN_FACING.get().floatValue(), 0f);
            });
        }
    }

    static Optional<BlockPos> getZoneInPoint(ServerPlayer player) {
        return BaseInstanceManager.get().getBaseForPlayer(player).map(base ->
                baseToRiftCoords(base.extents().start())
                        .getBlockPos(Config.getSpawnOffset()));
    }

    /**
     * A bit of a no-op here, but makes clear that there's a 1:1 mapping of base coordinates in the base dim to
     * rift island coordinates. Also allows for flexibility should this ever need to be changed.
     *
     * @param regionCoords the region coordinates in the base dimension (specifically the extents.start() region)
     * @return corresponding region coordinates in the rift dimension
     */
    static RegionCoords baseToRiftCoords(RegionCoords regionCoords) {
        return regionCoords;
    }

    /**
     * See above.
     *
     * @param regionCoords region coordinates in the rift dimension
     * @return corresponding region coordinates in the base dimension
     */
    static RegionCoords riftToBaseCoords(RegionCoords regionCoords) {
        return regionCoords;
    }

    public static Set<RegionCoords> getLoadedRegions(ServerLevel level) {
        return Util.make(new HashSet<>(), set -> ((ChunkMapAccess) level.getChunkSource().chunkMap).invokeGetChunks()
                .forEach(holder -> set.add(new RegionCoords(holder.getPos().x >> 5, holder.getPos().z >> 5)))
        );
    }
}
