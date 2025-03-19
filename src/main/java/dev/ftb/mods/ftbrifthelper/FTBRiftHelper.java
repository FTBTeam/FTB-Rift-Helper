package dev.ftb.mods.ftbrifthelper;

import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbteambases.data.bases.BaseInstanceManager;
import dev.ftb.mods.ftbteambases.events.BaseArchivedEvent;
import dev.ftb.mods.ftbteambases.events.BaseCreatedEvent;
import dev.ftb.mods.ftbteambases.util.RegionCoords;
import dev.ftb.mods.ftbteambases.util.RegionFileUtil;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Mod(FTBRiftHelper.MODID)
public class FTBRiftHelper {
    public static final String MODID = "ftbrifthelper";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final ResourceKey<Level> RIFT_DIMENSION = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("ftb:the_rift"));
    private static final ResourceLocation RIFT_WEAVER = ResourceLocation.parse("ftboceanmobs:rift_weaver");
    private static final Vec3i ZERO_64_ZERO = new Vec3i(0, 64, 0);

    public FTBRiftHelper(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onEntityDeath);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onPlayerDeath);

        // arch events
        BaseCreatedEvent.CREATED.register(this::onTeamBaseCreation);
        BaseArchivedEvent.ARCHIVED.register(this::onTeamBaseArchived);
    }

    private void onTeamBaseCreation(BaseInstanceManager baseInstanceManager, ServerPlayer player, Team team) {
        baseInstanceManager.getBaseForTeam(team).ifPresent(base -> {
            RegionCoords riftCoords = RiftHelperUtil.baseToRiftCoords(base.extents().start());
            RiftHelperUtil.copyAndRelocateRegions(team.getTeamId(), riftCoords);
            pasteTempleStructure(player);
        });
    }

    private void onTeamBaseArchived(BaseInstanceManager baseInstanceManager, Team team) {
        RiftRegionManager.getInstance().onTeamBaseArchived(team.getTeamId());
    }

    private void onServerTick(ServerTickEvent.Post event) {
        // once a minute by default, check if any regions need refreshing
        if (event.getServer().getTickCount() % Config.REFRESH_CHECK_INTERVAL.get() == 0) {
            ServerLevel level = event.getServer().getLevel(RIFT_DIMENSION);
            if (level != null) {
                Set<UUID> pendingRefresh = RiftRegionManager.getInstance().getPendingRefresh();
                Set<RegionCoords> pendingDelete = RiftRegionManager.getInstance().getPendingDeletion();
                if (!pendingDelete.isEmpty() || !pendingRefresh.isEmpty()) {
                    Set<RegionCoords> loadedRegions = RiftHelperUtil.getLoadedRegions(level);
                    checkForRegionRefresh(level, loadedRegions, pendingRefresh);
                    if (Config.REMOVE_RIFT_MCA_ON_BASE_ARCHIVAL.get()) {
                        checkForRegionDeletion(level, loadedRegions, pendingDelete);
                    }
                }
            }
        }
    }

    private void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level().dimension().equals(RIFT_DIMENSION)) {
            // players don't die in the rift dimension, instead they're kicked back to base spawn with 1 health and full inventory
            FTBTeamsAPI.api().getManager().getTeamForPlayer(player).ifPresent(team -> {
                BaseInstanceManager mgr = BaseInstanceManager.get(player.getServer());
                player.setHealth(1f);
                event.setCanceled(true);
                if (!mgr.teleportToBaseSpawn(player, team.getId())) {
                    mgr.teleportToLobby(player);
                }
                player.server.executeIfPossible(() ->
                        player.displayClientMessage(Component.translatable("ftbrifthelper.bootedFromRift").withStyle(ChatFormatting.GOLD), false)
                );
            });
        }
    }

    private void checkForRegionRefresh(ServerLevel level, Set<RegionCoords> loadedRegions, Set<UUID> pendingTeams) {
        pendingTeams.forEach(teamId ->
                BaseInstanceManager.get().getBaseForTeamId(teamId).ifPresent(base -> {
                    RegionCoords riftCoords = RiftHelperUtil.baseToRiftCoords(base.extents().start());
                    if (loadedRegions.contains(riftCoords)) {
                        FTBRiftHelper.LOGGER.info("Skipping rift refresh for team {} - one or more chunks still loaded", teamId);
                    } else if (RiftRegionManager.getInstance().tryCloseRegionFiles(level, teamId)) {
                        RiftHelperUtil.copyAndRelocateRegions(teamId, riftCoords);
                    }
                })
        );
    }

    private static final List<String> SUBDIRS = List.of("region", "entities", "poi");

    private void checkForRegionDeletion(ServerLevel level, Set<RegionCoords> loadedRegions, Set<RegionCoords> pendingDelete) {
        if (!pendingDelete.isEmpty()) {
            Set<RegionCoords> toClear = new HashSet<>();

            pendingDelete.forEach(rc -> {
                if (!loadedRegions.contains(rc)) {
                    if (RiftRegionManager.getInstance().tryCloseRegionFiles(level, List.of(rc))) {
                        for (String subDir : SUBDIRS) {
                            Path path = RegionFileUtil.getPathForDimension(level.getServer(), RIFT_DIMENSION, subDir)
                                    .resolve(String.format("r.%d.%d.mca", rc.x(), rc.z()));
                            try {
                                Files.deleteIfExists(path);
                                toClear.add(rc);
                                LOGGER.debug("Purged region file {}", path);
                            } catch (IOException e) {
                                LOGGER.error("can't delete {}: {}", path, e.getMessage());
                            }
                        }
                    }
                }
            });
            RiftRegionManager.getInstance().clearPendingDeletion(toClear);
        }
    }

    private void onEntityDeath(LivingDeathEvent event) {
        if (event.getEntity().level() instanceof ServerLevel sl && sl.dimension().location().equals(RIFT_DIMENSION.location())) {
            ResourceLocation rl = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
            if (rl.equals(RIFT_WEAVER)) {
                BlockPos pos = event.getEntity().blockPosition();
                RegionCoords regionCoords = new RegionCoords(pos.getX() >> 9, pos.getZ() >> 9);
                RiftRegionManager mgr = RiftRegionManager.getInstance();
                mgr.getTeamForRegion(regionCoords).ifPresent(mgr::addPendingRefresh);
            }
        }
    }

    private void registerCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher(), event.getBuildContext());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        RiftRegionManager.clearCachedRiftDimension();
    }

    private void pasteTempleStructure(ServerPlayer player) {
        ResourceLocation templeLoc = Config.getTempleStructure();
        if (templeLoc != null && player.level() instanceof ServerLevel serverLevel) {
            player.getServer().getStructureManager().get(templeLoc).ifPresent(template -> {
                BlockPos pos = new BlockPos(player.getBlockX(), Config.TEMPLATE_STRUCTURE_Y.get(), player.getBlockZ());
                StructurePlaceSettings settings = new StructurePlaceSettings();
                template.placeInWorld(serverLevel, pos, pos, settings, RandomSource.create(Util.getMillis()), Block.UPDATE_CLIENTS);
            });
        }
    }
}
