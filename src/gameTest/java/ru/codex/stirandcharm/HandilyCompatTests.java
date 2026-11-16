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
        h.assertTrue(board.isCutting() && p.getInventory().countItem(tool.getItem())==1,"One input transferred to native pending slot");
        int count=CuttingBoardBlockEntity.requiredChops(tool,recipe);
        for(int i=0;i<count;i++) h.runAtTickTime(1+i*5,()->{
            int mark=(int)ctx.state().getControl("chops");
            var tag=new CompoundTag();tag.putInt("mark",mark);tag.putFloat("at",8);
            var wrong=tag.copy();wrong.putFloat("at",Float.NaN);logic.onCustomAction(ctx,p,"chop",wrong);
            h.assertTrue((int)ctx.state().getControl("chops")==mark,"Invalid cut position must be rejected");
            int before=Arrays.stream(ctx.state().getItems("result")).mapToInt(ItemStack::getCount).sum();
            logic.onCustomAction(ctx,p,"chop",tag);
            logic.onCustomAction(ctx,p,"chop",tag);
            h.assertTrue(Arrays.stream(ctx.state().getItems("result")).mapToInt(ItemStack::getCount).sum()>=before,"The same tick cuts at most once");
        });
        h.runAtTickTime(count*5+5,()->{
            h.assertTrue(!board.isCutting(),"Native chop threshold reached");
            var outputs=new ArrayList<ItemStack>();outputs.add(recipe.getResult());outputs.addAll(recipe.getByproducts());
            for(var expected:outputs) {
                int actual=Arrays.stream(ctx.state().getItems("result")).filter(s -> ItemStack.isSameItemSameComponents(s,expected)).mapToInt(ItemStack::getCount).sum();
                int wanted=outputs.stream().filter(s -> ItemStack.isSameItemSameComponents(s,expected)).mapToInt(ItemStack::getCount).sum();
                h.assertTrue(actual==wanted,"Result and byproducts must match native recipe");
            }
            h.assertTrue(p.getInventory().items.stream().anyMatch(s -> s.is(tool.getItem()) && s.getDamageValue()==tool.getDamageValue()+1),"Native knife durability charged once");h.succeed();
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
            var expected=KitchenRecipes.result(ctx.level(),recipe);
            if(!board.isEmpty()){
                h.assertTrue(recipe.matches(new CuttingBoardAssemblyRecipe.Input(board.getItems()),ctx.level()),"Transferred layout matches "+holder.id());
                logic.onCustomAction(ctx,p,"assemble",new CompoundTag());
            }
            h.assertTrue(board.isEmpty(),"Assembly consumes the layers");
            h.assertTrue(Arrays.stream(ctx.state().getItems("result")).anyMatch(s -> ItemStack.isSameItemSameComponents(s,expected)),
                "Native assembly output preserved for "+holder.id()+": wanted "+expected+", got "+ctx.state().getItems("result")[0]);tested++;
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
    @GameTest(template="empty",timeoutTicks=220) public static void boardCursorQueueAndBorrowedKnife(GameTestHelper h){
        var c=setup(h,"farm_and_charm:cutting_board","cutting_board");var logic=new BoardLogic(c);
        var board=(CuttingBoardBlockEntity)c.blockEntity();var p=h.makeMockPlayer(GameType.SURVIVAL);
        var recipe=(CuttingBoardRecipe)BoardRecipes.all(c.level()).stream().filter(r -> r.value() instanceof CuttingBoardRecipe && !r.id().getPath().startsWith("stripping/")).findFirst().orElseThrow().value();
        var raw=recipe.getIngredient().getItems()[0].copyWithCount(3);var rawItem=raw.getItem();p.inventoryMenu.setCarried(raw);
        logic.onCustomAction(c,p,"place",new CompoundTag());
        h.assertTrue(p.inventoryMenu.getCarried().isEmpty() && board.getPending().getCount()==3,"Cursor food is placed directly without a parked knife");
        var tool=BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(s -> BoardTools.knife(s) && s.isDamageableItem()).findFirst().orElseThrow();
        p.inventoryMenu.setCarried(tool.copy());
        int required=CuttingBoardBlockEntity.requiredChops(tool,recipe);
        for(int i=0;i<required;i++)h.runAtTickTime(1+i*5,()->{
            var tag=new CompoundTag();tag.putInt("mark",(int)c.state().getControl("chops"));tag.putFloat("at",8);logic.onCustomAction(c,p,"chop",tag);
        });
        h.runAtTickTime(required*5+5,()->{
            h.assertTrue(board.getPending().isEmpty(),"The last mark cuts the whole queued batch");
            h.assertTrue(board.getKnife().isEmpty() && p.inventoryMenu.getCarried().getDamageValue()==tool.getDamageValue()+3,"Every queued item wears the borrowed knife");
            h.assertTrue(Arrays.stream(c.state().getItems("result")).mapToInt(ItemStack::getCount).sum()>=3,"Every queued item gives its result");
            var saved=board.saveWithoutMetadata(c.level().registryAccess());
            var restored=new CuttingBoardBlockEntity(c.pos(),c.blockState());restored.loadWithComponents(saved,c.level().registryAccess());
            h.assertTrue(restored.getPending().isEmpty() && restored.getPendingChops()==0,"A finished batch persists empty");
            h.succeed();
        });
    }
    @GameTest(template="empty") public static void boardCarvedKnifeAndFullOutput(GameTestHelper h){
        var c=setup(h,"farm_and_charm:cutting_board","cutting_board");var logic=new BoardLogic(c);
        var board=(CuttingBoardBlockEntity)c.blockEntity();var p=h.makeMockPlayer(GameType.SURVIVAL);
        var knife=BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(BoardTools::knife).findFirst().orElseThrow();
        p.inventoryMenu.setCarried(knife.copy());logic.onCustomAction(c,p,"carve",new CompoundTag());
        h.assertTrue(board.hasKnife() && p.inventoryMenu.getCarried().isEmpty(),"Knife can be stuck in an empty board");
        var saved=board.saveWithoutMetadata(c.level().registryAccess());var restored=new CuttingBoardBlockEntity(c.pos(),c.blockState());restored.loadWithComponents(saved,c.level().registryAccess());
        h.assertTrue(ItemStack.matches(restored.getKnife(),knife),"Carved knife survives native save/load");
        var recipe=(CuttingBoardRecipe)BoardRecipes.all(c.level()).stream().filter(r -> r.value() instanceof CuttingBoardRecipe && !r.id().getPath().startsWith("stripping/")).findFirst().orElseThrow().value();
        p.inventoryMenu.setCarried(recipe.getIngredient().getItems()[0].copyWithCount(2));logic.onCustomAction(c,p,"place",new CompoundTag());
        for(int i=0;i<8;i++)c.state().setItem(SlotRef.of("result",i),new ItemStack(Items.BEDROCK,64));
        var tag=new CompoundTag();tag.putInt("mark",0);tag.putFloat("x",6);tag.putFloat("z",8);logic.onCustomAction(c,p,"chop",tag);
        h.assertTrue(board.getPendingChops()==0 && board.getPending().getCount()==2,"A full output cannot consume queued food or tool durability");h.succeed();
    }
    @GameTest(template="empty") public static void boardOnlyAssemblesFarmCharmCrafts(GameTestHelper h){
        var c=setup(h,"farm_and_charm:cutting_board","cutting_board");var logic=new BoardLogic(c);var p=h.makeMockPlayer(GameType.SURVIVAL);
        var crafts=BoardDishes.crafting(c.level());
        h.assertTrue(!crafts.isEmpty(),"Farm & Charm has food crafts for the board");
        h.assertTrue(crafts.stream().allMatch(r -> r.id().getNamespace().equals("farm_and_charm")),
            "The board never turns into a generic crafting table for other mods");
        h.assertTrue(crafts.stream().noneMatch(r -> r.value().getResultItem(c.level().registryAccess()).is(Items.CAKE)),
            "Vanilla cake stays in the crafting table");
        var holder=crafts.stream().filter(r -> r.value().getIngredients().stream().allMatch(i -> i.getItems().length>0))
            .findFirst().orElseThrow();
        for(var ingredient:holder.value().getIngredients())p.getInventory().add(ingredient.getItems()[0].copyWithCount(1));
        logic.onCustomAction(c,p,"fill",fill(holder));logic.onCustomAction(c,p,"assemble",new CompoundTag());
        var expected=KitchenRecipes.result(c.level(),holder.value());
        h.assertTrue(Arrays.stream(c.state().getItems("result")).anyMatch(s -> ItemStack.isSameItemSameComponents(s,expected)),
            "A Farm & Charm crafting dish still assembles on the board: "+holder.id());h.succeed();
    }
    @GameTest(template="empty") public static void boardPlacedStackPersistsWithoutEmptyEntry(GameTestHelper h){
        var c=setup(h,"farm_and_charm:cutting_board","cutting_board");var logic=new BoardLogic(c);
        var board=(CuttingBoardBlockEntity)c.blockEntity();var p=h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack base=ItemStack.EMPTY;
        for(var dish:BoardAssembly.all(c.level())) {
            var ingredient=dish.ingredients().get(dish.base());
            for(var candidate:ingredient.getItems()) {
                var stack=candidate.copyWithCount(1);
                if(stack.getMaxStackSize()>2 && !BoardTools.knife(stack) && BoardRecipes.cut(c.level(),stack).isEmpty()
                    && BoardAssembly.isBase(c.level(),stack)) {base=stack;break;}
            }
            if(!base.isEmpty())break;
        }
        h.assertTrue(!base.isEmpty(),"A non-cutting dish base exists");
        var stack=base.copyWithCount(3);p.getInventory().setItem(0,stack);p.getInventory().selected=0;
        var tag=new CompoundTag();tag.putInt("from",0);logic.onCustomAction(c,p,"place",tag);
        h.assertTrue(board.getItems().size()==1 && ItemStack.isSameItemSameComponents(board.getItems().get(0),base)
            && board.getItems().get(0).getCount()==3,"The whole base stack is kept once, with no AIR entry");
        var saved=board.saveWithoutMetadata(c.level().registryAccess());
        h.assertTrue(saved.getList("Items",10).size()==1,"Native save has exactly one real item");
        var restored=new CuttingBoardBlockEntity(c.pos(),c.blockState());
        restored.loadWithComponents(saved,c.level().registryAccess());
        h.assertTrue(restored.getItems().size()==1 && restored.getItems().get(0).getCount()==3,
            "The native board reloads the same layer count without an empty stack");h.succeed();
    }
    @GameTest(template="empty") public static void boardTopLayerComesBackAsAWhole(GameTestHelper h){
        var c=setup(h,"farm_and_charm:cutting_board","cutting_board");var logic=new BoardLogic(c);
        var board=(CuttingBoardBlockEntity)c.blockEntity();var p=h.makeMockPlayer(GameType.SURVIVAL);
        board.addItem(new ItemStack(Items.CARROT,3));
        logic.onCustomAction(c,p,"take",new CompoundTag());
        h.assertTrue(board.getItems().isEmpty(),"Removing the top layer clears only that layer");
        h.assertTrue(p.getInventory().countItem(Items.CARROT)==3,"Right-click returns the whole top layer, as HOD does");h.succeed();
    }
    @GameTest(template="empty") public static void cleaverAndKnifeNeedTheSameChops(GameTestHelper h){
        var recipe=BoardRecipes.all(h.getLevel()).stream().filter(r -> r.value() instanceof CuttingBoardRecipe c && c.getIngredient().getItems().length>0)
            .map(r -> (CuttingBoardRecipe)r.value()).findFirst().orElseThrow();
        var cleaver=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("farm_and_charm:iron_cleaver")));
        h.assertTrue(!cleaver.isEmpty() && cleaver.getItem() instanceof net.satisfy.farm_and_charm.core.item.CleaverItem,"The Farm & Charm cleaver exists");
        h.assertTrue(CuttingBoardBlockEntity.requiredChops(cleaver,recipe)==Math.min(3,Math.max(1,recipe.getChops())),"A cleaver uses the recipe's own chop count");
        var knife=BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(s -> BoardTools.knife(s) && !(s.getItem() instanceof net.satisfy.farm_and_charm.core.item.CleaverItem)).findFirst();
        if(knife.isPresent())h.assertTrue(CuttingBoardBlockEntity.requiredChops(knife.get(),recipe)==Math.min(3,Math.max(1,recipe.getChops())),"A knife uses the recipe's own chop count");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void boardAssemblyMakesEveryRound(GameTestHelper h){
        var c=setup(h,"farm_and_charm:cutting_board","cutting_board");var logic=new BoardLogic(c);
        var board=(CuttingBoardBlockEntity)c.blockEntity();var p=h.makeMockPlayer(GameType.SURVIVAL);
        var holder=BoardRecipes.all(c.level()).stream().filter(r -> r.value() instanceof CuttingBoardAssemblyRecipe a
            && a.getIngredients().stream().allMatch(i -> i.getItems().length>0)).findFirst().orElseThrow();
        for(var ingredient:holder.value().getIngredients())board.addItem(ingredient.getItems()[0].copyWithCount(3));
        ((BoardSelection)board).handily$recipe(holder.id());
        logic.onCustomAction(c,p,"assemble",new CompoundTag());
        int made=Arrays.stream(c.state().getItems("result")).mapToInt(ItemStack::getCount).sum();
        int per=KitchenRecipes.result(c.level(),holder.value()).getCount();
        h.assertTrue(board.isEmpty() && made==3*per,"Three layers make three rounds, not one");
        h.succeed();
    }
    @GameTest(template="empty") public static void removedStationsStayNative(GameTestHelper h){
        for(String id:new String[]{"mincer","tea_kettle","copper_tea_kettle"})
            h.assertTrue(Diegesis.stationType(StirAndCharm.id(id)).isEmpty(),"No Handily scene for "+id);
        h.succeed();
    }
    @GameTest(template="empty") public static void cookingDurationsAndCameraDirections(GameTestHelper h){
        h.assertTrue(CookingPotBlockEntity.getMaxCookingTime()==700 && RoasterBlockEntity.getMaxRoastingTime()==700,"45 seconds changed to 35 seconds in native timers");
        var board=Diegesis.stationType(StirAndCharm.BOARD).orElseThrow();
        h.assertTrue(Diegesis.stationType(StirAndCharm.id("mincer")).isEmpty(),"Mincer has no scene");
        var oven=Diegesis.stationType(StirAndCharm.id("stove")).orElseThrow();
        h.assertTrue(board.orientation()==StationType.Orientation.BLOCK,
            "Board stays aligned with the block; approach shots supply the camera side");
        h.assertTrue(oven.orientation()==StationType.Orientation.BLOCK,"Oven keeps a fixed front view");h.succeed();
    }
    @GameTest(template="empty") public static void candlelightOpenPotAndSoupWithoutStirring(GameTestHelper h){
        if(!net.neoforged.fml.ModList.get().isLoaded("candlelight")){h.succeed();return;}
        var c=setup(h,"candlelight:cooking_pot","candlelight_pot");var logic=new KitchenLogic(c,false);var p=h.makeMockPlayer(GameType.SURVIVAL);
        logic.onOpen(c,p);h.assertTrue(!c.blockState().hasProperty(RoasterLid.OPEN),"Candlelight pot has no lid mechanic");
        ((CookingPotBlockEntity)c.blockEntity()).setItem(0,new ItemStack(Items.WATER_BUCKET));logic.tick(c);
        h.assertTrue(c.state().getTank("broth").amount()>0 && c.state().getControl("spoon_x")==0,"Liquid is present before the first stirring input");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=260) public static void mixingBowlUsesNativeWhiskAndFinishes(GameTestHelper h){
        var c=setup(h,"farm_and_charm:crafting_bowl","crafting_bowl");var bowl=(CraftingBowlBlockEntity)c.blockEntity();
        var logic=new KitchenLogic(c,true);var p=h.makeMockPlayer(GameType.SURVIVAL);
        var holder=KitchenRecipes.all(c.level(),true).stream().filter(r -> r.value().getIngredients().stream().allMatch(i -> i.getItems().length>0)).findFirst().orElseThrow();
        for(var ingredient:holder.value().getIngredients())p.getInventory().add(ingredient.getItems()[0].copyWithCount(1));
        logic.onCustomAction(c,p,"fill",fill(holder));
        h.assertTrue(bowl.hasRecipe(),"The bowl keeps native recipe matching");
        for(int i=0;i<220;i++){bowl.markStirred(c.level());bowl.addWhiskImpulse(.25F);bowl.tick(c.level(),c.pos(),c.blockState(),bowl);}
        for(int i=0;i<10;i++)bowl.tick(c.level(),c.pos(),c.blockState(),bowl);
        h.assertTrue(bowl.isFinished(),"Native whisk motion finishes the bowl");
        var expected=KitchenRecipes.result(c.level(),holder.value());
        h.assertTrue(ItemStack.isSameItemSameComponents(bowl.getItem(4),expected),"The native bowl output and count are preserved");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=280) public static void stoveUsesNativeFuelAndCookTime(GameTestHelper h){
        var c=setup(h,"farm_and_charm:stove","stove");var stove=(StoveBlockEntity)c.blockEntity();
        var holder=Appliance.STOVE.recipes(c.level()).stream().filter(r -> r.value().getIngredients().stream().allMatch(i -> i.getItems().length>0)).findFirst().orElseThrow();
        int slot=1;for(var ingredient:holder.value().getIngredients())stove.setItem(slot++,ingredient.getItems()[0].copyWithCount(1));
        stove.setItem(4,new ItemStack(Items.COAL,8));
        h.assertTrue(stove.ignite(),"The stove accepts native fuel and ignites");
        for(int i=0;i<StoveBlockEntity.TOTAL_COOKING_TIME+2;i++)stove.tick(c.level(),c.pos(),c.blockState(),stove);
        h.assertTrue(!stove.getItem(0).isEmpty(),"The stove cooks through its native timer and fuel");
        h.succeed();
    }
    private static void roasterCooks(GameTestHelper h,String block,String station){
        var c=setup(h,block,station);var roaster=(RoasterBlockEntity)c.blockEntity();
        var holder=Appliance.ROASTER.recipes(c.level()).stream().filter(r -> r.value().getIngredients().stream().allMatch(i -> i.getItems().length>0)).findFirst().orElseThrow();
        int slot=0;for(var ingredient:holder.value().getIngredients())roaster.setItem(slot++,ingredient.getItems()[0].copyWithCount(1));
        if(holder.value() instanceof RoasterRecipe rr && !rr.getContainer().isEmpty())roaster.setItem(6,rr.getContainer().copyWithCount(1));
        c.level().setBlockAndUpdate(c.pos().below(),Blocks.REDSTONE_BLOCK.defaultBlockState());
        for(int i=0;i<RoasterBlockEntity.getMaxRoastingTime()+2;i++)roaster.tick(c.level(),c.pos(),c.blockState(),roaster);
        h.assertTrue(!roaster.getItem(7).isEmpty(),station+" roasts a native recipe into its native output");
    }
    @GameTest(template="empty",timeoutTicks=820) public static void roasterCooksNativeRecipe(GameTestHelper h){
        roasterCooks(h,"farm_and_charm:roaster","roaster");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=820) public static void candlelightPanCooksNativeRecipe(GameTestHelper h){
        if(!net.neoforged.fml.ModList.get().isLoaded("candlelight")){h.succeed();return;}
        roasterCooks(h,"candlelight:cooking_pan","candlelight_pan");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=260) public static void mincerConsumesInputThroughNativeTimer(GameTestHelper h){
        var pos=h.absolutePos(new net.minecraft.core.BlockPos(2,2,2));
        var level=h.getLevel();level.setBlockAndUpdate(pos,BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("farm_and_charm:mincer")).defaultBlockState());
        var mincer=(MincerBlockEntity)level.getBlockEntity(pos);
        var holder=Appliance.MINCER.recipes(level).stream().filter(r -> r.value() instanceof MincerRecipe m && m.getInput().getItems().length>0).findFirst().orElseThrow();
        mincer.setItem(0,((MincerRecipe)holder.value()).getInput().getItems()[0].copyWithCount(3));
        for(int n=0;n<24;n++){mincer.crank();for(int t=0;t<12;t++)mincer.tick(level,pos,level.getBlockState(pos),mincer);}
        h.assertTrue(mincer.getItem(0).getCount()<3,"A valid crank consumes native mincer input");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=260) public static void boardCutsFullSixteenBatch(GameTestHelper h){
        var c=setup(h,"farm_and_charm:cutting_board","cutting_board");var logic=new BoardLogic(c);
        var board=(CuttingBoardBlockEntity)c.blockEntity();var p=h.makeMockPlayer(GameType.SURVIVAL);
        var recipe=(CuttingBoardRecipe)BoardRecipes.all(c.level()).stream().filter(r -> r.value() instanceof CuttingBoardRecipe && !r.id().getPath().startsWith("stripping/")
            && ((CuttingBoardRecipe)r.value()).getIngredient().getItems().length>0).findFirst().orElseThrow().value();
        var tool=BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(s -> BoardTools.knife(s) && s.isDamageableItem()).findFirst().orElseThrow();
        p.inventoryMenu.setCarried(tool.copy());p.inventoryMenu.setCarried(recipe.getIngredient().getItems()[0].copyWithCount(16));
        logic.onCustomAction(c,p,"place",new CompoundTag());
        h.assertTrue(board.getPending().getCount()==16,"Sixteen items can be queued");
        int marks=Math.min(3,Math.max(1,recipe.getChops()));
        for(int i=0;i<marks;i++){int mark=i;h.runAtTickTime(1+i*5,()->{var tag=new CompoundTag();tag.putInt("mark",mark);tag.putFloat("at",8);p.inventoryMenu.setCarried(tool.copy());logic.onCustomAction(c,p,"chop",tag);});}
        h.runAtTickTime(marks*5+5,()->{
            h.assertTrue(board.getPending().isEmpty(),"The last mark cuts the whole sixteen-item queue");
            int expected=recipe.getResult().getCount()*16;
            int made=Arrays.stream(c.state().getItems("result")).filter(s -> ItemStack.isSameItemSameComponents(s,recipe.getResult())).mapToInt(ItemStack::getCount).sum();
            h.assertTrue(made==expected,"Every queued item yields its native result: wanted "+expected+", got "+made);
            h.succeed();
        });
    }
    @GameTest(template="empty") public static void bowlAndStoveInventoriesAreNative(GameTestHelper h){
        var c=setup(h,"farm_and_charm:crafting_bowl","crafting_bowl");var logic=new KitchenLogic(c,true);
        var p=h.makeMockPlayer(GameType.SURVIVAL);c.state().setItem(SlotRef.of("ingredients",0),new ItemStack(Items.WHEAT));
        h.assertTrue(((CraftingBowlBlockEntity)c.blockEntity()).getItem(0).is(Items.WHEAT),"Bowl slots stay the native inventory");
        var s=setup(h,"farm_and_charm:stove","stove");var stoveLogic=new ApplianceLogic(s,Appliance.STOVE);
        s.state().setItem(SlotRef.of("ingredients",0),new ItemStack(Items.POTATO));
        h.assertTrue(((StoveBlockEntity)s.blockEntity()).getItem(1).is(Items.POTATO),"Stove slots stay the native inventory");
        h.succeed();
    }
    @GameTest(template="empty") public static void candlelightOpenPotShapeHasNoLidCollision(GameTestHelper h){
        if(!net.neoforged.fml.ModList.get().isLoaded("candlelight")){h.succeed();return;}
        var c=setup(h,"candlelight:cooking_pot","candlelight_pot");
        var base=c.blockState();
        net.minecraft.world.level.block.state.properties.Property support=base.getBlock().getStateDefinition().getProperty("needs_support");
        for(var facing:Direction.Plane.HORIZONTAL){
            var state=base.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,facing);
            if(support!=null)state=state.setValue(support,true);
            var shape=state.getShape(c.level(),c.pos());
            h.assertTrue(!shape.isEmpty() && shape.max(net.minecraft.core.Direction.Axis.Y)<=.751D,
                "Open Candlelight pot shape keeps walls and handles but no lid collision from any facing/support state");
        }
        h.succeed();
    }
    private static CookingPotBlockEntity cookedPot(GameTestHelper h) {
        var c=setup(h,"farm_and_charm:cooking_pot","cooking_pot");var pot=(CookingPotBlockEntity)c.blockEntity();
        var holder=KitchenRecipes.all(c.level(),false).stream().filter(r -> r.value() instanceof CookingPotRecipe p && !p.requiresLearning()
            && p.isContainerRequired() && p.getIngredients().stream().allMatch(i -> !i.isEmpty() && i.getItems().length>0)).findFirst().orElseThrow();
        int slot=0;for(var ingredient:holder.value().getIngredients())pot.setItem(slot++,ingredient.getItems()[0].copyWithCount(1));
        c.level().setBlockAndUpdate(c.pos().below(),Blocks.REDSTONE_BLOCK.defaultBlockState());
        for(int i=0;i<=CookingPotBlockEntity.getMaxCookingTime();i++)pot.tick(c.level(),c.pos(),c.blockState(),pot);
        h.assertTrue(!pot.getItem(7).isEmpty(),"A pot with heat and ingredients cooks a real serving");
        h.assertTrue(!KitchenRecipes.dish(holder.value()).isEmpty(),"The tested recipe asks for a vessel");
        return pot;
    }
    @GameTest(template="empty",timeoutTicks=120) public static void potClickWithVesselServesOneOntoCursor(GameTestHelper h){
        var pot=cookedPot(h);var p=h.makeMockPlayer(GameType.SURVIVAL);
        var dish=((MealVessel)pot).handily$vessel();h.assertTrue(!dish.isEmpty(),"Cooking remembers the vessel");
        p.inventoryMenu.setCarried(dish.copyWithCount(1));
        int before=pot.getItem(7).getCount();
        int taken=MealServing.collect(p,pot,true,false);
        h.assertTrue(taken==1,"One portion is served");
        h.assertTrue(pot.getItem(7).getCount()==before-1,"The native output loses exactly one portion");
        var portion=p.inventoryMenu.getCarried();
        h.assertTrue(portion.getCount()==1 && !portion.isEmpty(),"The last vessel frees the cursor and the dish takes its place");
        h.assertTrue(pot.getItem(6).isEmpty(),"The cursor vessel paid for the portion, not the station stock");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void potFullInventorySpendsNothing(GameTestHelper h){
        var pot=cookedPot(h);var p=h.makeMockPlayer(GameType.SURVIVAL);
        var dish=((MealVessel)pot).handily$vessel();
        for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.BEDROCK,64));
        p.inventoryMenu.setCarried(dish.copyWithCount(2));
        int before=pot.getItem(7).getCount();
        int taken=MealServing.collect(p,pot,true,false);
        h.assertTrue(taken==0,"No room means no portion");
        h.assertTrue(pot.getItem(7).getCount()==before,"The native output is untouched");
        h.assertTrue(p.inventoryMenu.getCarried().getCount()==2,"The vessel is untouched");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void potShiftUsesInventoryAndStationVessels(GameTestHelper h){
        var pot=cookedPot(h);var p=h.makeMockPlayer(GameType.SURVIVAL);
        var dish=((MealVessel)pot).handily$vessel();
        p.getInventory().add(dish.copyWithCount(1));p.getInventory().add(dish.copyWithCount(1));
        int before=pot.getItem(7).getCount(),vessels=p.getInventory().countItem(dish.getItem());
        int taken=MealServing.collect(p,pot,false,true);
        h.assertTrue(taken>=1,"Shift takes what vessels and stock allow");
        h.assertTrue(pot.getItem(7).getCount()==before-taken,"The output matches the taken portions");
        h.assertTrue(p.getInventory().countItem(dish.getItem())==vessels-taken,"Only the used vessels left the inventory");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120) public static void potServeFromHandKeepsLastVesselSlot(GameTestHelper h){
        var pot=cookedPot(h);var p=h.makeMockPlayer(GameType.SURVIVAL);
        var dish=((MealVessel)pot).handily$vessel();
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,dish.copyWithCount(1));
        int before=pot.getItem(7).getCount();
        int taken=MealServing.serveFromHand(p,pot);
        h.assertTrue(taken==1 && pot.getItem(7).getCount()==before-1,"One hand-served portion leaves the pot");
        h.assertTrue(!p.getMainHandItem().isEmpty() && p.getMainHandItem().getCount()==1,
            "The emptied hand holds the dish");
        h.succeed();
    }
    @GameTest(template="empty") public static void brothAppearsOnSlotChangeWithoutStirring(GameTestHelper h){
        var c=setup(h,"farm_and_charm:cooking_pot","cooking_pot");var logic=new KitchenLogic(c,false);
        ((CookingPotBlockEntity)c.blockEntity()).setItem(0,new ItemStack(Items.WATER_BUCKET));
        logic.onSlotChanged(c,SlotRef.of("ingredients",0));
        h.assertTrue(c.state().getTank("broth").amount()>0,"The tank is published by the slot change itself");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200) public static void bakeryJamUsesNativeStirringAndBottles(GameTestHelper h){
        if(net.neoforged.fml.ModList.get().isLoaded("bakery"))BakeryChecks.run(h);else h.succeed();
    }
    // Reflection used by the GameTest registrar must not load optional classes on a core-only server.
    private static class BakeryChecks {
        static void run(GameTestHelper h){
        var c=setup(h,"bakery:small_cooking_pot","jam_pot");var logic=new JamLogic(c);var p=h.makeMockPlayer(GameType.SURVIVAL);
        var pot=(net.satisfy.bakery.core.block.entity.SmallCookingPotBlockEntity)c.blockEntity();
        var recipe=JamLogic.recipes(c.level()).stream().filter(r -> r.value().getIngredients().stream().allMatch(i -> i.getItems().length>0)).findFirst().orElseThrow();
        for(var ingredient:recipe.value().getIngredients())p.getInventory().add(ingredient.getItems()[0].copyWithCount(1));
        logic.onCustomAction(c,p,"fill",fill(recipe));
        h.assertTrue(pot.isCooking(),"Recipe book starts a native Bakery recipe");
        c.level().setBlockAndUpdate(c.pos().below(),Blocks.AIR.defaultBlockState());int start=pot.getTimer();
        net.satisfy.bakery.core.block.entity.SmallCookingPotBlockEntity.serverTick(c.level(),c.pos(),c.blockState(),pot);
        h.assertTrue(pot.getTimer()==start,"Jam cannot cook without heat");
        c.level().setBlockAndUpdate(c.pos().below(),Blocks.CAMPFIRE.defaultBlockState());
        for(int i=0;i<160;i++)net.satisfy.bakery.core.block.entity.SmallCookingPotBlockEntity.serverTick(c.level(),c.pos(),c.blockState(),pot);
        h.assertTrue(pot.isStirDue(),"The native jam requires stirring after 160 cooking ticks");
        logic.tick(c);h.assertTrue(pot.isStirDue(),"Opening the scene cannot stir automatically");
        var first=new CompoundTag();first.putFloat("x",2);first.putFloat("z",0);logic.onCustomAction(c,p,"stir",first);
        h.runAtTickTime(2,()->{
            var next=new CompoundTag();next.putFloat("x",1.8F);next.putFloat("z",.8F);logic.onCustomAction(c,p,"stir",next);
            h.assertTrue(!pot.isStirDue(),"Real validated mouse motion clears the native stirring requirement");
            int remaining=pot.getCookingTime()-pot.getTimer();
            for(int i=0;i<remaining;i++){net.satisfy.bakery.core.block.entity.SmallCookingPotBlockEntity.serverTick(c.level(),c.pos(),c.blockState(),pot);if(pot.isStirDue())pot.stir();}
            h.assertTrue(pot.canBottle(),"Native recipe reaches bottling stage");
            var jar=new ItemStack(net.satisfy.bakery.core.registry.ObjectRegistry.JAR.get());p.inventoryMenu.setCarried(jar);
            logic.onCustomAction(c,p,"place",new CompoundTag());
            h.assertTrue(p.inventoryMenu.getCarried().isEmpty(),"Bottling consumes one empty jar");
            h.assertTrue(p.getInventory().items.stream().anyMatch(s -> s.is(recipe.value().getResult().getItem())),"Native filled jam and its components are given to the player");h.succeed();
        });
        }
    }
    @GameTest(template="empty",timeoutTicks=40) public static void mincerEmptyInputStillCranks(GameTestHelper h){
        var pos=h.absolutePos(new net.minecraft.core.BlockPos(2,2,2));var level=h.getLevel();
        level.setBlockAndUpdate(pos,BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("farm_and_charm:mincer")).defaultBlockState());
        var mincer=(MincerBlockEntity)level.getBlockEntity(pos);
        mincer.crank();
        for(int i=0;i<5;i++)mincer.tick(level,pos,level.getBlockState(pos),mincer);
        h.assertTrue(mincer.getCrankSpeed()>0 && mincer.getInterpolatedCrankAngle(1)>0,"Native empty mincer handle rotates");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=920) public static void candlelightPotCooksNativelyAndRequiresHeat(GameTestHelper h){
        if(!net.neoforged.fml.ModList.get().isLoaded("candlelight")){h.succeed();return;}
        var c=setup(h,"candlelight:cooking_pot","candlelight_pot");var pot=(CookingPotBlockEntity)c.blockEntity();
        var holder=KitchenRecipes.all(c.level(),false).stream().filter(r -> r.value() instanceof CookingPotRecipe p && !p.requiresLearning()
            && p.isContainerRequired() && p.getIngredients().stream().allMatch(i -> !i.isEmpty() && i.getItems().length>0)).findFirst().orElseThrow();
        int slot=0;for(var ingredient:holder.value().getIngredients())pot.setItem(slot++,ingredient.getItems()[0].copyWithCount(1));
        c.level().setBlockAndUpdate(c.pos().below(),Blocks.AIR.defaultBlockState());
        for(int i=0;i<20;i++)pot.tick(c.level(),c.pos(),c.blockState(),pot);
        h.assertTrue(((ru.codex.stirandcharm.mixin.CookingPotAccess)pot).stirandcharm$cookingTime()==0,
            "Candlelight pot without native heat does not cook");
        c.level().setBlockAndUpdate(c.pos().below(),Blocks.REDSTONE_BLOCK.defaultBlockState());
        for(int i=0;i<CookingPotBlockEntity.getMaxCookingTime();i++)pot.tick(c.level(),c.pos(),c.blockState(),pot);
        h.assertTrue(!pot.getItem(7).isEmpty(),"Candlelight pot cooks through its native inventory and timer");
        var p=h.makeMockPlayer(GameType.SURVIVAL);var dish=((MealVessel)pot).handily$vessel();
        h.assertTrue(!dish.isEmpty(),"The native Candlelight recipe requires a serving vessel");
        p.inventoryMenu.setCarried(dish.copyWithCount(1));int before=pot.getItem(7).getCount();
        int taken=MealServing.collect(p,pot,true,false);
        h.assertTrue(taken==1 && pot.getItem(7).getCount()==before-1,"Candlelight serving takes one native portion");
        h.succeed();
    }
    @GameTest(template="empty") public static void potNativeTimingHeatAndServing(GameTestHelper h){
        var c=setup(h,"farm_and_charm:cooking_pot","cooking_pot");var pot=(CookingPotBlockEntity)c.blockEntity();
        var holder=KitchenRecipes.all(c.level(),false).stream().filter(r -> r.value() instanceof CookingPotRecipe p && !p.requiresLearning() && p.isContainerRequired()
            && p.getIngredients().stream().allMatch(i -> !i.isEmpty() && i.getItems().length>0)).findFirst().orElseThrow();
        int slot=0;for(var ingredient:holder.value().getIngredients())pot.setItem(slot++,ingredient.getItems()[0].copyWithCount(1));
        c.level().setBlockAndUpdate(c.pos().below(),Blocks.AIR.defaultBlockState());
        for(int i=0;i<20;i++)pot.tick(c.level(),c.pos(),c.blockState(),pot);
        h.assertTrue(((ru.codex.stirandcharm.mixin.CookingPotAccess)pot).stirandcharm$cookingTime()==0,"No heat means no cooking progress");
        c.level().setBlockAndUpdate(c.pos().below(),Blocks.REDSTONE_BLOCK.defaultBlockState());
        for(int i=0;i<699;i++)pot.tick(c.level(),c.pos(),c.blockState(),pot);
        h.assertTrue(pot.getItem(7).isEmpty(),"Cooking does not finish before 35 seconds");
        pot.tick(c.level(),c.pos(),c.blockState(),pot);h.assertTrue(!pot.getItem(7).isEmpty(),"Native recipe finishes at exactly 700 ticks without stirring or an empty bowl");
        h.assertTrue(pot.removeItem(7,1).isEmpty(),"A serving cannot leave without its required container");
        int ready=pot.getItem(7).getCount();pot.setItem(6,KitchenRecipes.dish(holder.value()).copyWithCount(1));
        var serving=pot.removeItem(7,1);h.assertTrue(serving.getCount()==1 && pot.getItem(6).isEmpty() && pot.getItem(7).getCount()==ready-1,"One serving consumes exactly one real container");
        var c2=setup(h,"farm_and_charm:cooking_pot","cooking_pot");var boosted=(CookingPotBlockEntity)c2.blockEntity();
        slot=0;for(var ingredient:holder.value().getIngredients())boosted.setItem(slot++,ingredient.getItems()[0].copyWithCount(1));
        ((PotStirring)boosted).handily$stirred(c2.level().getGameTime());int ticks=0;
        while(boosted.getItem(7).isEmpty() && ticks<700){boosted.tick(c2.level(),c2.pos(),c2.blockState(),boosted);ticks++;}
        h.assertTrue(ticks>=466 && ticks<=468,"Real stirring boost caps cooking at approximately 1.5x speed");h.succeed();
    }
}
