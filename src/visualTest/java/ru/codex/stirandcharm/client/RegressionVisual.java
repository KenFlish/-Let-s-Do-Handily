package ru.codex.stirandcharm.client;

import com.mojang.logging.LogUtils;
import com.pockyl.diegesis.api.client.*;
import com.pockyl.diegesis.impl.client.DiegesisClientRuntime;
import com.pockyl.diegesis.impl.client.focus.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.satisfy.farm_and_charm.core.block.entity.*;
import net.satisfy.farm_and_charm.core.recipe.*;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import ru.codex.stirandcharm.*;

/** Targeted release checks, with server acknowledgements and actual screen clicks. */
@EventBusSubscriber(modid=StirAndCharm.ID,value=Dist.CLIENT)
public final class RegressionVisual {
    private static final BlockPos BOARD=new BlockPos(0,70,0),POT=new BlockPos(3,70,0),ROASTER=new BlockPos(6,70,0);
    private static int stage,age,total,fixture;
    private static volatile boolean ready;
    private static ItemStack cutResult=ItemStack.EMPTY,meal=ItemStack.EMPTY;
    private static long clicked;
    private static void next(){stage++;age=0;}
    private static void check(boolean value,String reason){if(!value)throw new IllegalStateException(reason);}
    private static void pass(String reason){LogUtils.getLogger().info("HANDILY_064 PASS: {}",reason);}
    private static void open(Minecraft mc,BlockPos pos){DiegesisClientRuntime.onUseBlock(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false));}
    private static void point(Minecraft mc,StationView view,float x,float y,float z){var p=Projector.toScreen(view.toWorld(x,y,z));check(p!=null && mc.screen!=null,"Missing mouse target");FocusScreen.forcedMouse=new double[]{p.x,p.y};mc.screen.mouseMoved(p.x,p.y);}
    private static void click(Minecraft mc){var p=FocusScreen.forcedMouse;mc.screen.mouseClicked(p[0],p[1],0);mc.screen.mouseReleased(p[0],p[1],0);}
    private static int shown(Minecraft mc,ItemStack item,boolean gains){int n=0;for(int i=0;i<36;i++){var s=ClientDelivery.visible(mc.player.getInventory(),i,gains);if(ItemStack.isSameItemSameComponents(s,item))n+=s.getCount();}return n;}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,"handily-064-"+name+".png",mc.getMainRenderTarget(),m->LogUtils.getLogger().info(m.getString()));}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!Boolean.getBoolean("handily.regressionVisual"))return;
        var mc=Minecraft.getInstance();if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null)return;
        mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(5);age++;total++;
        check(total<2200,"Regression driver timed out at stage "+stage);
        if(stage==0){
            if(mc.getOverlay()!=null)return;
            check(GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_FALSE,"Test window took keyboard focus");
            check(!mc.mouseHandler.isMouseGrabbed(),"Test window captured the mouse");pass("background window has no keyboard focus or mouse capture");
            ready=false;mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var level=server.overworld();var p=server.getPlayerList().getPlayers().getFirst();
                for(int x=-3;x<10;x++)for(int z=-4;z<5;z++)level.setBlockAndUpdate(new BlockPos(x,69,z),Blocks.OAK_PLANKS.defaultBlockState());
                level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(-10,65,-10,15,80,15)).forEach(net.minecraft.world.entity.Entity::discard);
                p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.getInventory().clearContent();p.inventoryMenu.setCarried(ItemStack.EMPTY);p.getInventory().selected=8;p.connection.teleport(.5,70,-2.2,0,45);
                level.setBlockAndUpdate(BOARD,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(BOARD,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("farm_and_charm:cutting_board")).defaultBlockState());
                var recipe=BoardRecipes.all(level).stream().filter(h->h.value() instanceof CuttingBoardRecipe r && java.util.Arrays.stream(r.getIngredient().getItems()).anyMatch(s->BoardAssembly.fits(level,java.util.List.of(s.copyWithCount(1))))).findFirst().orElseThrow();
                var raw=java.util.Arrays.stream(((CuttingBoardRecipe)recipe.value()).getIngredient().getItems()).filter(s->BoardAssembly.fits(level,java.util.List.of(s.copyWithCount(1)))).findFirst().orElseThrow();cutResult=((CuttingBoardRecipe)recipe.value()).getResult().copy();
                ((BoardSelection)level.getBlockEntity(BOARD)).handily$recipe(BoardAssembly.all(level).getFirst().holder().id());
                p.getInventory().setItem(0,raw.copyWithCount(3));p.getInventory().setItem(1,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("farm_and_charm:iron_cleaver"))));p.inventoryMenu.broadcastChanges();ready=true;
            });next();return;
        }
        if(stage==1){if(!ready || age<15)return;open(mc,BOARD);next();return;}
        if(stage==2){var v=DiegesisClient.station(BOARD);if(v.isEmpty() || !v.get().isFocused() || age<20)return;v.get().takeFromInventory(0);next();return;}
        if(stage==3){if(mc.player.inventoryMenu.getCarried().getCount()!=3)return;point(mc,DiegesisClient.station(BOARD).orElseThrow(),6,1.1F,8);next();return;}
        if(stage==4){var v=DiegesisClient.station(BOARD).orElseThrow();point(mc,v,6,1.1F,8);if(age<20)return;LogUtils.getLogger().info("REGRESSION_PLACE carried={} surface={} focused={} cursor={}",mc.player.inventoryMenu.getCarried(),v.node("surface").orElseThrow().isHovered(),v.isFocused(),v.cursorOn(1.1F));shot(mc,"placement-target");click(mc);next();return;}
        if(stage==5){
            var view=DiegesisClient.station(BOARD).orElseThrow();if(view.state().getItems("pending")[0].isEmpty()){check(age<80,"Placement failed: carried="+mc.player.inventoryMenu.getCarried()+" layers="+java.util.Arrays.toString(view.state().getItems("ingredients"))+" focused="+view.isFocused());return;}
            check(java.util.Arrays.stream(view.state().getItems("ingredients")).allMatch(ItemStack::isEmpty),"First cuttable ingredient became a layer");
            if(!view.node("result").orElseThrow().isVisible()){check(age<60,"Dish/progress badge missing after state and frame synchronization");return;}
            pass("actual placement keeps dual-purpose food under the knife; dish/progress badge visible");shot(mc,"board-progress");
            view.takeFromInventory(1);next();return;
        }
        if(stage==6){if(!BoardTools.knife(mc.player.inventoryMenu.getCarried()))return;next();return;}
        if(stage==7){
            var view=DiegesisClient.station(BOARD).orElseThrow();var food=view.state().getItems("pending")[0];
            if(food.isEmpty()){
                if(mc.player.getInventory().countItem(cutResult.getItem())==0)return;
                check(shown(mc,cutResult,false)==0 && shown(mc,cutResult,true)==0,"Cut result appeared in inventory before scene animation");pass("cut results hidden until the cutting receipt finishes");shot(mc,"cut-receipt");next();return;
            }
            var method=BoardBehavior.class.getDeclaredMethod("mark",StationView.class,ItemStack.class,int.class);method.setAccessible(true);var mark=(ItemVoxels.Cut)method.invoke(null,view,food,(int)view.state().getControl("chops"));
            var at=view.node("food").orElseThrow().sceneMatrix().transformPosition(new Vector3f(mark.x(),.05F,mark.z()));point(mc,view,at.x,1.1F,at.z);
            if(age%8==0 && System.nanoTime()-clicked>250_000_000L){click(mc);clicked=System.nanoTime();}return;
        }
        if(stage==8){if(shown(mc,cutResult,false)==0)return;pass("cut result appears after scene and inventory flight");FocusController.get().requestExit();FocusScreen.forcedMouse=null;next();return;}
        if(stage==9){
            if(DiegesisClient.isFocused() || age<20)return;ready=false;
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var level=server.overworld();var p=server.getPlayerList().getPlayers().getFirst();var pos=fixture==0?POT:ROASTER;
                level.setBlockAndUpdate(pos.below(),Blocks.REDSTONE_BLOCK.defaultBlockState());level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(pos,BuiltInRegistries.BLOCK.get(ResourceLocation.parse(fixture==0?"farm_and_charm:cooking_pot":"farm_and_charm:roaster")).defaultBlockState());
                ItemStack vessel;
                if(fixture==0){var pot=(CookingPotBlockEntity)level.getBlockEntity(pos);var recipe=KitchenRecipes.all(level,false).stream().filter(h->h.value() instanceof CookingPotRecipe q && !q.requiresLearning() && q.isContainerRequired() && q.getIngredients().stream().allMatch(i->i.getItems().length>0)).findFirst().orElseThrow();int i=0;for(var ingredient:recipe.value().getIngredients())pot.setItem(i++,ingredient.getItems()[0].copyWithCount(1));for(i=0;i<701;i++)pot.tick(level,pos,pot.getBlockState(),pot);meal=pot.getItem(7).copyWithCount(1);vessel=((MealVessel)pot).handily$vessel().copyWithCount(1);}
                else{var pot=(RoasterBlockEntity)level.getBlockEntity(pos);var recipe=Appliance.ROASTER.recipes(level).stream().filter(h->h.value() instanceof RoasterRecipe r && !r.getContainer().isEmpty() && r.getIngredients().stream().allMatch(i->i.getItems().length>0)).findFirst().orElseThrow();int i=0;for(var ingredient:recipe.value().getIngredients())pot.setItem(i++,ingredient.getItems()[0].copyWithCount(1));for(i=0;i<RoasterBlockEntity.getMaxRoastingTime()+2;i++)pot.tick(level,pos,pot.getBlockState(),pot);level.setBlockAndUpdate(pos,pot.getBlockState().setValue(RoasterLid.OPEN,true));meal=pot.getItem(7).copyWithCount(1);vessel=((MealVessel)pot).handily$vessel().copyWithCount(1);}
                check(!meal.isEmpty() && !vessel.isEmpty(),"Native serving fixture did not cook");p.getInventory().clearContent();p.inventoryMenu.setCarried(ItemStack.EMPTY);p.getInventory().selected=8;p.getInventory().setItem(0,vessel);p.inventoryMenu.broadcastChanges();p.connection.teleport(pos.getX()+.5,70,-2.2,0,45);ready=true;
            });next();return;
        }
        if(stage==10){if(!ready || age<15)return;open(mc,fixture==0?POT:ROASTER);next();return;}
        if(stage==11){
            var view=DiegesisClient.station(fixture==0?POT:ROASTER).orElse(null);if(view==null || !view.isFocused() || age<20)return;
            float y=fixture==0?PotMotion.surface(view)+.4F:4.26F;point(mc,view,8,y,8);
            if(!view.node("serve_area").orElseThrow().isHovered() || !view.node("offer_vessel").orElseThrow().isVisible())return;
            check(view.node("result").orElseThrow().isVisible(),"Cooking badge absent");click(mc);next();return;
        }
        if(stage==12){
            if(mc.player.getInventory().countItem(meal.getItem())==0)return;
            check(shown(mc,meal,false)==0 && shown(mc,meal,true)==0,"Served dish appeared in inventory before scoop animation");pass((fixture==0?"pot":"roaster")+": last vessel serves with actual mouse click; inventory reveal waits for scoop");shot(mc,fixture==0?"pot-scoop":"roaster-scoop");next();return;
        }
        if(stage==13){
            if(shown(mc,meal,false)==0)return;pass((fixture==0?"pot":"roaster")+": inventory reveal follows serving motion and flight");
            FocusController.get().requestExit();FocusScreen.forcedMouse=null;
            if(fixture++==0){stage=9;age=0;return;}pass("ALL targeted regressions complete");mc.stop();
        }
    }
}
