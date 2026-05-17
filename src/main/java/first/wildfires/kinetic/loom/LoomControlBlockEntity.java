package first.wildfires.kinetic.loom;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import first.wildfires.kinetic.loom.recipe.IngredientWithCount;
import first.wildfires.kinetic.loom.recipe.WeavingRecipe;
import first.wildfires.kinetic.loom.recipe.WeavingRecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class LoomControlBlockEntity extends KineticBlockEntity implements GeoBlockEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final LoomItemStackHandler itemStackHandler = new LoomItemStackHandler(4);
    private final LazyOptional<IItemHandler> itemHandlerLazy = LazyOptional.of(() -> itemStackHandler);
    private float clientTargetProgress = 0;
    private float lastProgress = 0;
    private float progress = 0;
    private double renderTick = -1;

    @Nullable
    private WeavingRecipe currentRecipe;

    public LoomControlBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    /**
     * 返回渲染边界框，扩展到包含整个多方块结构
     * 防止玩家视角不在控制块所在格子时被剔除
     */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(getBlockPos()).inflate(2);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;
        if (!level.isClientSide()) {
            float speed = getSpeed();
            if (currentRecipe == null) {
                // 没有正在执行的配方，尝试查找配方
                WeavingRecipe matchedRecipe = findMatchingRecipe();
                if (matchedRecipe != null) {
                    currentRecipe = matchedRecipe;
                }
            } else {
                // 检查当前物品是否仍然满足配方要求
                if (!matchesCurrentRecipe()) {
                    // 物品变化，清空进度和配方
                    currentRecipe = null;
                    progress = 0;
                } else {
                    // 执行配方
                    BlockState blockState = level.getBlockState(getBlockPos());
                    if (blockState.hasProperty(LoomControlBlock.FACING)) {
                        Direction direction = blockState.getValue(LoomControlBlock.FACING);
                        // EAST和NORTH朝向：正向为负速度，反向为正速度
                        // SOUTH和WEST朝向：正向为正速度，反向为负速度
                        boolean isForward;
                        if (direction == Direction.EAST || direction == Direction.NORTH) {
                            isForward = speed < 0;
                        } else {
                            isForward = speed > 0;
                        }

                        if (isForward) {
                            progress += Math.abs(speed) / 60;
                        }
                    }

                    // 检查是否完成
                    if (progress > 160) {
                        progress -= 160;
                        // 产出物品
                        outputResults();
                    }
                }
            }
            setChanged();
            sendData();
        } else {
            lastProgress = progress;
            progress = clientTargetProgress;
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
        List<IngredientWithCount> ingredientsWithCount = recipe.getIngredientsWithCount();
        if (ingredientsWithCount.isEmpty()) {
            // 兼容旧逻辑
            List<Ingredient> ingredients = recipe.getIngredients();
            boolean[] used = new boolean[itemStackHandler.getSlots()];

            for (Ingredient ingredient : ingredients) {
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

        // 使用带数量的匹配逻辑
        // 创建槽位物品的副本用于匹配
        List<ItemStack> availableItems = new ArrayList<>();
        for (int i = 0; i < itemStackHandler.getSlots(); i++) {
            ItemStack stack = itemStackHandler.getStackInSlot(i).copy();
            if (!stack.isEmpty()) {
                availableItems.add(stack);
            }
        }

        // 检查每个成分需求
        for (IngredientWithCount iwc : ingredientsWithCount) {
            int needed = iwc.count();
            int found = 0;

            for (int i = 0; i < availableItems.size() && found < needed; i++) {
                ItemStack available = availableItems.get(i);
                if (!available.isEmpty() && iwc.ingredient().test(available)) {
                    int canTake = Math.min(available.getCount(), needed - found);
                    available.shrink(canTake);
                    found += canTake;
                }
            }

            if (found < needed) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesCurrentRecipe() {
        if (currentRecipe == null) return false;
        return matchesRecipe(currentRecipe);
    }

    public void setCurrentRecipe(@Nullable WeavingRecipe currentRecipe) {
        this.currentRecipe = currentRecipe;
    }

    private void outputResults() {
        if (currentRecipe == null || level == null) return;

        // 消耗输入物品（使用带数量的逻辑）
        List<IngredientWithCount> ingredientsWithCount = currentRecipe.getIngredientsWithCount();

        if (!ingredientsWithCount.isEmpty()) {
            // 使用带数量的消耗逻辑
            for (IngredientWithCount iwc : ingredientsWithCount) {
                int needed = iwc.count();
                int remaining = needed;

                for (int i = 0; i < itemStackHandler.getSlots() && remaining > 0; i++) {
                    ItemStack stack = itemStackHandler.getStackInSlot(i);
                    if (!stack.isEmpty() && iwc.ingredient().test(stack)) {
                        int toExtract = Math.min(stack.getCount(), remaining);
                        stack.shrink(toExtract);
                        remaining -= toExtract;
                    }
                }
            }
        } else {
            // 兼容旧逻辑
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
        }

        // 在方块上方生成产物（不掉入储存空间）
        Vec3 blockPos = getBlockPos().getCenter();
        Vec3 facePos = getBlockPos().relative(getBlockState().getValue(LoomControlBlock.FACING).getClockWise()).getCenter();
        Vec3 targetPos = blockPos.add(facePos);
        for (ItemStack output : currentRecipe.getOutputs()) {
            ItemStack copy = output.copy();
            ItemEntity itemEntity = new ItemEntity(level, targetPos.x() / 2, (targetPos.y() / 2) + 0.5f, targetPos.z() / 2, copy);
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
        if (currentRecipe != null) {
            compound.putString("recipe", currentRecipe.getId().toString());
        }
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        super.read(compound, clientPacket);
        if (compound.contains("progress")) {
            clientTargetProgress = compound.getFloat("progress");
        }
        if (compound.contains("inventory")) {
            itemStackHandler.deserializeNBT(compound.getCompound("inventory"));
        }
        if (compound.contains("recipe") && level != null) {
            List<WeavingRecipe> allRecipesFor = level.getRecipeManager().getAllRecipesFor(WeavingRecipeType.INSTANCE);
            for (WeavingRecipe weavingRecipe : allRecipesFor) {
                if (weavingRecipe.getId().toString().equals(compound.getString("recipe"))) {
                    currentRecipe = weavingRecipe;
                    break;
                }
            }
        } else {
            currentRecipe = null;
        }
    }

    @Override
    public float getSpeed() {
        BlockState state = getBlockState();
        if (level != null && state.hasProperty(LoomControlBlock.FACING)) {
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
        if (renderTick != -1) {
            return renderTick;
        }
        if (level != null && level.isClientSide()) {
            return getRenderTick();
        }
        return 0;
    }

    public void setRenderTick(double renderTick) {
        this.renderTick = renderTick;
    }

    @OnlyIn(Dist.CLIENT)
    public double getRenderTick() {
        float partialTick = Minecraft.getInstance().getPartialTick();
        if (lastProgress - progress > 100) {
            return Mth.lerp(partialTick, lastProgress - 160, progress);
        }
        return Mth.lerp(partialTick, lastProgress, progress);
    }

    @Nullable
    public WeavingRecipe getCurrentRecipe() {
        return currentRecipe;
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

    @Override
    public float calculateStressApplied() {
        BlockState blockState = getBlockState();
        if (level != null && level.isClientSide() && blockState.hasProperty(LoomControlBlock.FACING)) {
            BlockEntity blockEntity = level.getBlockEntity(getBlockPos().relative(blockState.getValue(LoomControlBlock.FACING).getOpposite()));
            if (blockEntity instanceof LoomStructureBlockEntity loomStructureBlockEntity) {
                return loomStructureBlockEntity.calculateStressApplied();
            }
        }
        return super.calculateStressApplied();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        // 状态显示
        CreateLang.text("织布机状态：").style(ChatFormatting.WHITE)
                .space()
                .add(CreateLang.text(currentRecipe != null ? "编织中" : "空闲").style(currentRecipe != null ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                .forGoggles(tooltip);

        // 当前配方信息
        if (currentRecipe != null) {
            CreateLang.text("正在编织").style(ChatFormatting.GRAY)
                    .forGoggles(tooltip, 1);
            CreateLang.text(currentRecipe.getWeavingType().getDisplayName()).style(ChatFormatting.BLUE)
                    .forGoggles(tooltip, 2);

            // 进度显示
            CreateLang.text("编织进度").style(ChatFormatting.GRAY)
                    .forGoggles(tooltip, 1);
            int progressPercent = (int) (progress / 160 * 100);
            CreateLang.number(progressPercent).style(ChatFormatting.GOLD)
                    .add(CreateLang.text("%").style(ChatFormatting.GOLD))
                    .forGoggles(tooltip, 2);
        }

        // 输入槽状态
        CreateLang.text("输入槽").style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        int filledSlots = 0;
        for (int i = 0; i < itemStackHandler.getSlots(); i++) {
            if (!itemStackHandler.getStackInSlot(i).isEmpty()) {
                filledSlots++;
            }
        }
        CreateLang.number(filledSlots).style(ChatFormatting.YELLOW)
                .add(CreateLang.text(" / ").style(ChatFormatting.GRAY))
                .add(CreateLang.number(itemStackHandler.getSlots()).style(ChatFormatting.DARK_GRAY))
                .add(CreateLang.text(" 格").style(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 2);

        // 动力状态
        float speed = getSpeed();
        if (speed == 0 && currentRecipe != null) {
            CreateLang.text("缺少动力输入").style(ChatFormatting.RED)
                    .forGoggles(tooltip, 1);
        } else if (speed != 0) {
            CreateLang.text("动力状态").style(ChatFormatting.GRAY)
                    .forGoggles(tooltip, 1);

            // 根据朝向判定正向/反向
            BlockState blockState = getBlockState();
            if (blockState.hasProperty(LoomControlBlock.FACING)) {
                boolean isForward;
                Direction direction = blockState.getValue(LoomControlBlock.FACING);
                if (direction == Direction.EAST || direction == Direction.NORTH) {
                    isForward = speed < 0;
                } else {
                    isForward = speed > 0;
                }
                CreateLang.number(Math.abs(speed)).style(ChatFormatting.AQUA)
                        .add(CreateLang.text(" RPM").style(ChatFormatting.AQUA))
                        .space()
                        .add(CreateLang.text(isForward ? "正向运转" : "反向运转").style(ChatFormatting.DARK_GRAY))
                        .forGoggles(tooltip, 2);
            }
        }
        float stressAtBase = this.calculateStressApplied();
        if (!Mth.equal(stressAtBase, 0.0F)) {
            CreateLang.translate("tooltip.stressImpact")
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip, 1);
            float stressTotal = stressAtBase * Math.abs(this.getSpeed());
            CreateLang.number(stressTotal)
                    .translate("generic.unit.stress")
                    .style(ChatFormatting.AQUA)
                    .space()
                    .add(CreateLang.translate("gui.goggles.at_current_speed").style(ChatFormatting.DARK_GRAY))
                    .forGoggles(tooltip, 2);

        }
        return true;
    }
}