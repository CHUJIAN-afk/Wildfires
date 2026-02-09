package first.wildfires.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;

import java.util.Random;
import java.util.UUID;

public class WildfiresUtil {

    private static final ThreadLocal<Random> ThreadLocalRandom = ThreadLocal.withInitial(Random::new);

    public static Random random() {
        return ThreadLocalRandom.get();
    }

    public static void post(Event event) {
        MinecraftForge.EVENT_BUS.post(event);
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
