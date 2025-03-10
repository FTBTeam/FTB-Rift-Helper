package dev.ftb.mods.ftbrifthelper;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ftb.mods.ftbrifthelper.mixin.ChunkStorageAccess;
import dev.ftb.mods.ftbrifthelper.mixin.IOWorkerAccess;
import dev.ftb.mods.ftbrifthelper.mixin.RegionFileStorageAccess;
import dev.ftb.mods.ftbteambases.util.RegionCoords;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.Util;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.util.*;

public class RiftRegionManager extends SavedData {
    private static final String DATA_NAME = "RiftRegionData";
    private static ServerLevel riftDimension;

    private static final Codec<Map<RegionCoords,UUID>> REGION_MAP_CODEC
            = Codec.unboundedMap(RegionCoords.STRING_CODEC, UUIDUtil.CODEC).xmap(HashMap::new, Map::copyOf);
    private static final Codec<Set<UUID>> UUID_SET_CODEC
            = UUIDUtil.CODEC.listOf().xmap(HashSet::new, ArrayList::new);
    private static final Codec<Set<RegionCoords>> REGION_COORDS_SET_CODEC
            = RegionCoords.CODEC.listOf().xmap(HashSet::new, ArrayList::new);

    public static final Codec<RiftRegionManager> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            REGION_MAP_CODEC.fieldOf("region_to_team_id").forGetter(d -> d.region2TeamId),
            UUID_SET_CODEC.fieldOf("pending_refresh").forGetter(d -> d.pendingRefresh),
            REGION_COORDS_SET_CODEC.fieldOf("pending_delete").forGetter(d -> d.pendingDelete)
    ).apply(builder, RiftRegionManager::new));

    private final Map<RegionCoords, UUID> region2TeamId;
    private final Set<UUID> pendingRefresh;
    private final Set<RegionCoords> pendingDelete;
    private final Map<UUID,Set<RegionCoords>> team2regions;  // not persisted; dynamically recalculated as needed

    private RiftRegionManager(Map<RegionCoords, UUID> region2TeamId, Set<UUID> pendingRefresh, Set<RegionCoords> pendingDelete) {
        this.region2TeamId = region2TeamId;
        this.pendingRefresh = pendingRefresh;
        this.pendingDelete = pendingDelete;

        team2regions = new HashMap<>();
    }

    public static RiftRegionManager getInstance() {
        return getRiftDimension().getDataStorage().computeIfAbsent(RiftRegionManager.factory(), DATA_NAME);
    }

    private static RiftRegionManager load(CompoundTag tag, HolderLookup.Provider provider) {
        return CODEC.parse(provider.createSerializationContext(NbtOps.INSTANCE), tag.getCompound("manager"))
                .resultOrPartial(err -> FTBRiftHelper.LOGGER.error("failed to deserialize rift region data: {}", err))
                .orElse(RiftRegionManager.createNew());
    }

    private static RiftRegionManager createNew() {
        return new RiftRegionManager(new HashMap<>(), new HashSet<>(), new HashSet<>());
    }

    private static SavedData.Factory<RiftRegionManager> factory() {
        return new SavedData.Factory<>(RiftRegionManager::createNew, RiftRegionManager::load, null);
    }

    private static ServerLevel getRiftDimension() {
        if (riftDimension == null) {
            riftDimension = ServerLifecycleHooks.getCurrentServer().getLevel(FTBRiftHelper.RIFT_DIMENSION);
            if (riftDimension == null) {
                throw new IllegalStateException("Rift Dimension not available!");
            }
        }
        return riftDimension;
    }

    static void clearCachedRiftDimension() {
        riftDimension = null;
    }

    @Override
    public CompoundTag save(CompoundTag compoundTag, HolderLookup.Provider registries) {
        return Util.make(new CompoundTag(), tag ->
                tag.put("manager", CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this)
                        .resultOrPartial(err -> FTBRiftHelper.LOGGER.error("failed to serialize rift region data: {}", err))
                        .orElse(new CompoundTag())));
    }

    public Optional<UUID> getTeamForRegion(RegionCoords regionCoords) {
        return Optional.ofNullable(region2TeamId.get(regionCoords));
    }

    public Set<RegionCoords> getRegionsForTeam(UUID teamId) {
        return team2regions.computeIfAbsent(teamId, k -> Util.make(new HashSet<>(), set ->
                region2TeamId.forEach((rc, id) -> {
                    if (id.equals(teamId)) {
                        set.add(rc);
                    }
                }))
        );
    }

    public void addRegion(UUID teamId, RegionCoords regionCoords) {
        region2TeamId.put(regionCoords, teamId);
        team2regions.remove(teamId);
        setDirty();
    }

    public void addPendingRefresh(UUID teamId) {
        pendingRefresh.add(teamId);
        setDirty();
    }

    public Set<UUID> getPendingRefresh() {
        // return a copy here so we can safely delete elements in clearPendingRefresh()
        return Set.copyOf(pendingRefresh);
    }

    public void clearPendingRefresh(UUID teamId) {
        if (pendingRefresh.remove(teamId)) {
            setDirty();
        }
    }

    public void onTeamBaseArchived(UUID teamId) {
        Set<RegionCoords> toDelete = new HashSet<>();
        region2TeamId.forEach((rc, id) -> {
            if (id.equals(teamId)) {
                toDelete.add(rc);
            }
        });
        if (!toDelete.isEmpty()) {
            pendingDelete.addAll(toDelete);
            toDelete.forEach(region2TeamId::remove);
            setDirty();
        }
        team2regions.remove(teamId);
    }

    public Set<RegionCoords> getPendingDeletion() {
        return Collections.unmodifiableSet(pendingDelete);
    }

    public void clearPendingDeletion(Set<RegionCoords> rc) {
        if (!rc.isEmpty()) {
            pendingDelete.removeAll(rc);
            setDirty();
        }
    }

    public boolean tryCloseRegionFiles(ServerLevel level, UUID teamId) {
        return tryCloseRegionFiles(level, getRegionsForTeam(teamId));
    }

    public boolean tryCloseRegionFiles(ServerLevel level, Collection<RegionCoords> regions) {
        IOWorker worker = ((ChunkStorageAccess) level.getChunkSource().chunkMap).getWorker();
        RegionFileStorage storage = ((IOWorkerAccess) worker).getStorage();
        if (storage == null) {
            // should never happen! but see https://github.com/FTBTeam/FTB-Modpack-Issues/issues/7259
            // we'll assume in this case the region is indeed closed and return true
            FTBRiftHelper.LOGGER.warn("null RegionFileStorage in IOWorker? hopefully closed already, skipping");
            return true;
        }
        Long2ObjectLinkedOpenHashMap<RegionFile> cache = ((RegionFileStorageAccess) (Object) storage).getRegionCache();

        int closed = 0;
        for (RegionCoords rc : regions) {
            long key = ChunkPos.asLong(rc.x(), rc.z());
            RegionFile regionFile = cache.get(key);
            if (regionFile != null) {
                try {
                    cache.remove(key).close();
                    FTBRiftHelper.LOGGER.debug("closed and purged region {}/{} from the cache", level.dimension().location(), rc);
                    closed++;
                } catch (IOException e) {
                    FTBRiftHelper.LOGGER.error("can't close region file {} for region {}", regionFile.getPath(), rc);
                }
            } else {
                FTBRiftHelper.LOGGER.debug("region file for region {} not in cache, already closed? continuing!", rc);
                closed++;
            }
        }

        return closed == regions.size();
    }
}
