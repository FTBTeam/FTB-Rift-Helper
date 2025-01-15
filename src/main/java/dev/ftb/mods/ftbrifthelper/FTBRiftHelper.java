package dev.ftb.mods.ftbrifthelper;

import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbteambases.data.bases.BaseInstanceManager;
import dev.ftb.mods.ftbteambases.events.BaseCreatedEvent;
import dev.ftb.mods.ftbteambases.util.RegionCoords;
import dev.ftb.mods.ftbteambases.util.RegionExtents;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
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

import java.util.Set;

@Mod(FTBRiftHelper.MODID)
public class FTBRiftHelper {
    public static final String MODID = "ftbrifthelper";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final ResourceKey<Level> RIFT_DIMENSION = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("ftb:the_rift"));
    private static final ResourceLocation RIFT_WEAVER = ResourceLocation.parse("ftboceanmobs:rift_weaver");
    private static final Vec3i ZERO_64_ZERO = new Vec3i(0, 64, 0);

    public FTBRiftHelper(IEventBus modEventBus, ModContainer modContainer) {
//        modEventBus.addListener(this::commonSetup);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        NeoForge.EVENT_BUS.addListener(this::registerCommands);
//        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onEntityDeath);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);

        // arch events
        BaseCreatedEvent.CREATED.register(this::onTeamBaseCreation);
    }

    private void onTeamBaseCreation(BaseInstanceManager baseInstanceManager, ServerPlayer player, Team team) {
        baseInstanceManager.getBaseForTeam(team).ifPresent(base -> {
            RegionCoords riftCoords = RiftHelperUtil.baseToRiftCoords(base.extents().start());
            RiftHelperUtil.copyAndRelocateRegion(team.getShortName(), riftCoords);
        });
    }

    private void onServerTick(ServerTickEvent.Post event) {
        // once a minute, check if any regions need refreshing
        if (event.getServer().getTickCount() % 1200 == 0) {
            Set<RegionCoords> pending = PendingRegionRefresh.getInstance().getPending();
            if (!pending.isEmpty()) {
                ServerLevel level = event.getServer().getLevel(RIFT_DIMENSION);
                if (level != null) {
                    pending.forEach(rc -> {
                        if (safeToRefresh(level, rc)) {
                            RiftHelperUtil.copyAndRelocateRegion("<refresh>", rc);
                        }
                    });
                    pending.clear();
                }
            }
        }
    }

    private boolean safeToRefresh(ServerLevel riftLevel, RegionCoords rc) {
        // FIXME: a little kludgy here and dependent on a 2x2 region template
        // Check that no player is closer than 1024 blocks to the (0,64,0) point of the base region
        // This is the centre, since we're copying 4 regions ([-1,-1], [-1, 0], [0,-1], [0,0])
        return riftLevel.players().stream()
                .noneMatch(p -> p.distanceToSqr(Vec3.atCenterOf(rc.getBlockPos(ZERO_64_ZERO))) < 1024 * 1024);
    }

    private void onEntityDeath(LivingDeathEvent event) {
        if (event.getEntity().level().dimension().location().equals(RIFT_DIMENSION)) {
            ResourceLocation rl = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
            if (rl.equals(RIFT_WEAVER)) {
                BlockPos pos = event.getEntity().blockPosition();
                RegionCoords riftCoords = new RegionCoords(pos.getX() >> 9, pos.getZ() >> 9);
                if (BaseInstanceManager.get().allLiveBases().values().stream()
                        .anyMatch(base -> coordsInExtents(RiftHelperUtil.riftToBaseCoords(riftCoords), base.extents()))) {
                    PendingRegionRefresh.getInstance().markRegionForRefresh(riftCoords);
                    LOGGER.info("Rift Weaver boss killed at {} - mark region {} for pending refresh", pos, riftCoords);
                } else {
                    LOGGER.warn("Rift Weaver boss was killed outside any known rift island coords? Loc = {}", pos);
                }
            }
        }
    }

    private boolean coordsInExtents(RegionCoords rc, RegionExtents extents) {
        return rc.x() >= extents.start().x() && rc.x() <= extents.end().x()
                && rc.z() >= extents.start().z() && rc.z() <= extents.end().z();
    }

    private void registerCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher(), event.getBuildContext());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        PendingRegionRefresh.clearCachedRiftDimension();
    }
}
