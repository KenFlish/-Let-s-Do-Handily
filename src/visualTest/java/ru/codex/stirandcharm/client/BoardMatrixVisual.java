package ru.codex.stirandcharm.client;

import com.mojang.logging.LogUtils;
import com.pockyl.diegesis.api.client.*;
import com.pockyl.diegesis.impl.client.*;
import com.pockyl.diegesis.impl.client.focus.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.satisfy.farm_and_charm.core.block.entity.CuttingBoardBlockEntity;
import net.satisfy.farm_and_charm.core.recipe.CuttingBoardRecipe;
import org.joml.Vector3f;
import ru.codex.stirandcharm.*;

/** Release-only driver. Real screen clicks, projection and carried-tool rendering, all 32 combinations. */
@EventBusSubscriber(modid=StirAndCharm.ID,value=Dist.CLIENT)
public final class BoardMatrixVisual {
    private static final BlockPos POS=new BlockPos(0,70,0);
    private static final Direction[] FACINGS={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
    private static int ticks;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception {
        if(!Boolean.getBoolean("handily.boardMatrix"))return;
        var mc=Minecraft.getInstance();if(mc.level==null || mc.player==null || mc.getSingleplayerServer()==null)return;
        mc.options.pauseOnLostFocus=false;int t=ticks++,run=t/150,phase=t%150;
        int total=net.neoforged.fml.ModList.get().isLoaded("farmersdelight")?32:16;
        if(run>=total){FocusScreen.forcedMouse=null;LogUtils.getLogger().info("HANDILY_063_MATRIX PASS: {} actual cutting cases, reopen after first cut",total);mc.stop();return;}
        if(phase==0){
            FocusScreen.forcedMouse=null;FocusController.get().requestExit();
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var level=server.overworld();var player=server.getPlayerList().getPlayers().getFirst();
                var facing=FACINGS[run/4%4];var side=FACINGS[run%4];
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.getInventory().clearContent();player.inventoryMenu.setCarried(ItemStack.EMPTY);player.getInventory().selected=8;
                player.connection.teleport(.5+side.getStepX()*2.2,70,.5+side.getStepZ()*2.2,side.toYRot()+180,45);
                level.setBlockAndUpdate(POS.below(),Blocks.OAK_PLANKS.defaultBlockState());level.setBlockAndUpdate(POS,Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(POS,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("farm_and_charm:cutting_board")).defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,facing));
                var board=(CuttingBoardBlockEntity)level.getBlockEntity(POS);
                var recipe=(CuttingBoardRecipe)BoardRecipes.all(level).stream().filter(h->h.value() instanceof CuttingBoardRecipe && h.id().getPath().contains("tomato")).findFirst().orElseThrow().value();
                board.startCut(recipe.getIngredient().getItems()[0].copyWithCount(1));
                player.getInventory().setItem(1,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(run<16?"farm_and_charm:iron_cleaver":"farmersdelight:iron_knife"))));player.inventoryMenu.broadcastChanges();
            });
        }
        if(phase==20 || phase==60){mc.setScreen(null);DiegesisClientRuntime.onUseBlock(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(POS),Direction.UP,POS,false));}
        if(phase==32)DiegesisClient.station(POS).orElseThrow().takeFromInventory(1);
        if(phase==50){FocusController.get().requestExit();FocusScreen.forcedMouse=null;}
        if((phase>=40&&phase<=45)||(phase>=70&&phase<=75)||(phase>=85&&phase<=90)||(phase>=105&&phase<=110)||(phase>=120&&phase<=125)){
            var view=DiegesisClient.station(POS).orElseThrow();var food=view.state().getItems("pending")[0];
            if(!food.isEmpty()){
                var method=BoardBehavior.class.getDeclaredMethod("mark",StationView.class,ItemStack.class,int.class);method.setAccessible(true);
                var line=(ItemVoxels.Cut)method.invoke(null,view,food,(int)view.state().getControl("chops"));
                var at=view.node("food").orElseThrow().sceneMatrix().transformPosition(new Vector3f(line.x(),.05F,line.z()));
                // Project onto the same physical cutting plane used by the actual cursor ray.
                at.y=1.1F;var screen=Projector.toScreen(view.toWorld(at.x,at.y,at.z));
                if(screen==null || mc.screen==null)throw new IllegalStateException("Missing screen for case "+run);
                FocusScreen.forcedMouse=new double[]{screen.x,screen.y};mc.screen.mouseMoved(screen.x,screen.y);
                if(phase==45 || phase==75 || phase==90 || phase==110 || phase==125){LogUtils.getLogger().info("MATRIX_CLICK run={} phase={} mark={} required={} aim={} cursor={} carried={}",run,phase,view.state().getControl("chops"),view.state().getControl("required"),at,view.cursorOn(1.1F),mc.player.inventoryMenu.getCarried());mc.screen.mouseClicked(screen.x,screen.y,0);mc.screen.mouseReleased(screen.x,screen.y,0);}
                if(phase==44 && FocusInput.get().carried()!=null)throw new IllegalStateException("Knife and flat cursor are both drawn in case "+run);
            }
        }
        if(phase==140){
            var view=DiegesisClient.station(POS).orElseThrow();
            if(!view.state().getItems("pending")[0].isEmpty())throw new IllegalStateException("Cutting stalled: case="+run+" facing="+FACINGS[run/4%4]+" approach="+FACINGS[run%4]+" marks="+view.state().getControl("chops"));
            var recipe=(CuttingBoardRecipe)BoardRecipes.all(mc.level).stream().filter(h->h.value() instanceof CuttingBoardRecipe && h.id().getPath().contains("tomato")).findFirst().orElseThrow().value();
            if(mc.player.getInventory().countItem(recipe.getResult().getItem())!=recipe.getResult().getCount())throw new IllegalStateException("Wrong native yield in case "+run);
            LogUtils.getLogger().info("HANDILY_063_MATRIX case {} PASS: facing={} approach={} tool={}",run,FACINGS[run/4%4],FACINGS[run%4],run<16?"cleaver":"knife");
        }
    }
}
