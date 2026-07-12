package first.wildfires.mixin.minecraft;


import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.latvian.mods.kubejs.core.PlayerKJS;
import dev.latvian.mods.kubejs.stages.Stages;
import first.wildfires.utils.WildfiresUtil;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.StructureAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Collectors;

@Mixin(StructureManager.class)
public class StructureManagerMixin {

    @Shadow
    @Final
    private LevelAccessor level;

    @WrapMethod(method = "getStartForStructure")
    private StructureStart startsForStructure(SectionPos sectionPos, Structure structure, StructureAccess access, Operation<StructureStart> original) {
        StructureStart call = original.call(sectionPos, structure, access);
        if (call != null) {
            MinecraftServer server = level.getServer();
            ResourceLocation location = level.registryAccess()
                    .registryOrThrow(Registries.STRUCTURE)
                    .getKey(structure);
            if (location != null && server != null && WildfiresUtil.StructureStageMap.values()
                    .stream()
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet())
                    .contains(location)) {
                boolean anyMatch = server.getPlayerList()
                        .getPlayers()
                        .stream()
                        .filter(player -> player instanceof PlayerKJS)
                        .map(player -> (PlayerKJS) player)
                        .map(PlayerKJS::kjs$getStages)
                        .map(Stages::getAll)
                        .flatMap(Collection::stream)
                        .map(id -> WildfiresUtil.StructureStageMap.getOrDefault(id, new HashSet<>()))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toSet())
                        .contains(location);
                if (!anyMatch) {
                    return null;
                }
            }
        }
        return call;
    }
}
