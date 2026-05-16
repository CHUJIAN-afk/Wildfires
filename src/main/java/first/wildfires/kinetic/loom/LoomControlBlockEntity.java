package first.wildfires.kinetic.loom;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import first.wildfires.kinetic.loom.recipe.WeavingRecipe;
import first.wildfires.kinetic.loom.recipe.WeavingRecipeType;
import first.wildfires.kinetic.loom.recipe.WeavingType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class LoomControlBlockEntity extends KineticBlockEntity implements GeoBlockEntity, IHaveGoggleInformation {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final LoomItemStackHandler itemStackHandler = new LoomItemStackHandler(4);
    private final LazyOptional<IItemHandler> itemHandlerLazy = LazyOptional.of(() -> itemStackHandler);
    private float lastProgress = 0;
    private float progress = 0;

    @Nullable
    private WeavingRecipe currentRecipe;
    private int color = 0xFFFFFF;
    private WeavingType weavingType = WeavingType.KNITTED_CLOTH;

    public LoomControlBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && !level.isClientSide()) {
            float speed = getSpeed();

            if (currentRecipe == null) {
                // 没有正在执行的配方，尝试查找配方
                WeavingRecipe matchedRecipe = findMatchingRecipe();
                if (matchedRecipe != null) {
                    currentRecipe = matchedRecipe;
                    this.color = matchedRecipe.getColor();
                    this.weavingType = matchedRecipe.getWeavingType();
                    setChanged();
                    sendData();
                }
            } else {
                // 检查当前物品是否仍然满足配方要求
                if (!matchesCurrentRecipe()) {
                    // 物品变化，清空进度和配方
                    currentRecipe = null;
                    progress = 0;
                    this.color = 0xFFFFFF;
                    this.weavingType = WeavingType.KNITTED_CLOTH;
                } else {
                    // 执行配方
                    progress += speed / 60;

                    // 检查是否完成
                    if (progress > 160) {
                        progress -= 160;
                        // 产出物品
                        outputResults();
                    }
                }
                setChanged();
                sendData();
            }
        }
    }

    @Nullable
    private WeavingRecipe findMatchingRecipe() {
        if (level == null) return null;

        List<WeavingRecipe> recipes = level.getRecipeManager().getAllRecipesFor(WeavingRecipeType.INSTANCE);
        for (WeavingRecipe recipe : recipes) {
            if (matchesRecipe(recipe)) {
                return recipe;
            }
        }
        return null;
    }

    private boolean matchesRecipe(WeavingRecipe recipe) {
        List<net.minecraft.world.item.crafting.Ingredient> ingredients = recipe.getIngredients();
        boolean[] used = new boolean[itemStackHandler.getSlots()];

        for (net.minecraft.world.item.crafting.Ingredient ingredient : ingredients) {
            boolean found = false;
            for (int i = 0; i < itemStackHandler.getSlots(); i++) {
                if (!used[i]) {
                    ItemStack stack = itemStackHandler.getStackInSlot(i);
                    if (!stack.isEmpty() && ingredient.test(stack)) {
                        used[i] = true;
                        found = true;
                        break;
                    }
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private boolean matchesCurrentRecipe() {
        if (currentRecipe == null) return false;
        return matchesRecipe(currentRecipe);
    }

    private void outputResults() {
        if (currentRecipe == null || level == null) return;

        // 消耗输入物品
        List<net.minecraft.world.item.crafting.Ingredient> ingredients = currentRecipe.getIngredients();
        boolean[] used = new boolean[itemStackHandler.getSlots()];

        for (net.minecraft.world.item.crafting.Ingredient ingredient : ingredients) {
            for (int i = 0; i < itemStackHandler.getSlots(); i++) {
                if (!used[i]) {
                    ItemStack stack = itemStackHandler.getStackInSlot(i);
                    if (!stack.isEmpty() && ingredient.test(stack)) {
                        stack.shrink(1);
                        used[i] = true;
                        break;
                    }
                }
            }
        }

        // 在方块上方生成产物（不掉入储存空间）
        BlockPos outputPos = getBlockPos().above();
        for (ItemStack output : currentRecipe.getOutputs()) {
            ItemStack copy = output.copy();
            ItemEntity itemEntity = new ItemEntity(level, outputPos.getX() + 0.5, outputPos.getY(), outputPos.getZ() + 0.5, copy);
            level.addFreshEntity(itemEntity);
        }
    }

    /**
     * 方块破坏时掉落储存空间中的物品
     */
    public void dropInventory() {
        if (level == null) return;
        for (int i = 0; i < itemStackHandler.getSlots(); i++) {
            ItemStack stack = itemStackHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                ItemEntity itemEntity = new ItemEntity(
                    level,
                    getBlockPos().getX() + 0.5,
                    getBlockPos().getY() + 0.5,
                    getBlockPos().getZ() + 0.5,
                    stack.copy()
                );
                level.addFreshEntity(itemEntity);
            }
        }
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        super.write(compound, clientPacket);
        compound.putFloat("progress", progress);
        compound.put("inventory", itemStackHandler.serializeNBT());
        compound.putInt("color", color);
        compound.putString("weavingType", weavingType.getName());
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        if (compound.contains("progress")) {
            progress = compound.getFloat("progress");
        }
        if (compound.contains("inventory")) {
            itemStackHandler.deserializeNBT(compound.getCompound("inventory"));
        }
        if (compound.contains("color")) {
            color = compound.getInt("color");
        }
        if (compound.contains("weavingType")) {
            weavingType = WeavingType.fromName(compound.getString("weavingType"));
        }
        // 重新查找配方以恢复currentRecipe
        if (level != null && !level.isClientSide()) {
            currentRecipe = findMatchingRecipe();
        }
    }

    @Override
    public float getSpeed() {
        BlockState state = getBlockState();
        if (level != null && !level.isClientSide() && state.hasProperty(LoomControlBlock.FACING)) {
            Direction facing = state.getValue(LoomControlBlock.FACING);
            Direction back = facing.getOpposite();
            BlockPos backPos = getBlockPos().relative(back);
            BlockEntity backBE = level.getBlockEntity(backPos);
            if (backBE instanceof LoomStructureBlockEntity kineticBE) {
                return kineticBE.getSpeed();
            }
        }
        return super.getSpeed();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "weaving_controller", 0, state -> state.setAndContinue(RawAnimation.begin().then("weaving", Animation.LoopType.LOOP))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public double getTick(Object object) {
        if (level != null && level.isClientSide()) {
            return getRenderTick();
        }
        return 0;
    }

    @OnlyIn(Dist.CLIENT)
    public double getRenderTick() {
        float partialTick = Minecraft.getInstance().getPartialTick();
        if (lastProgress > progress) {
            lastProgress -= 160;
        }
        lastProgress = Mth.lerp(partialTick, lastProgress, progress);
        return lastProgress;
    }

    public float getProgress() {
        return progress;
    }

    public int getColor() {
        return color;
    }

    public WeavingType getWeavingType() {
        return weavingType;
    }

    public LazyOptional<IItemHandler> getItemHandlerLazy() {
        return itemHandlerLazy;
    }

    /**
     * 自定义ItemStackHandler，只允许放入有效配方的材料物品
     */
    private class LoomItemStackHandler extends ItemStackHandler {

        public LoomItemStackHandler(int size) {
            super(size);
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            // 检查物品是否是任何配方的材料
            if (level == null || stack.isEmpty()) return false;

            List<WeavingRecipe> recipes = level.getRecipeManager().getAllRecipesFor(WeavingRecipeType.INSTANCE);
            for (WeavingRecipe recipe : recipes) {
                for (net.minecraft.world.item.crafting.Ingredient ingredient : recipe.getIngredients()) {
                    if (ingredient.test(stack)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack)) {
                return stack;
            }
            return super.insertItem(slot, stack, simulate);
        }
    }
}