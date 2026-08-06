package first.wildfires.utils;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import first.wildfires.api.KineticData;
import first.wildfires.api.MobPoopData;
import first.wildfires.api.customEvent.StressAppliedModifyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.*;

public class WildfiresUtil {

    private static final ThreadLocal<Random> ThreadLocalRandom = ThreadLocal.withInitial(Random::new);
    private static final ThreadLocal<KineticBlockEntity> KineticTickContext = new ThreadLocal<>();

    public static Random random() {
        return ThreadLocalRandom.get();
    }

    public static void beginKineticTick(KineticBlockEntity blockEntity) {
        KineticTickContext.set(blockEntity);
    }

    @Nullable
    public static KineticBlockEntity getKineticTickContext() {
        KineticBlockEntity blockEntity = KineticTickContext.get();
        if (blockEntity == null) {
            return null;
        }

        // A kinetic subclass may do its processing after super.tick() returns.
        // Keep the context for that call, but reject unrelated recipe queries.
        ClassLoader classLoader = WildfiresUtil.class.getClassLoader();
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            try {
                Class<?> caller = Class.forName(element.getClassName(), false, classLoader);
                if ("tick".equals(element.getMethodName())
                        && KineticBlockEntity.class.isAssignableFrom(caller)) {
                    return blockEntity;
                }
            } catch (ClassNotFoundException | LinkageError ignored) {
                // Stack frames can belong to classes unavailable to this loader.
            }
        }
        return null;
    }

    public static List<ProcessingOutput> modifyProcessingOutputs(List<ProcessingOutput> outputs,
                                                                  @Nullable KineticBlockEntity blockEntity) {
        if (blockEntity == null) {
            return outputs;
        }
        return modifyProcessingOutputs(outputs, blockEntity.getLevel(), blockEntity.getBlockPos());
    }

    public static List<ProcessingOutput> modifyProcessingOutputs(List<ProcessingOutput> outputs,
                                                                  @Nullable Level level,
                                                                  BlockPos blockPos) {
        if (level == null || outputs.isEmpty()) {
            return outputs;
        }

        float luck = getLuck(level, Player.class, new AABB(blockPos).inflate(32));
        if (luck == 0) {
            return outputs;
        }

        float multiplier = 1 + luck * 0.1f;
        List<ProcessingOutput> modified = new ArrayList<>(outputs.size());
        for (ProcessingOutput output : outputs) {
            float chance = output.getChance();
            if (chance <= 0 || chance >= 1) {
                modified.add(output);
                continue;
            }

            float adjustedChance = Math.max(0, Math.min(1, chance * multiplier));
            modified.add(new ProcessingOutput(output.getStack(), adjustedChance));
        }
        return modified;
    }

    public static final List<KineticData> kineticDataList = new ArrayList<>();
    public static final List<MobPoopData> mobPoopDataList = new ArrayList<>();
    public static final List<EntityType<?>> PoopList = new ArrayList<>();
    public static final Map<String, Set<ResourceLocation>> StructureStageMap = new HashMap<>();
    public static long destroyTime = -1;

    public static float stressAppliedModify(KineticBlockEntity blockEntity, float stress) {
        StressAppliedModifyEvent event = new StressAppliedModifyEvent(blockEntity.getBlockState().getBlock(), stress);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getStressApplied();
    }

    public static boolean isEquippedCurio(Player player, Item item) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        if (handler != null) {
            return handler.isEquipped(item);
        }
        return false;
    }

    public static void post(Event event) {
        MinecraftForge.EVENT_BUS.post(event);
    }

    public static LootParams.Builder modifyLootParams(LootParams.Builder builder, LivingEntity living) {
        AttributeInstance instance = living.getAttribute(Attributes.LUCK);
        if (instance != null) {
            return builder.withLuck((float) instance.getValue());
        }
        return builder;
    }

    public static <T extends LivingEntity> float getLuck(Level level, Class<T> tClass, AABB aabb) {
        return (float) level.getEntitiesOfClass(tClass, aabb).stream()
                .mapToDouble(living -> {
                    AttributeInstance instance = living.getAttribute(Attributes.LUCK);
                    if (instance != null) {
                        return instance.getValue();
                    }
                    return 0;
                })
                .max()
                .orElse(0);
    }

    public static <T extends LivingEntity> LootParams.Builder modifyLootParams(LootParams.Builder builder, Level level, Class<T> tClass, AABB aabb) {
        return builder.withLuck(getLuck(level, tClass, aabb));
    }

    public static UUID getUUID(String name) {
        Random random = random();
        random.setSeed(name.hashCode());
        return new UUID(random.nextLong(), random.nextLong());
    }

    public static void playSound(Level level, BlockPos blockPos, SoundEvent soundEvent, SoundSource soundSource) {
        Random random = random();
        level.playSound(
                null,
                blockPos,
                soundEvent,
                soundSource,
                random.nextFloat(0.5f,1),
                random.nextFloat(0.5f,1)
        );
    }

}
