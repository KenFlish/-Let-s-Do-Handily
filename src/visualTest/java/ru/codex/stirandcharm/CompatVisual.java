package ru.codex.stirandcharm;

import com.mojang.logging.LogUtils;
import com.pockyl.diegesis.api.client.DiegesisClient;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.satisfy.farm_and_charm.core.block.CuttingBoardBlock;
import net.satisfy.farm_and_charm.core.block.entity.*;
import net.satisfy.farm_and_charm.core.recipe.CuttingBoardRecipe;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid=StirAndCharm.ID,value=Dist.CLIENT)
public final class CompatVisual {
    private static int ticks;
    private static final String[] BLOCKS={"farm_and_charm:cutting_board","candlelight:cooking_pan","candlelight:cooking_pot"};
    private static final BlockPos[] POS={new BlockPos(0,70,0),new BlockPos(2,70,0),new BlockPos(-2,70,0)};
    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if(!Boolean.getBoolean("handily.compatVisual")) return;
        var mc=Minecraft.getInstance();if(mc.level==null || mc.player==null || mc.getSingleplayerServer()==null)return;
        ticks++;
        if(ticks==25) {
            mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(5);
            mc.getSingleplayerServer().execute(()->{
                var level=mc.getSingleplayerServer().overworld();level.setDayTime(6000);
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();p.teleportTo(.5,70,-2.2);
                p.getInventory().clearContent();
                for(int i=0;i<POS.length;i++) {
                    level.setBlockAndUpdate(POS[i].below(),Blocks.REDSTONE_BLOCK.defaultBlockState());
                    level.setBlockAndUpdate(POS[i],Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(POS[i],BuiltInRegistries.BLOCK.get(ResourceLocation.parse(BLOCKS[i])).defaultBlockState());
                }
                var board=(CuttingBoardBlockEntity)level.getBlockEntity(POS[0]);
                board.setKnife(BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(s -> s.is(CuttingBoardBlock.KNIVES)).findFirst().orElseThrow());
                var cut=BoardRecipes.all(level).stream().filter(h -> h.value() instanceof CuttingBoardRecipe).findFirst().orElseThrow().value();
                board.startCut(cut.getIngredients().getFirst().getItems()[0].copyWithCount(1));
                var pot=(CookingPotBlockEntity)level.getBlockEntity(POS[2]);
                pot.setItem(0,new ItemStack(Items.WATER_BUCKET));pot.setItem(1,new ItemStack(Items.CARROT));
                var pan=(RoasterBlockEntity)level.getBlockEntity(POS[1]);pan.setItem(0,new ItemStack(Items.BEEF));pan.setItem(1,new ItemStack(Items.POTATO));
                for(var entity:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(-12,68,-8,12,78,8))) entity.discard();
            });
        }
        for(int i=0;i<POS.length;i++) {
            if(ticks==90+i*100) {
                if(i==0) mc.setScreen(null);
                com.pockyl.diegesis.impl.client.DiegesisClientRuntime.goOver(POS[i],.35F);
                if(i==0) com.pockyl.diegesis.impl.client.DiegesisClientRuntime.onUseBlock(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(POS[i]),Direction.UP,POS[i],false));
            }
            if(ticks==145+i*100) {
                if(!DiegesisClient.isFocused()) throw new IllegalStateException("Compat station failed to open: "+BLOCKS[i]);
                Screenshot.grab(mc.gameDirectory,"handily-040-"+i+".png",mc.getMainRenderTarget(),msg -> LogUtils.getLogger().info("Compat screenshot: {}",msg.getString()));
            }
        }
        if(ticks==365) mc.stop();
    }
}
