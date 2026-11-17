package ru.codex.stirandcharm.client;

import com.mojang.logging.LogUtils;
import com.pockyl.diegesis.api.client.*;
import com.pockyl.diegesis.impl.client.DiegesisClientRuntime;
import com.pockyl.diegesis.impl.client.focus.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.satisfy.farm_and_charm.core.block.StoveBlock;
import net.satisfy.farm_and_charm.core.block.entity.StoveBlockEntity;
import org.lwjgl.glfw.GLFW;
import ru.codex.stirandcharm.*;

/** Real screen clicks, in a hidden window; never moves the OS cursor or activates a window. */
@EventBusSubscriber(modid=StirAndCharm.ID,value=Dist.CLIENT)
public final class StoveVisual {
    private static final BlockPos POS=new BlockPos(0,70,0);
    private static final Direction[] FACINGS={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
    private static int stage,age,total,turn;
    private static volatile boolean ready;
    private static String recipe;
    private static ItemStack meal=ItemStack.EMPTY;
    private static void next(){stage++;age=0;}
    private static void check(boolean ok,String why){if(!ok)throw new IllegalStateException(why+" (stage="+stage+", facing="+FACINGS[turn]+")");}
    private static void pass(String why){LogUtils.getLogger().info("HANDILY_065 PASS {}: {}",FACINGS[turn],why);}
    private static void point(Minecraft mc,StationView v,float x,float y,float z){var p=Projector.toScreen(v.toWorld(x,y,z));check(p!=null && mc.screen!=null,"Missing click target");FocusScreen.forcedMouse=new double[]{p.x,p.y};mc.screen.mouseMoved(p.x,p.y);}
    private static void click(Minecraft mc){var p=FocusScreen.forcedMouse;mc.screen.mouseClicked(p[0],p[1],0);mc.screen.mouseReleased(p[0],p[1],0);}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,"handily-065-"+FACINGS[turn].getName()+"-"+name+".png",mc.getMainRenderTarget(),m->LogUtils.getLogger().info(m.getString()));}
    private static int shown(Minecraft mc){int n=0;for(int i=0;i<36;i++){var s=ClientDelivery.visible(mc.player.getInventory(),i,false);if(ItemStack.isSameItemSameComponents(s,meal))n+=s.getCount();}return n;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("handily.stoveVisual"))return;
        var mc=Minecraft.getInstance();if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null)return;
        mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);age++;total++;
        check(total<2600 && age<500,"Stove visual check timed out");
        if(stage==0){
            if(mc.getOverlay()!=null)return;
            check(GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_FALSE && !mc.mouseHandler.isMouseGrabbed(),"Background test took focus or mouse");
            ready=false;mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var level=server.overworld();var p=server.getPlayerList().getPlayers().getFirst();
                for(int x=-5;x<6;x++)for(int z=-5;z<6;z++){
                    level.setBlockAndUpdate(new BlockPos(x,69,z),Blocks.OAK_PLANKS.defaultBlockState());
                    for(int y=70;y<74;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                }
                level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(-8,65,-8,8,80,8)).forEach(net.minecraft.world.entity.Entity::discard);
                level.setDayTime(1000);p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.getInventory().clearContent();p.inventoryMenu.setCarried(ItemStack.EMPTY);p.getInventory().selected=8;
                var facing=FACINGS[turn];level.setBlockAndUpdate(POS,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("farm_and_charm:stove")).defaultBlockState().setValue(StoveBlock.FACING,facing));
                var r=Appliance.STOVE.recipes(level).stream().filter(h->h.value().getIngredients().size()<=3 && h.value().getIngredients().stream().allMatch(i->i.getItems().length>0)).findFirst().orElseThrow();recipe=r.id().toString();
                int slot=0;for(var ingredient:r.value().getIngredients())p.getInventory().setItem(slot++,ingredient.getItems()[0].copyWithCount(1));
                p.getInventory().setItem(3,new ItemStack(Items.COAL,4));p.getInventory().setItem(4,new ItemStack(Items.FLINT_AND_STEEL));p.inventoryMenu.broadcastChanges();
                p.connection.teleport(.5+facing.getStepX()*2.8,70,.5+facing.getStepZ()*2.8,facing.toYRot()+180,10);ready=true;
            });next();return;
        }
        if(stage==1){if(!ready || age<20)return;DiegesisClientRuntime.onUseBlock(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(POS),FACINGS[turn],POS,false));next();return;}
        var v=DiegesisClient.station(POS).orElse(null);
        if(stage==2){if(v==null || !v.isFocused() || age<25)return;check(v.node("mouth_door").orElseThrow().isVisible(),"No animated doors");v.takeFromInventory(0);next();return;}
        if(stage==3){if(mc.player.inventoryMenu.getCarried().isEmpty() || age<15)return;point(mc,v,10.5F,11,0);click(mc);next();return;}
        if(stage==4){if(java.util.Arrays.stream(v.state().getItems("ingredients")).allMatch(ItemStack::isEmpty))return;pass("actual click places an ingredient inside the chamber");v.takeFromInventory(3);next();return;}
        if(stage==5){if(!mc.player.inventoryMenu.getCarried().is(Items.COAL) || age<12)return;point(mc,v,8,3,0);click(mc);next();return;}
        if(stage==6){if(v.state().getItems("fuel")[0].isEmpty())return;pass("actual click puts coal in the firebox");v.takeFromInventory(4);next();return;}
        if(stage==7){if(!mc.player.inventoryMenu.getCarried().is(Items.FLINT_AND_STEEL) || age<12)return;point(mc,v,8,3,0);click(mc);next();return;}
        if(stage==8){if(v.state().getControl("heated")<.5F)return;pass("flint and steel ignites native stove");v.putAwayCarried();var data=new CompoundTag();data.putString("recipe",recipe);v.sendAction("fill",data);next();return;}
        if(stage==9){if(v.state().getControl("progress")<.05F)return;check(v.node("progress").orElseThrow().isVisible(),"Progress strip missing");shot(mc,"cooking");pass("native cooking progress visible over grate");next();return;}
        if(stage==10){if(v.state().getItems("result")[0].isEmpty())return;meal=v.state().getItems("result")[0].copyWithCount(1);point(mc,v,5.6F,10.4F,2.8F);if(age<15)return;shot(mc,"ready");check(v.node("result").orElseThrow().isHovered(),"Ready food cannot be clicked");click(mc);next();return;}
        if(stage==11){if(mc.player.getInventory().countItem(meal.getItem())==0)return;check(shown(mc)==0,"Dish appeared before pickup motion");pass("ready food click delivers to inventory after presentation");next();return;}
        if(stage==12){if(shown(mc)==0)return;shot(mc,"received");FocusController.get().requestExit();FocusScreen.forcedMouse=null;next();return;}
        if(stage==13){if(DiegesisClient.isFocused())return;if(age<4){shot(mc,"closing");return;}if(age<25)return;pass("closed grille world model restored");if(turn<3){turn++;stage=0;age=0;return;}pass("ALL stove checks complete, four block facings");mc.stop();}
    }
}
