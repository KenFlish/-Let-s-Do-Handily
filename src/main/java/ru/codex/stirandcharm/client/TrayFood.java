package ru.codex.stirandcharm.client;

import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pockyl.diegesis.api.client.StationView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.item.ItemStack;

/** Irregular, slightly overlapping layers of food rather than an inventory grid. */
final class TrayFood {
    static final float[][] AT={{5.2F,1.1F,6.2F,-24},{9.8F,1.2F,8.4F,19},{6.9F,1.5F,10.1F,66},
        {10.1F,1.8F,5.8F,-48},{4.9F,2F,9.9F,32},{8F,2.25F,7.6F,-12}};
    private record Built(com.pockyl.diegesis.api.client.MeshShape shape, net.minecraft.resources.ResourceLocation texture){}
    private record Key(net.minecraft.world.item.Item item, int components){}
    private static final Map<Key,Built> CACHE=new HashMap<>();
    private static Object model;
    static void frame(StationView view){
        var items=view.state().getItems("ingredients");
        for(int i=0;i<items.length;i++){
            var stack=items[i];
            view.node("ingredient_"+i).ifPresent(n -> {
                var built=build(stack);
                if(built!=null)n.setShape(built.shape(),built.texture());
                n.setVisible(!stack.isEmpty());
            });
        }
    }
    static Built build(ItemStack item){
        if(item.isEmpty())return null;
        // Resource reload changes the sprites; retain only a small cache of the current icons.
        var current=Minecraft.getInstance().getModelManager().getMissingModel();
        if(model!=current || CACHE.size()>256){CACHE.clear();model=current;}
        return CACHE.computeIfAbsent(new Key(item.getItem(),item.getComponents().hashCode()),key ->
            ItemVoxels.icon(item).map(icon -> new Built(ItemVoxels.build(icon,4.5F,List.of(),0),icon.sprite())).orElse(null));
    }
    static void world(ItemStack item,int index,float floor,PoseStack pose,MultiBufferSource buffers,int light){
        var built=build(item);if(built==null)return;var at=AT[index];
        pose.pushPose();pose.translate(at[0]/16F,(floor+at[1])/16F,at[2]/16F);pose.scale(1F/16,1F/16,1F/16);
        pose.mulPose(Axis.YP.rotationDegrees(at[3]));
        var sprite=Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(built.texture());
        built.shape().mesh().draw(pose.last(),buffers.getBuffer(RenderType.cutout()),sprite,light);
        pose.popPose();
    }
    private TrayFood(){}
}
