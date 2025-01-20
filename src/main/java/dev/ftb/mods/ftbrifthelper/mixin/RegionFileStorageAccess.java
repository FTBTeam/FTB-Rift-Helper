package dev.ftb.mods.ftbrifthelper.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RegionFileStorage.class)
public interface RegionFileStorageAccess {
    @Accessor
    Long2ObjectLinkedOpenHashMap<RegionFile> getRegionCache();
}
