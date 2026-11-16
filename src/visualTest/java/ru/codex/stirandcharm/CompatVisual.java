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
    private static float crankAngleBefore=Float.NaN;
    private static float layerScaleBefore=Float.NaN;
    private static final String[] BLOCKS={"farm_and_charm:cutting_board","candlelight:cooking_pan","candlelight:cooking_pot","farm_and_charm:roaster","bakery:small_cooking_pot","farm_and_charm:cooking_pot"};
    private static final BlockPos[] POS={new BlockPos(0,70,0),new BlockPos(2,70,0),new BlockPos(-2,70,0),new BlockPos(4,70,0),new BlockPos(-4,70,0),new BlockPos(6,70,0)};
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,"handily-060-"+name+".png",mc.getMainRenderTarget(),msg -> LogUtils.getLogger().info("Compat screenshot: {}",msg.getString()));}
    private static void open(Minecraft mc,int i){
        if(DiegesisClient.isFocused())DiegesisClientRuntime.goOver(POS[i],.35F);
        else DiegesisClientRuntime.onUseBlock(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND,
            new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(POS[i]),Direction.UP,POS[i],false));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(!Boolean.getBoolean("handily.compatVisual"))return;
        var mc=Minecraft.getInstance();if(mc.level==null || mc.player==null || mc.getSingleplayerServer()==null)return;
        ticks++;
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
        if(ticks==1075)mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().connection.teleport(-1.2,70,.2,90,45));
        if(ticks==1090)shot(mc,"candlelight-world-soup");
        if(ticks==1095)mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();
            level.setBlockAndUpdate(POS[0],BuiltInRegistries.BLOCK.get(ResourceLocation.parse("farm_and_charm:cutting_board")).defaultBlockState());
            mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().connection.teleport(-2.2,70,.5,270,45);
        });
        if(ticks==1100)open(mc,0);
        if(ticks==1105){
            var camera=mc.gameRenderer.getMainCamera().getPosition();
            if(camera.x>=POS[0].getX()+.5)throw new IllegalStateException("Board west approach camera: "+camera);
            LogUtils.getLogger().info("HANDILY_CHECK board west camera side: PASS at {}",camera);
            com.pockyl.diegesis.impl.client.focus.FocusController.get().requestExit();
        }
        if(ticks==1110)mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().connection.teleport(.5,70,-2.2,0,45));
        if(ticks==1115)open(mc,0);
        if(ticks==1120){
            var camera=mc.gameRenderer.getMainCamera().getPosition();
            if(camera.z>=POS[0].getZ()+.5)throw new IllegalStateException("Board north approach camera: "+camera);
            LogUtils.getLogger().info("HANDILY_CHECK board north camera side: PASS at {}",camera);
            com.pockyl.diegesis.impl.client.focus.FocusController.get().requestExit();
        }
        if(ticks==1125)mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().connection.teleport(.5,70,2.2,180,45));
        if(ticks==1132)open(mc,0);
        if(ticks==1137){
            var camera=mc.gameRenderer.getMainCamera().getPosition();
            if(camera.z<=POS[0].getZ()+.5)throw new IllegalStateException("Board south approach camera: "+camera);
            LogUtils.getLogger().info("HANDILY_CHECK board south camera side: PASS at {}",camera);
            com.pockyl.diegesis.impl.client.focus.FocusController.get().requestExit();
        }
        if(ticks==1142)mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().connection.teleport(2.2,70,.5,90,45));
        if(ticks==1149)open(mc,0);
        if(ticks==1154){
            var camera=mc.gameRenderer.getMainCamera().getPosition();
            if(camera.x<=POS[0].getX()+.5)throw new IllegalStateException("Board east approach camera: "+camera);
            LogUtils.getLogger().info("HANDILY_CHECK board east camera side: PASS at {}",camera);
            mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var board=(CuttingBoardBlockEntity)level.getBlockEntity(POS[0]);
                board.startCut(ItemStack.EMPTY);board.setContents(ItemStack.EMPTY);board.setKnife(ItemStack.EMPTY);
                ((BoardSelection)board).handily$recipe(null);p.getInventory().clearContent();
                var holder=BoardRecipes.all(level).stream().filter(r -> r.value() instanceof net.satisfy.farm_and_charm.core.recipe.CuttingBoardAssemblyRecipe a
                    && a.getIngredients().stream().allMatch(i -> i.getItems().length>0)).findFirst().orElseThrow();
                for(var ingredient:holder.value().getIngredients())p.getInventory().add(ingredient.getItems()[0].copyWithCount(1));
                p.inventoryMenu.broadcastChanges();
            });
        }
        if(ticks==1157)DiegesisClient.station(POS[0]).ifPresent(view -> {
            var holder=BoardRecipes.all(view.level()).stream().filter(r -> r.value() instanceof net.satisfy.farm_and_charm.core.recipe.CuttingBoardAssemblyRecipe a
                && a.getIngredients().stream().allMatch(i -> i.getItems().length>0)).findFirst().orElseThrow();
            var tag=new CompoundTag();tag.putString("recipe",holder.id().toString());view.sendAction("fill",tag);
        });
        if(ticks==1168)com.pockyl.diegesis.impl.client.focus.FocusController.get().requestExit();
        if(ticks==1180)open(mc,0);
        if(ticks==1188){
            var layer=DiegesisClient.station(POS[0]).orElseThrow().node("layer_0").orElseThrow();
            float ratio=scale(layer)/layerScaleBefore;
            if(Math.abs(ratio-1F)>.02F)throw new IllegalStateException("Interrupted board assembly left a shrunken layer: "+ratio);
            LogUtils.getLogger().info("HANDILY_CHECK board interrupted assembly transform restored: PASS ratio={}",ratio);
        }
        if(ticks==1194)mc.stop();
    }
    private static float scale(NodeView node){
        var matrix=node.sceneMatrix();
        float x=matrix.transformPosition(new Vector3f(1,0,0)).length();
        float y=matrix.transformPosition(new Vector3f(0,1,0)).length();
        float z=matrix.transformPosition(new Vector3f(0,0,1)).length();
        return (x+y+z)/3F;
    }
}
