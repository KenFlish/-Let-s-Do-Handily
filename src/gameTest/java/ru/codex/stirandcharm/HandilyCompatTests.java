package ru.codex.stirandcharm;

import java.util.*;
import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.station.*;
import com.pockyl.diegesis.api.state.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.*;
import net.satisfy.farm_and_charm.core.block.CuttingBoardBlock;
import net.satisfy.farm_and_charm.core.block.entity.*;
import net.satisfy.farm_and_charm.core.recipe.*;

@GameTestHolder(StirAndCharm.ID)
@PrefixGameTestTemplate(false)
public final class HandilyCompatTests {
    private record Context(ServerLevel level,BlockPos pos,StationType type,StationState state) implements StationContext {
        public BlockState blockState(){return level.getBlockState(pos);}
        public Direction facing(){return Direction.NORTH;}
        public Collection<? extends Player> users(){return List.of();}
        public void effect(ResourceLocation id,String node,CompoundTag tag){}
    }
    private static Context setup(GameTestHelper h,String block,String station) {
        var pos=new BlockPos(2,2,2);
        h.setBlock(pos.below(),Blocks.REDSTONE_BLOCK);
        var target=BuiltInRegistries.BLOCK.get(ResourceLocation.parse(block));
        h.assertTrue(target!=Blocks.AIR,"Missing block "+block);
        h.setBlock(pos,Blocks.AIR);h.setBlock(pos,target);
        var type=Diegesis.stationType(StirAndCharm.id(station)).orElseThrow();
        return new Context(h.getLevel(),h.absolutePos(pos),type,new StationState(type.schema()));
    }
    private static CompoundTag fill(RecipeHolder<?> h) {var t=new CompoundTag();t.putString("recipe",h.id().toString());return t;}
    @GameTest(template="empty",timeoutTicks=160)
    public static void candlelightPanSurvivesNativeTicks(GameTestHelper h) {
        if(!net.neoforged.fml.ModList.get().isLoaded("candlelight")){h.succeed();return;}
        var ctx=setup(h,"candlelight:cooking_pan","candlelight_pan");
        var logic=new ApplianceLogic(ctx,Appliance.ROASTER);
        logic.onOpen(ctx,h.makeMockPlayer(GameType.SURVIVAL));
        h.assertTrue(!ctx.blockState().hasProperty(RoasterLid.OPEN),"A pan must not acquire a roaster lid");
        h.runAtTickTime(60,()->{
            logic.tick(ctx);
            h.assertTrue(ctx.blockEntity() instanceof RoasterBlockEntity,"Pan keeps native cooking entity");h.succeed();
        });
    }
    @GameTest(template="empty") public static void candlelightStationsBindNativeInventories(GameTestHelper h) {
        if(!net.neoforged.fml.ModList.get().isLoaded("candlelight")){h.succeed();return;}
        var player=h.makeMockPlayer(GameType.SURVIVAL);
        h.assertTrue(Diegesis.stationType(StirAndCharm.id("candlelight_basalt_stove")).isEmpty(),"Candlelight stoves must keep their own interface");
        var ctx=setup(h,"candlelight:cooking_pot","candlelight_pot");
        var logic=new KitchenLogic(ctx,false);logic.onOpen(ctx,player);logic.tick(ctx);
        ctx.state().setItem(SlotRef.of("ingredients",0),new ItemStack(Items.CARROT));
        h.assertTrue(((CookingPotBlockEntity)ctx.blockEntity()).getItem(0).is(Items.CARROT),"Pot slot must remain native");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=220) public static void boardCutsWithNativeDurabilityAndByproducts(GameTestHelper h) {
        cutWith(h,true);
    }
    @GameTest(template="empty",timeoutTicks=220) public static void boardCutsWithOrdinaryKnife(GameTestHelper h) {
        if(!net.neoforged.fml.ModList.get().isLoaded("farmersdelight")){h.succeed();return;}
        cutWith(h,false);
    }
    private static void cutWith(GameTestHelper h,boolean cleaver) {
        var ctx=setup(h,"farm_and_charm:cutting_board","cutting_board");var logic=new BoardLogic(ctx);
        var board=(CuttingBoardBlockEntity)ctx.blockEntity();var p=h.makeMockPlayer(GameType.SURVIVAL);
        var tool=BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(s -> BoardTools.knife(s) && s.isDamageableItem()
            && (s.getItem() instanceof net.satisfy.farm_and_charm.core.item.CleaverItem)==cleaver).findFirst().orElseThrow();
        board.setKnife(tool.copy());
        var holder=BoardRecipes.all(ctx.level()).stream().filter(r -> r.value() instanceof CuttingBoardRecipe && r.value().getIngredients().getFirst().getItems().length>0).findFirst().orElseThrow();
        var recipe=(CuttingBoardRecipe)holder.value();p.getInventory().add(recipe.getIngredient().getItems()[0].copyWithCount(1));
        logic.onCustomAction(ctx,p,"fill",fill(holder));
        h.assertTrue(board.isCutting() && p.getInventory().isEmpty(),"One input transferred to native pending slot");
        int count=CuttingBoardBlockEntity.requiredChops(tool,recipe);
        for(int i=0;i<count;i++) h.runAtTickTime(1+i*5,()->{
            int before=board.getPendingChops();var tag=new CompoundTag();tag.putInt("mark",before);tag.putFloat("x",8);tag.putFloat("z",BoardTools.markZ(before,count));
            var wrong=tag.copy();wrong.putFloat("z",Float.NaN);logic.onCustomAction(ctx,p,"chop",wrong);
            h.assertTrue(board.getPendingChops()==before,"Invalid cut position must be rejected");
            logic.onCustomAction(ctx,p,"chop",tag);
            if(board.isCutting()) {logic.onCustomAction(ctx,p,"chop",tag);h.assertTrue(board.getPendingChops()==before+1,"Same-tick spam must not cut twice");}
        });
        h.runAtTickTime(count*5+5,()->{
            h.assertTrue(!board.isCutting(),"Native chop threshold reached");
            var outputs=new ArrayList<ItemStack>();outputs.add(recipe.getResult());outputs.addAll(recipe.getByproducts());
            for(var expected:outputs) {
                int actual=Arrays.stream(ctx.state().getItems("result")).filter(s -> ItemStack.isSameItemSameComponents(s,expected)).mapToInt(ItemStack::getCount).sum();
                int wanted=outputs.stream().filter(s -> ItemStack.isSameItemSameComponents(s,expected)).mapToInt(ItemStack::getCount).sum();
                h.assertTrue(actual==wanted,"Result and byproducts must match native recipe");
            }
            h.assertTrue(board.getKnife().getDamageValue()==tool.getDamageValue()+1,"Native knife durability charged once");h.succeed();
        });
    }
    @GameTest(template="empty") public static void boardAssemblyRecipesIncludeFinisher(GameTestHelper h) {
        int tested=0;
        for(var holder:BoardRecipes.all(h.getLevel())) if(holder.value() instanceof CuttingBoardAssemblyRecipe recipe && recipe.getIngredients().stream().allMatch(i -> i.getItems().length>0)) {
            var ctx=setup(h,"farm_and_charm:cutting_board","cutting_board");var logic=new BoardLogic(ctx);
            var board=(CuttingBoardBlockEntity)ctx.blockEntity();var p=h.makeMockPlayer(GameType.SURVIVAL);
            // Deliberately seed inventory backwards: the finisher still belongs last on the board.
            for(var ingredient:recipe.getIngredients().reversed()) p.getInventory().add(ingredient.getItems()[0].copyWithCount(1));
            logic.onCustomAction(ctx,p,"fill",fill(holder));
            h.assertTrue(recipe.matches(new CuttingBoardAssemblyRecipe.Input(board.getItems()),ctx.level()),"Transferred layout matches "+holder.id());
            logic.onCustomAction(ctx,p,"assemble",new CompoundTag());
            h.assertTrue(board.isEmpty() && p.getInventory().isEmpty(),"Assembly consumes each ingredient once");
            h.assertTrue(ItemStack.matches(ctx.state().getItems("result")[0],recipe.getResult()),"Native assembly output preserved");tested++;
        }
        h.assertTrue(tested>0,"Assembly recipes exercised");h.succeed();
    }
    @GameTest(template="empty") public static void boardMissingIngredientIsAtomic(GameTestHelper h) {
        var recipe=new CuttingBoardAssemblyRecipe(List.of(Ingredient.of(Items.WHEAT),Ingredient.of(Items.CARROT)),Optional.of(Ingredient.of(Items.BOWL)),new ItemStack(Items.BREAD),true);
        var p=h.makeMockPlayer(GameType.SURVIVAL);p.getInventory().add(new ItemStack(Items.CARROT));
        var before=p.getInventory().getItem(0).copy();
        h.assertTrue(BoardRecipes.plan(p.getInventory(),List.of(new ItemStack(Items.WHEAT)),recipe).isEmpty(),"Missing finisher rejects whole recipe");
        h.assertTrue(ItemStack.matches(before,p.getInventory().getItem(0)),"Failure must leave inventory unchanged");h.succeed();
    }
}
