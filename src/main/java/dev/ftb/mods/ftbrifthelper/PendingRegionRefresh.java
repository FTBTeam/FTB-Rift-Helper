package dev.ftb.mods.ftbrifthelper;

import com.mojang.serialization.Codec;
import dev.ftb.mods.ftbteambases.util.RegionCoords;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PendingRegionRefresh extends SavedData {
    private static final Codec<List<RegionCoords>> CODEC = RegionCoords.CODEC.listOf();
    private static final String DATA_NAME = "PendingRegionRefresh";
    private static ServerLevel riftDimension;

    private final Set<RegionCoords> pendingRefresh = new HashSet<>();

    public static SavedData.Factory<PendingRegionRefresh> factory() {
        return new SavedData.Factory<>(PendingRegionRefresh::new, PendingRegionRefresh::load, null);
    }

    public static PendingRegionRefresh getInstance() {
        return getRiftDimension().getDataStorage().computeIfAbsent(PendingRegionRefresh.factory(), DATA_NAME);
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
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CODEC.encode(List.copyOf(pendingRefresh), NbtOps.INSTANCE, tag);
        return tag;
    }

    private static PendingRegionRefresh load(CompoundTag tag, HolderLookup.Provider provider) {
        return new PendingRegionRefresh().readNBT(tag);
    }

    private PendingRegionRefresh readNBT(CompoundTag tag) {
        CODEC.parse(NbtOps.INSTANCE, tag).ifSuccess(coords -> {
            pendingRefresh.clear();
            pendingRefresh.addAll(coords);
        });
        return this;
    }

    public void markRegionForRefresh(RegionCoords coords) {
        pendingRefresh.add(coords);
        setDirty();
    }

    public void clear() {
        pendingRefresh.clear();
        setDirty();
    }

    public Set<RegionCoords> getPending() {
        return Collections.unmodifiableSet(pendingRefresh);
    }
}
