package first.wildfires.mixin.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import first.wildfires.api.customEvent.KineticBlockEntityTickEvent;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KineticBlockEntity.class, remap = false)
public abstract class KineticBlockEntityMixin {

	@Inject(
			method = "tick",
			at = @At(
					value = "HEAD"
			),
			cancellable = true
	)
	private void tickHead(CallbackInfo ci) {
		KineticBlockEntity blockEntity = (KineticBlockEntity) (Object) this;
		KineticBlockEntityTickEvent.Pre event = new KineticBlockEntityTickEvent.Pre(blockEntity);
		MinecraftForge.EVENT_BUS.post(event);
		if (event.isCanceled()) {
			ci.cancel();
		}
	}

	@Inject(
			method = "tick",
			at = @At(
					value = "TAIL"
			),
			remap = false
	)
	private void tickTail(CallbackInfo ci) {
		KineticBlockEntity blockEntity = (KineticBlockEntity) (Object) this;
		KineticBlockEntityTickEvent.Post event = new KineticBlockEntityTickEvent.Post(blockEntity);
		MinecraftForge.EVENT_BUS.post(event);
/*


		Level level = blockEntity.getLevel();
		KineticNetwork createNetwork = blockEntity.getOrCreateNetwork();
		BlockState blockState = blockEntity.getBlockState();
		BlockPos blockPos = blockEntity.getBlockPos();
		List<Long> destroyed = Config.destroyed;
		if (level != null && createNetwork != null && !destroyed.contains(level.getGameTime())) {
			float consumedStress = 0;
			Map<KineticBlockEntity, Float> members = createNetwork.members;
			List<KineticBlockEntity> list = members.keySet().stream().toList();
			for (KineticBlockEntity kineticBlockEntity : list) {
				consumedStress += Math.abs(kineticBlockEntity.getSpeed() * (members.get(kineticBlockEntity)));
			}
			if (consumedStress > Config.getMaxPower(blockState) || Math.abs(blockEntity.getSpeed()) > Config.getMaxSpeed(blockState)) {
				destroyed.add(level.getGameTime());
				Map<String, List<Double>> map = Config.getDestroy(blockState);
				level.destroyBlock(blockPos, map == null);
				if (map != null) {
					level.destroyBlock(blockPos, false);
					map.forEach((string, sizeList) -> {
						Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(string));
						int size = 0;
						if (sizeList.size() > 1) {
							int min = (int) sizeList.get(0).floatValue();
							int max = (int) sizeList.get(1).floatValue();
							if (max > 0 && max > min) {
								size = Config.random.nextInt(min, max);
							}
						} else if (!sizeList.isEmpty()) {
							size = (int) sizeList.get(0).floatValue();
						}
						if (item != null && size > 0) {
							ItemStack itemStack = new ItemStack(item);
							itemStack.setCount(size);
							ItemEntity itemEntity = new ItemEntity(
									level,
									blockPos.getX() + 0.5,
									blockPos.getY() + 0.5,
									blockPos.getZ() + 0.5,
									itemStack);
							level.addFreshEntity(itemEntity);
						}
					});
				}
			}
		}
		*/
	}

}
