package ru.codex.stirandcharm;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.impl.client.DiegesisClientRuntime;
import com.pockyl.diegesis.impl.client.focus.Projector;
import com.pockyl.diegesis.api.client.NodeView;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.satisfy.farm_and_charm.core.block.entity.*;
import net.satisfy.farm_and_charm.core.recipe.CuttingBoardRecipe;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

@EventBusSubscriber(modid=StirAndCharm.ID,value=Dist.CLIENT)
public final class CompatVisual {
    private static int ticks;
    private static int recoveryWait;
    private static Vector3f spoonBefore,foodBefore;
    private static float crankAngleBefore=Float.NaN;
    private static float layerScaleBefore=Float.NaN;
    private static final String[] BLOCKS={"farm_and_charm:cutting_board","candlelight:cooking_pan","candlelight:cooking_pot","farm_and_charm:roaster","bakery:small_cooking_pot","farm_and_charm:cooking_pot"};
    private static final BlockPos[] POS={new BlockPos(0,70,0),new BlockPos(2,70,0),new BlockPos(-2,70,0),new BlockPos(4,70,0),new BlockPos(-4,70,0),new BlockPos(6,70,0)};
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,"handily-063-"+name+".png",mc.getMainRenderTarget(),msg -> LogUtils.getLogger().info("Compat screenshot: {}",msg.getString()));}
    private static void open(Minecraft mc,int i){
        if(DiegesisClient.isFocused())DiegesisClientRuntime.goOver(POS[i],.35F);
        else DiegesisClientRuntime.onUseBlock(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND,
            new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(POS[i]),Direction.UP,POS[i],false));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(!Boolean.getBoolean("handily.compatVisual"))return;
        if(Boolean.getBoolean("handily.boardMatrix"))return;
        var mc=Minecraft.getInstance();if(mc.level==null || mc.player==null || mc.getSingleplayerServer()==null)return;
        ticks++;
        if(ticks==70 && Boolean.getBoolean("handily.potVisualOnly"))ticks=1234;
        if(ticks==25){
            mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(5);
            mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();level.setDayTime(6000);
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();p.connection.teleport(.5,70,-2.2,0,45);
                p.getInventory().clearContent();p.getInventory().selected=8;
                for(int i=0;i<POS.length;i++){
                    level.setBlockAndUpdate(POS[i].below(),BuiltInRegistries.BLOCK.get(ResourceLocation.parse("farm_and_charm:stove")).defaultBlockState());
                    level.setBlockAndUpdate(POS[i],Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(POS[i],BuiltInRegistries.BLOCK.get(ResourceLocation.parse(BLOCKS[i])).defaultBlockState());
                }
                var cut=(CuttingBoardRecipe)BoardRecipes.all(level).stream().filter(h -> h.value() instanceof CuttingBoardRecipe && h.id().getPath().contains("tomato")).findFirst().orElseThrow().value();
                p.getInventory().setItem(0,cut.getIngredient().getItems()[0].copyWithCount(3));
                p.getInventory().setItem(1,BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(BoardTools::knife).findFirst().orElseThrow());
                var pan=(RoasterBlockEntity)level.getBlockEntity(POS[1]);
                var roaster=(RoasterBlockEntity)level.getBlockEntity(POS[3]);
                for(var tray:new RoasterBlockEntity[]{pan,roaster}){tray.setItem(0,new ItemStack(Items.BEEF));tray.setItem(1,new ItemStack(Items.POTATO));tray.setItem(2,new ItemStack(Items.CARROT));}
                level.setBlockAndUpdate(POS[3],level.getBlockState(POS[3]).setValue(RoasterLid.OPEN,true));
                var pot=(CookingPotBlockEntity)level.getBlockEntity(POS[5]);pot.setItem(0,new ItemStack(Items.WATER_BUCKET));pot.setItem(1,new ItemStack(Items.CARROT));
                var jam=(net.satisfy.bakery.core.block.entity.SmallCookingPotBlockEntity)level.getBlockEntity(POS[4]);
                for(var ingredient:JamLogic.recipes(level).getFirst().value().getIngredients())jam.addIngredient(ingredient.getItems()[0].copyWithCount(1),p);
                for(var entity:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(-12,68,-8,12,78,8)))entity.discard();
                p.inventoryMenu.broadcastChanges();
            });
        }
        if(ticks==90){mc.setScreen(null);open(mc,0);}
        if(ticks==115)DiegesisClient.station(POS[0]).orElseThrow().takeFromInventory(0);
        if(ticks==130){
            var view=DiegesisClient.station(POS[0]).orElseThrow();var at=Projector.toScreen(view.toWorld(6,4.9F,8));
            if(at==null || mc.screen==null)throw new IllegalStateException("No board click target");
            mc.screen.mouseMoved(at.x,at.y);mc.screen.mouseClicked(at.x,at.y,0);mc.screen.mouseReleased(at.x,at.y,0);
        }
        if(ticks==155){
            var view=DiegesisClient.station(POS[0]).orElseThrow();
            if(view.state().getItems("pending")[0].getCount()!=3 || !mc.player.inventoryMenu.getCarried().isEmpty())throw new IllegalStateException("Actual board cursor click failed");
            LogUtils.getLogger().info("HANDILY_CHECK actual board cursor placement: PASS");shot(mc,"board");
        }
        if(ticks==190)open(mc,1);
        if(ticks==245)shot(mc,"pan");
        if(ticks==290)open(mc,2);
        if(ticks==345)shot(mc,"candlelight-empty-floor");
        if(ticks==390)open(mc,3);
        if(ticks==445)shot(mc,"roaster-plating");
        if(ticks==460)com.pockyl.diegesis.impl.client.focus.FocusController.get().requestExit();
        if(ticks==490)mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().connection.teleport(4.5,70,-1.5,0,50));
        if(ticks==525){if(DiegesisClient.isFocused())throw new IllegalStateException("World view remained focused");shot(mc,"roaster-world");}
        if(ticks==540)mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().connection.teleport(6.5,70,-1.5,0,50));
        if(ticks==575)shot(mc,"soup-without-stirring-world");
        if(ticks==590)mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().connection.teleport(-1.2,70,.2,90,45));
        if(ticks==625)open(mc,4);
        if(ticks==680)shot(mc,"bakery-jam");
        if(ticks==700)com.pockyl.diegesis.impl.client.focus.FocusController.get().requestExit();
        if(ticks==735)mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().connection.teleport(2.2,70,.5,90,45));
        if(ticks==770)open(mc,0);
        if(ticks==800)mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            p.inventoryMenu.setCarried(new ItemStack(Items.BEETROOT,8));
            p.inventoryMenu.broadcastChanges();
        });
        if(ticks==820)shot(mc,"carried-east");
        if(ticks==825){
            var view=DiegesisClient.station(POS[0]).orElseThrow();
            var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            if(camera.x < POS[0].getX()+.5)throw new IllegalStateException("Board did not open from the east side: "+camera);
            LogUtils.getLogger().info("HANDILY_CHECK board east camera side: PASS at {}",camera);shot(mc,"board-east");
        }
        if(ticks==830)mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            var board=(CuttingBoardBlockEntity)level.getBlockEntity(POS[0]);
            board.startCut(ItemStack.EMPTY);board.setContents(ItemStack.EMPTY);board.setKnife(ItemStack.EMPTY);
            ((BoardSelection)board).handily$recipe(null);
            p.inventoryMenu.setCarried(ItemStack.EMPTY);p.getInventory().clearContent();
            var holder=BoardRecipes.all(level).stream().filter(r -> r.value() instanceof net.satisfy.farm_and_charm.core.recipe.CuttingBoardAssemblyRecipe a
                && a.getIngredients().stream().allMatch(i -> i.getItems().length>0)).findFirst().orElseThrow();
            for(var ingredient:holder.value().getIngredients())p.getInventory().add(ingredient.getItems()[0].copyWithCount(1));
            p.inventoryMenu.broadcastChanges();
        });
        if(ticks==840)DiegesisClient.station(POS[0]).ifPresent(view -> {
            var holder=BoardRecipes.all(view.level()).stream().filter(r -> r.value() instanceof net.satisfy.farm_and_charm.core.recipe.CuttingBoardAssemblyRecipe a
                && a.getIngredients().stream().allMatch(i -> i.getItems().length>0)).findFirst().orElseThrow();
            var tag=new CompoundTag();tag.putString("recipe",holder.id().toString());
            view.sendAction("fill",tag);
        });
        if(ticks==835){
            var view=DiegesisClient.station(POS[0]).orElseThrow();
            layerScaleBefore=scale(view.node("layer_0").orElseThrow());
        }
        if(ticks==875)shot(mc,"assembly");
        if(ticks==888){
            var view=DiegesisClient.station(POS[0]).orElseThrow();
            var layer=view.node("layer_0").orElseThrow();
            float scale=scale(layer);
            if(Math.abs(scale/layerScaleBefore-1F)>.02F)throw new IllegalStateException("Board layer kept a shrunken transform after assembly: "+scale+" vs "+layerScaleBefore);
            if(layer.isVisible())throw new IllegalStateException("Finished board layers stayed visible");
            LogUtils.getLogger().info("HANDILY_CHECK board layer transform restored after assembly: PASS scale ratio={}",scale/layerScaleBefore);
        }
        if(ticks==890)com.pockyl.diegesis.impl.client.focus.FocusController.get().requestExit();
        if(ticks==895)mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();
            mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().connection.teleport(-1.5,70,-1.2,0,55);
        });
        if(ticks==910)shot(mc,"candlelight-world-floor");
        if(ticks==930)mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();
            level.setBlockAndUpdate(POS[0],BuiltInRegistries.BLOCK.get(ResourceLocation.parse("farm_and_charm:mincer")).defaultBlockState());
        });
        if(ticks==955)open(mc,0);
        if(ticks==980)mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();var mincer=(MincerBlockEntity)level.getBlockEntity(POS[0]);
            mincer.setItem(0,new ItemStack(Items.BEEF,4));
        });
        if(ticks==990)mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();((MincerBlockEntity)level.getBlockEntity(POS[0])).crank();
        });
        if(ticks==1000){
            if(DiegesisClient.isFocused())throw new IllegalStateException("Removed mincer scene still opened");
            shot(mc,"mincer-native");
            var be=(MincerBlockEntity)mc.level.getBlockEntity(POS[0]);
            crankAngleBefore=be.getInterpolatedCrankAngle(0);
        }
        if(ticks==1008){
            var be=(MincerBlockEntity)mc.level.getBlockEntity(POS[0]);
            float after=be.getInterpolatedCrankAngle(0);
            if(Math.abs(after-crankAngleBefore)<1e-4F)throw new IllegalStateException("Native mincer crank did not turn");
            LogUtils.getLogger().info("HANDILY_CHECK native mincer without scene, crank angle moved: PASS {} -> {}",crankAngleBefore,after);
            shot(mc,"mincer-crank");
        }
        if(ticks==1010)com.pockyl.diegesis.impl.client.focus.FocusController.get().requestExit();
        if(ticks==1015)mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();var pot=(CookingPotBlockEntity)level.getBlockEntity(POS[2]);
            pot.setItem(0,new ItemStack(Items.WATER_BUCKET));pot.setItem(1,new ItemStack(Items.CARROT));
        });
        if(ticks==1018)mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().connection.teleport(-2.5,70,-1.5,0,50));
        if(ticks==1022)open(mc,2);
        if(ticks==1055)shot(mc,"candlelight-pot");
        if(ticks==1060)com.pockyl.diegesis.impl.client.focus.FocusController.get().requestExit();
        // Camera combinations live in BoardMatrixVisual; leave enough time for network close/open here.
        if(ticks==1075)mc.getSingleplayerServer().execute(()->{
            var server=mc.getSingleplayerServer();var level=server.overworld();var p=server.getPlayerList().getPlayers().getFirst();
            level.setBlockAndUpdate(POS[0],Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(POS[0],BuiltInRegistries.BLOCK.get(ResourceLocation.parse("farm_and_charm:cutting_board")).defaultBlockState());
            p.connection.teleport(.5,70,-2.2,0,45);p.getInventory().clearContent();p.inventoryMenu.setCarried(ItemStack.EMPTY);
            var holder=BoardRecipes.all(level).stream().filter(r->r.value() instanceof net.satisfy.farm_and_charm.core.recipe.CuttingBoardAssemblyRecipe a && a.getIngredients().stream().allMatch(i->i.getItems().length>0)).findFirst().orElseThrow();
            for(var ingredient:holder.value().getIngredients())p.getInventory().add(ingredient.getItems()[0].copyWithCount(1));p.inventoryMenu.broadcastChanges();
        });
        if(ticks==1100)open(mc,0);
        if(ticks==1130){
            var view=DiegesisClient.station(POS[0]).orElseThrow();
            var holder=BoardRecipes.all(view.level()).stream().filter(r->r.value() instanceof net.satisfy.farm_and_charm.core.recipe.CuttingBoardAssemblyRecipe a && a.getIngredients().stream().allMatch(i->i.getItems().length>0)).findFirst().orElseThrow();
            var tag=new CompoundTag();tag.putString("recipe",holder.id().toString());view.sendAction("fill",tag);
        }
        if(ticks==1140)com.pockyl.diegesis.impl.client.focus.FocusController.get().requestExit();
        if(ticks==1180)open(mc,0);
        if(ticks==1210){
            var layer=DiegesisClient.station(POS[0]).orElseThrow().node("layer_0").orElseThrow();float ratio=scale(layer)/layerScaleBefore;
            if(Math.abs(ratio-1)>.02F || layer.isVisible())throw new IllegalStateException("Interrupted board assembly left a shrunken or visible layer: "+ratio);
            LogUtils.getLogger().info("HANDILY_CHECK board interrupted assembly transform restored: PASS ratio={}",ratio);
        }
        if(ticks==1234)com.pockyl.diegesis.impl.client.focus.FocusController.get().requestExit();
        if(ticks==1240)mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();
            var pot=(CookingPotBlockEntity)level.getBlockEntity(POS[5]);pot.clearContent();
            level.setBlockAndUpdate(POS[5].below(),Blocks.STONE.defaultBlockState());
            pot.setItem(0,new ItemStack(Items.SUGAR,32));pot.setChanged();
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();p.inventoryMenu.setCarried(ItemStack.EMPTY);p.connection.teleport(6.5,70,-2,0,45);
        });
        if(ticks==1250)open(mc,5);
        if(ticks>=1270 && ticks<1280){var view=DiegesisClient.station(POS[5]).orElseThrow();var at=Projector.toScreen(view.toWorld(8,6.5F,8));com.pockyl.diegesis.impl.client.focus.FocusScreen.forcedMouse=new double[]{at.x,at.y};mc.screen.mouseMoved(at.x,at.y);}
        if(ticks==1280){
            var view=DiegesisClient.station(POS[5]).orElseThrow();var sugar=view.node("ingredient_0").orElseThrow();
            if(sugar.isVisible())throw new IllegalStateException("Dissolved sugar must use the take-back preview");
            shot(mc,"062-sugar-visible");
            var pos=new Vector3f(8,6.5F,8);
            var at=Projector.toScreen(view.toWorld(pos.x,pos.y,pos.z));
            com.pockyl.diegesis.impl.client.focus.FocusScreen.forcedMouse=new double[]{at.x,at.y};mc.screen.mouseMoved(at.x,at.y);mc.screen.mouseClicked(at.x,at.y,1);mc.screen.mouseReleased(at.x,at.y,1);
        }
        if(ticks==1295){
            var view=DiegesisClient.station(POS[5]).orElseThrow();
            if(mc.player.getInventory().countItem(Items.SUGAR)!=32
                || !view.state().getItems("ingredients")[0].isEmpty())throw new IllegalStateException("Actual sugar click did not retrieve the full stack");
            LogUtils.getLogger().info("HANDILY_CHECK dissolved sugar recovered to inventory with real mouse click: PASS");
            mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();var pot=(CookingPotBlockEntity)level.getBlockEntity(POS[5]);
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                pot.setItem(0,new ItemStack(Items.SUGAR,32));p.getInventory().clearContent();p.inventoryMenu.setCarried(ItemStack.EMPTY);p.inventoryMenu.broadcastChanges();
                pot.setItem(1,new ItemStack(Items.CARROT));pot.setItem(2,new ItemStack(Items.POTATO));pot.setItem(3,new ItemStack(Items.BEEF));pot.setChanged();
            });
        }
        if(ticks==1310){
            var view=DiegesisClient.station(POS[5]).orElseThrow();var spoon=view.node("spoon").orElseThrow();
            spoonBefore=spoon.sceneMatrix().transformDirection(new Vector3f(0,1,0));
            var pos=view.node("stir_handle").orElseThrow().sceneMatrix().transformPosition(new Vector3f(-5.5F,10.25F,1));
            var at=Projector.toScreen(view.toWorld(pos.x,pos.y,pos.z));com.pockyl.diegesis.impl.client.focus.FocusScreen.forcedMouse=new double[]{at.x,at.y};mc.screen.mouseMoved(at.x,at.y);mc.screen.mouseClicked(at.x,at.y,0);
        }
        if(ticks>1310 && ticks<1390){
            var view=DiegesisClient.station(POS[5]).orElseThrow();float angle=(ticks-1310)*.16F;
            var at=Projector.toScreen(view.toWorld(8+(float)Math.cos(angle)*2,7.25F,8+(float)Math.sin(angle)*2));
            com.pockyl.diegesis.impl.client.focus.FocusScreen.forcedMouse=new double[]{at.x,at.y};
            mc.screen.mouseMoved(at.x,at.y);
        }
        if(ticks==1370){
            var view=DiegesisClient.station(POS[5]).orElseThrow();
            var now=view.node("spoon").orElseThrow().sceneMatrix().transformDirection(new Vector3f(0,1,0));
            if(now.distance(spoonBefore)<.2F || view.state().getControl("stirring")<.5F)throw new IllegalStateException("Mouse stirring did not tilt the native spoon: "+now+" state="+view.state().getControl("stirring")+" x="+view.state().getControl("spoon_x")+" z="+view.state().getControl("spoon_z"));
            foodBefore=view.node("ingredient_1").orElseThrow().sceneMatrix().transformPosition(new Vector3f());
            shot(mc,"062-stirring");LogUtils.getLogger().info("HANDILY_CHECK native spoon follows mouse and tilts: PASS");
        }
        if(ticks==1390){mc.screen.mouseReleased(0,0,0);com.pockyl.diegesis.impl.client.focus.FocusScreen.forcedMouse=null;}
        if(ticks==1405){
            var view=DiegesisClient.station(POS[5]).orElseThrow();var after=view.node("ingredient_1").orElseThrow().sceneMatrix().transformPosition(new Vector3f());
            if(after.distance(foodBefore)<.05F)throw new IllegalStateException("Ingredients did not drift with the soup");
            for(int i=1;i<4;i++){var pos=view.node("ingredient_"+i).orElseThrow().sceneMatrix().transformPosition(new Vector3f());
                if(Math.abs(pos.x-8)>3.1F || Math.abs(pos.z-8)>3.1F)throw new IllegalStateException("Floating ingredient left the pot: "+pos);}
            shot(mc,"062-floating");LogUtils.getLogger().info("HANDILY_CHECK ingredients drift inside pot bounds: PASS");
        }
        if(ticks==1420)mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();var pot=(CookingPotBlockEntity)level.getBlockEntity(POS[5]);pot.clearContent();pot.setItem(5,new ItemStack(Items.WATER_BUCKET));pot.setChanged();
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();p.getInventory().clearContent();p.inventoryMenu.setCarried(ItemStack.EMPTY);p.inventoryMenu.broadcastChanges();
        });
        if(ticks==1435){
            var view=DiegesisClient.station(POS[5]).orElseThrow();
            if(!view.node("hand_5").orElseThrow().isVisible() || view.node("ingredient_5").orElseThrow().isVisible())throw new IllegalStateException("Poured ingredient has no single animated hand");
            shot(mc,"063-pouring");LogUtils.getLogger().info("HANDILY_CHECK native container pouring motion visible: PASS");
        }
        if(ticks>=1450 && ticks<=1460){
            var view=DiegesisClient.station(POS[5]).orElseThrow();
            var point=view.node("contents").orElseThrow().sceneMatrix().transformPosition(new Vector3f(8,7.25F,8));
            var at=Projector.toScreen(view.toWorld(point.x,point.y,point.z));
            com.pockyl.diegesis.impl.client.focus.FocusScreen.forcedMouse=new double[]{at.x,at.y};mc.screen.mouseMoved(at.x,at.y);
        }
        if(ticks==1460){
            var view=DiegesisClient.station(POS[5]).orElseThrow();
            if(!view.node("contents").orElseThrow().isEnabled() || !view.node("contents").orElseThrow().isHovered()){
                if(++recoveryWait>200)throw new IllegalStateException("Poured ingredient recovery never became available after animation");
                ticks--;return;
            }
            var point=view.node("contents").orElseThrow().sceneMatrix().transformPosition(new Vector3f(8,7.25F,8));
            var at=Projector.toScreen(view.toWorld(point.x,point.y,point.z));
            mc.screen.mouseMoved(at.x,at.y);mc.screen.mouseClicked(at.x,at.y,1);mc.screen.mouseReleased(at.x,at.y,1);
        }
        if(ticks==1475){
            var view=DiegesisClient.station(POS[5]).orElseThrow();
            if(!view.state().getItems("ingredients")[5].isEmpty() || !mc.player.getInventory().contains(new ItemStack(Items.WATER_BUCKET)))throw new IllegalStateException("Right click on soup did not recover the poured ingredient");
            LogUtils.getLogger().info("HANDILY_CHECK poured ingredient recovered with real right click: PASS");
        }
        if(ticks==1480)mc.getSingleplayerServer().execute(()->{
            var server=mc.getSingleplayerServer();var level=server.overworld();var p=server.getPlayerList().getPlayers().getFirst();
            var pot=(CookingPotBlockEntity)level.getBlockEntity(POS[5]);pot.clearContent();level.setBlockAndUpdate(POS[5].below(),Blocks.REDSTONE_BLOCK.defaultBlockState());
            var holder=KitchenRecipes.all(level,false).stream().filter(r->r.value() instanceof net.satisfy.farm_and_charm.core.recipe.CookingPotRecipe q && !q.requiresLearning() && q.isContainerRequired() && q.getIngredients().stream().allMatch(i->i.getItems().length>0)).findFirst().orElseThrow();
            int slot=0;for(var ingredient:holder.value().getIngredients())pot.setItem(slot++,ingredient.getItems()[0].copyWithCount(1));
            for(int i=0;i<701;i++)pot.tick(level,POS[5],pot.getBlockState(),pot);
            p.inventoryMenu.setCarried(ItemStack.EMPTY);p.getInventory().clearContent();p.getInventory().setItem(0,((MealVessel)pot).handily$vessel().copyWithCount(1));p.inventoryMenu.broadcastChanges();
        });
        if(ticks>=1510 && ticks<1525){
            var view=DiegesisClient.station(POS[5]).orElseThrow();var at=Projector.toScreen(view.toWorld(8,7.6F,8));
            com.pockyl.diegesis.impl.client.focus.FocusScreen.forcedMouse=new double[]{at.x,at.y};mc.screen.mouseMoved(at.x,at.y);
            if(ticks==1520){
                if(!view.node("offer_vessel").orElseThrow().isVisible())throw new IllegalStateException("Inventory vessel offer is absent");
                shot(mc,"063-vessel-offer");mc.screen.mouseClicked(at.x,at.y,0);mc.screen.mouseReleased(at.x,at.y,0);
            }
        }
        if(ticks==1545){
            var view=DiegesisClient.station(POS[5]).orElseThrow();
            if(!mc.player.inventoryMenu.getCarried().isEmpty() || !view.state().data().contains("vessel"))throw new IllegalStateException("Serving changed the cursor");
            var vessel=BuiltInRegistries.ITEM.get(ResourceLocation.parse(view.state().data().getString("vessel")));
            if(mc.player.getInventory().countItem(vessel)!=0 || mc.player.getInventory().items.stream().noneMatch(s->!s.isEmpty()))throw new IllegalStateException("The offered last vessel did not serve into inventory");
            LogUtils.getLogger().info("HANDILY_CHECK offered last inventory vessel serves with real click: PASS");
            com.pockyl.diegesis.impl.client.focus.FocusScreen.forcedMouse=null;com.pockyl.diegesis.impl.client.focus.FocusController.get().requestExit();
        }
        if(ticks==1555)mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().connection.teleport(-3.5,70,-2,0,45));
        if(ticks==1580)open(mc,4);
        if(ticks>=1610 && ticks<=1620){
            var view=DiegesisClient.station(POS[4]).orElseThrow();var at=Projector.toScreen(view.toWorld(0,4,8));
            com.pockyl.diegesis.impl.client.focus.FocusScreen.forcedMouse=new double[]{at.x,at.y};mc.screen.mouseMoved(at.x,at.y);
            if(ticks==1620){
                if(view.node("dump").isPresent() || !view.node("dump_handle").orElseThrow().isHovered())throw new IllegalStateException("Bakery handle is not the hovered dump control");
                shot(mc,"063-bakery-handle");mc.screen.mouseClicked(at.x,at.y,0);mc.screen.mouseReleased(at.x,at.y,0);
            }
        }
        if(ticks==1640){
            var pot=(net.satisfy.bakery.core.block.entity.SmallCookingPotBlockEntity)mc.level.getBlockEntity(POS[4]);
            if(!pot.isEmpty())throw new IllegalStateException("Real Bakery handle click did not empty the pot");
            LogUtils.getLogger().info("HANDILY_CHECK Bakery real handle hover and dump click: PASS");com.pockyl.diegesis.impl.client.focus.FocusScreen.forcedMouse=null;mc.stop();
        }
    }
    private static float scale(NodeView node){
        var matrix=node.sceneMatrix();
        float x=matrix.transformDirection(new Vector3f(1,0,0)).length();
        float y=matrix.transformDirection(new Vector3f(0,1,0)).length();
        float z=matrix.transformDirection(new Vector3f(0,0,1)).length();
        return (x+y+z)/3F;
    }
}
