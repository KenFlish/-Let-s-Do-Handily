package ru.codex.stirandcharm.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pockyl.diegesis.api.client.DiegesisClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.satisfy.farm_and_charm.core.block.entity.CookingPotBlockEntity;
import net.satisfy.farm_and_charm.core.registry.EntityTypeRegistry;
import ru.codex.stirandcharm.*;

/** Uses native block-entity inventory packets, so food remains visible after leaving the scene or reconnecting. */
@EventBusSubscriber(modid = StirAndCharm.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class WorldIngredients {
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        if (net.neoforged.fml.ModList.get().isLoaded("candlelight")) {registerPan(event);registerCandlelightPot(event);}
        event.registerBlockEntityRenderer(EntityTypeRegistry.COOKING_POT_BLOCK_ENTITY.get(), ctx -> new BlockEntityRenderer<CookingPotBlockEntity>() {
            public void render(CookingPotBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
                if (DiegesisClient.station(be.getBlockPos()).map(s -> s.isInUse()).orElse(false)) return;
                if(!Settings.POT.get())return;
                WorldSoup.render(be,pose,buffers,light,7.3F);
                for (int i = 0; i < 6; i++) {
                    ItemStack stack = be.getItem(i);
                    if (KitchenIngredients.liquid(stack) || KitchenIngredients.dissolved(stack) || KitchenIngredients.emptyContainer(stack)) continue;
                    float x = .32F + (i % 3) * .18F, z = i < 3 ? .35F : .65F;
                    item(be, stack, pose, buffers, light, overlay, x, 7.55F/16F, z, .19F, i * 31);
                }
            }
        });
        event.registerBlockEntityRenderer(EntityTypeRegistry.ROASTER_BLOCK_ENTITY.get(), ctx -> new BlockEntityRenderer<net.satisfy.farm_and_charm.core.block.entity.RoasterBlockEntity>() {
            public void render(net.satisfy.farm_and_charm.core.block.entity.RoasterBlockEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
                if (!be.getBlockState().getValue(RoasterLid.OPEN) || DiegesisClient.station(be.getBlockPos()).map(s -> s.isInUse()).orElse(false)) return;
                pose.pushPose(); pose.translate(.5,0,.5);
                pose.mulPose(Axis.YP.rotationDegrees(180-be.getBlockState().getValue(net.satisfy.farm_and_charm.core.block.RoasterBlock.FACING).toYRot()));
                pose.translate(-.5,0,-.5);
                float height=be.getBlockState().getValue(net.satisfy.farm_and_charm.core.block.RoasterBlock.HANGING) ? 6.5F : 4.5F;
                for (int i=0;i<6;i++) TrayFood.world(be.getItem(i),i,height-4.3F,pose,buffers,light);
                pose.popPose();
            }
        });
    }
    @SuppressWarnings("unchecked")
    private static void registerPan(EntityRenderersEvent.RegisterRenderers event) {
        var type=(net.minecraft.world.level.block.entity.BlockEntityType<net.satisfy.farm_and_charm.core.block.entity.RoasterBlockEntity>)(Object)
            net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.parse("candlelight:cooking_pan"));
        event.registerBlockEntityRenderer(type,ctx -> (be,partial,pose,buffers,light,overlay) -> {
            if (!Settings.CANDLELIGHT.get() || DiegesisClient.station(be.getBlockPos()).map(s -> s.isInUse()).orElse(false)) return;
            pose.pushPose();pose.translate(.5,0,.5);
            pose.mulPose(Axis.YP.rotationDegrees(180-be.getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING).toYRot()));pose.translate(-.5,0,-.5);
            for(int i=0;i<6;i++)TrayFood.world(be.getItem(i),i,1.1F,pose,buffers,light);
            pose.popPose();
        });
    }
    @SuppressWarnings("unchecked")
    private static void registerCandlelightPot(EntityRenderersEvent.RegisterRenderers event){
        var type=(net.minecraft.world.level.block.entity.BlockEntityType<CookingPotBlockEntity>)(Object)
            net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.parse("candlelight:large_cooking_pot"));
        event.registerBlockEntityRenderer(type,ctx -> (be,partial,pose,buffers,light,overlay) -> {
            if(!Settings.CANDLELIGHT.get() || !Settings.POT.get() || DiegesisClient.station(be.getBlockPos()).map(s -> s.isInUse()).orElse(false))return;
            var state=be.getBlockState();
            pose.pushPose();pose.translate(.5,0,.5);
            pose.mulPose(Axis.YP.rotationDegrees(180-state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING).toYRot()));pose.translate(-.5,0,-.5);
            {
                WorldSoup.render(be,pose,buffers,light,8.8F);
                for(int i=0;i<6;i++){
                    var stack=be.getItem(i);if(KitchenIngredients.liquid(stack) || KitchenIngredients.dissolved(stack) || KitchenIngredients.emptyContainer(stack))continue;
                    item(be,stack,pose,buffers,light,overlay,.32F+i%3*.18F,9.05F/16F,i<3?.35F:.65F,.19F,i*31);
                }
            }
            pose.translate(0,3F/16F,0);
            PotModels.draw(pose,buffers,light,PotModels.farmPot(),PotModels::spoon,false);
            pose.popPose();
        });
    }
    public static void item(BlockEntity be, ItemStack stack, PoseStack pose, MultiBufferSource buffers,
                            int light, int overlay, float x, float y, float z, float size, float turn) {
        if (stack.isEmpty()) return;
        pose.pushPose(); pose.translate(x,y,z);
        pose.mulPose(Axis.YP.rotationDegrees(turn)); pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.scale(size,size,size);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, be.getLevel(), 0);
        pose.popPose();
    }
}
