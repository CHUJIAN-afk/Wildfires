package first.wildfires.mixin.minecraft;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Pair;
import dev.latvian.mods.kubejs.core.PlayerKJS;
import dev.latvian.mods.kubejs.stages.Stages;
import first.wildfires.utils.WildfiresUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;
import java.util.stream.Collectors;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {

    @Inject(
            method = "findNearestMapStructure",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;isEmpty()Z"
            ),
            cancellable = true
    )
    private void findNearestMapStructure(
            ServerLevel level,
            HolderSet<Structure> structureHolderSet,
            BlockPos blockPos,
            int pSearchRadius,
            boolean pSkipKnownStructures,
            CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> cir,
            @Local Map<StructurePlacement, Set<Holder<Structure>>> map
    ) {
        if (!map.isEmpty()) {
            MinecraftServer server = level.getServer();
            Registry<Structure> registry = level.registryAccess()
                    .registryOrThrow(Registries.STRUCTURE);
            Set<ResourceLocation> allowedSet = server.getPlayerList()
                    .getPlayers()
                    .stream()
                    .filter(player -> player instanceof PlayerKJS)
                    .map(player -> (PlayerKJS) player)
                    .map(PlayerKJS::kjs$getStages)
                    .map(Stages::getAll)
                    .flatMap(Collection::stream)
                    .map(WildfiresUtil.StructureStageMap::get)
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());
            Set<Structure> removeSet = WildfiresUtil.StructureStageMap.values()
                    .stream()
                    .flatMap(Collection::stream)
                    .filter(location -> !allowedSet.contains(location))
                    .map(registry::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            map.values()
                    .removeIf(set -> {
                        set.removeIf(holder -> removeSet.contains(holder.get()));
                        return set.isEmpty();
                    });
            if (map.isEmpty()) {
                cir.setReturnValue(null);
            }
        }
    }
}
