package first.wildfires.mixin.minecraft;


import com.google.common.collect.ImmutableList;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.core.PlayerKJS;
import dev.latvian.mods.kubejs.stages.Stages;
import first.wildfires.utils.WildfiresUtil;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Mixin(StructureManager.class)
public abstract class StructureManagerMixin {

    @Shadow @Final private LevelAccessor level;

    @Inject(
            method = "startsForStructure(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/levelgen/structure/Structure;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void startsForStructure(SectionPos sectionPos, Structure structure, CallbackInfoReturnable<List<StructureStart>> cir) {
        List<StructureStart> structureStartList = cir.getReturnValue();
        MinecraftServer server = level.getServer();
        ResourceLocation location = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structure);
        if (location != null && !structureStartList.isEmpty() && server != null && WildfiresUtil.StructureStageMap.values().stream().anyMatch(location::equals)) {
            boolean noneMatch = server.getPlayerList().getPlayers().stream()
                    .map(player -> (PlayerKJS) player)
                    .map(PlayerKJS::kjs$getStages)
                    .map(Stages::getAll)
                    .flatMap(Collection::stream)
                    .map(id -> WildfiresUtil.StructureStageMap.getOrDefault(id, new ArrayList<>()))
                    .flatMap(Collection::stream)
                    .noneMatch(resourceLocation -> resourceLocation == location);
            if (noneMatch) {
                cir.setReturnValue(new ArrayList<>());
            }
        }
    }

}
