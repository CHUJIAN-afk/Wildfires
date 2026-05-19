package first.wildfires.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import first.wildfires.kinetic.loom.LoomAuxiliaryBlockEntity;
import first.wildfires.kinetic.loom.LoomControlBlockEntity;
import first.wildfires.kinetic.loom.recipe.WeavingRecipe;
import first.wildfires.kinetic.loom.recipe.WeavingRecipeType;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

public class LoomPonder {

    public static void weave(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("loom", "wildfires.ponder.loom.header");
        scene.configureBasePlate(0, 0, 6);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        // ========== 位置定义 ==========
        // 主角：中间的织布机（位于底座中心区域，原 loom2）
        BlockPos mainAux     = util.grid().at(2, 1, 2);
        BlockPos mainControl = util.grid().at(2, 1, 3);
        BlockPos mainStruct1 = util.grid().at(3, 1, 2);
        BlockPos mainStruct2 = util.grid().at(3, 1, 3);

        // 配角：边缘的织布机（位于主角侧边，原 loom1）
        BlockPos sideAux     = util.grid().at(2, 1, 0);
        BlockPos sideControl = util.grid().at(2, 1, 1);
        BlockPos sideStruct1 = util.grid().at(3, 1, 0);
        BlockPos sideStruct2 = util.grid().at(3, 1, 1);

        // 动力组
        BlockPos cog1  = util.grid().at(3, 1, 5);
        BlockPos cog2  = util.grid().at(4, 1, 5);
        BlockPos motor = util.grid().at(4, 1, 6);

        // 选择区域
        Selection mainAll        = util.select().fromTo(2, 1, 2, 3, 1, 3);
        Selection sideAll        = util.select().fromTo(2, 1, 0, 3, 1, 1);
        Selection kineticNetwork = util.select().fromTo(2, 1, 1, 4, 1, 6);

        // ========== 第一部分：展示中间的织布机（图1） ==========
        scene.world().setKineticSpeed(mainAll, 0);
        scene.world().showSection(mainAll, Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(20)
                .attachKeyFrame()
                .placeNearTarget()
                .colored(PonderPalette.WHITE)
                .text("wildfires.ponder.loom.text_1")
                .pointAt(util.vector().topOf(mainControl));
        scene.idle(40);

        // ========== 第二部分：线轴与原料输入（图2-3） ==========
        scene.rotateCameraY(180); // 转到背面，展示传动杆与输入口
        scene.idle(10);

        scene.overlay().showText(40)
                .attachKeyFrame()
                .placeNearTarget()
                .colored(PonderPalette.INPUT)
                .text("wildfires.ponder.loom.text_2")
                .pointAt(util.vector().topOf(mainStruct1));
        scene.idle(40);

        // 展示右键放入原料
        ItemStack itemStack = null;
        WeavingRecipe weavingRecipe;
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            List<WeavingRecipe> recipes = level.getRecipeManager().getAllRecipesFor(WeavingRecipeType.INSTANCE);
            if (!recipes.isEmpty()) {
                weavingRecipe = recipes.get(0);
                NonNullList<Ingredient> ingredients = weavingRecipe.getIngredients();
                List<Ingredient> list = ingredients.stream().toList();
                if (!list.isEmpty()) {
                    Ingredient ingredient = list.get(0);
                    if (!ingredient.isEmpty()) {
                        itemStack = ingredient.getItems()[0];
                    }
                }
            } else {
                weavingRecipe = null;
            }
        } else {
            weavingRecipe = null;
        }
        scene.overlay().showControls(util.vector().topOf(mainStruct2), Pointing.DOWN, 20)
                .rightClick()
                .withItem(itemStack != null ? itemStack : Items.BLUE_WOOL.getDefaultInstance());
        scene.idle(10);

        if (weavingRecipe != null) {
            // 从客户端 level 获取第一个可能的配方并设置到主角方块实体，动画会自动配合
            scene.world().modifyBlockEntity(mainControl, LoomControlBlockEntity.class, be -> be.setCurrentRecipe(weavingRecipe));
            scene.world().modifyBlockEntity(mainAux, LoomAuxiliaryBlockEntity.class, be -> be.setCurrentRecipe(weavingRecipe));
            scene.idle(20);
        }

        scene.overlay().showText(40)
                .attachKeyFrame()
                .placeNearTarget()
                .colored(PonderPalette.INPUT)
                .text("wildfires.ponder.loom.text_3")
                .pointAt(util.vector().topOf(mainControl));
        scene.idle(40);

        // 展示漏斗/仓口输入（图3）
        scene.overlay().showControls(util.vector().topOf(mainStruct2), Pointing.DOWN, 40)
                .withItem(new ItemStack(Blocks.HOPPER));
        scene.idle(20);

        scene.overlay().showText(60)
                .attachKeyFrame()
                .placeNearTarget()
                .colored(PonderPalette.INPUT)
                .text("wildfires.ponder.loom.text_4")
                .pointAt(util.vector().topOf(mainStruct2));
        scene.idle(40);

        // ========== 第三部分：动力输入（图4） ==========
        scene.rotateCameraY(-90); // 转到侧面，展示动力组
        scene.idle(10);

        scene.world().showSection(util.select().fromTo(2, 1, 4, 4, 1, 5), Direction.DOWN);
        scene.idle(15);

        // 通过创造马达设定初始旋转方向 -16，随后统一应用到整个网络
        scene.world().setKineticSpeed(kineticNetwork, 16.0f);

        scene.overlay().showText(40)
                .attachKeyFrame()
                .placeNearTarget()
                .colored(PonderPalette.BLUE)
                .text("wildfires.ponder.loom.text_5")
                .pointAt(util.vector().topOf(cog1));
        scene.idle(60);

        // ========== 第四部分：串联边缘的织布机（图5） ==========
        scene.world().showSection(sideAll, Direction.DOWN);
        scene.idle(20);

        // 新出现的织布机与主角共享同一根轴，自动同向运转
        scene.world().setKineticSpeed(sideAll, 16.0f);

        scene.overlay().showText(40)
                .attachKeyFrame()
                .placeNearTarget()
                .colored(PonderPalette.GREEN)
                .text("wildfires.ponder.loom.text_6")
                .pointAt(util.vector().topOf(sideControl));
        scene.idle(60);

        // ========== 第五部分：旋转方向错误会卡住（图6） ==========
        // 通过创造马达反转方向为 16；织布机内部判定为反向，progress 不再增加，动画自动停住
        scene.world().setKineticSpeed(kineticNetwork, -16.0f);

        scene.overlay().showText(20)
                .attachKeyFrame()
                .placeNearTarget()
                .colored(PonderPalette.RED)
                .text("wildfires.ponder.loom.text_7")
                .pointAt(util.vector().topOf(mainControl));
        scene.idle(40);

        scene.overlay().showText(40)
                .attachKeyFrame()
                .placeNearTarget()
                .colored(PonderPalette.RED)
                .text("wildfires.ponder.loom.text_8")
                .pointAt(util.vector().topOf(mainControl));
        scene.idle(60);

        // 恢复正确方向，使场景以正常状态结束
        scene.world().setKineticSpeed(kineticNetwork, 16.0f);

        scene.markAsFinished();
    }
}