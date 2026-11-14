package ru.codex.stirandcharm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.satisfy.farm_and_charm.core.block.entity.CookingPotBlockEntity;
import ru.codex.stirandcharm.*;

/** Reads the native inventory; neither scene activation nor stirring is needed to show the soup. */
final class WorldSoup {
    static void render(CookingPotBlockEntity pot,PoseStack pose,MultiBufferSource buffers,int light,float height){
        int count=0,r=0,g=0,b=0;boolean any=!pot.getItem(7).isEmpty();
        for(int i=0;i<6;i++){
            var stack=pot.getItem(i);
            if(stack.isEmpty() || KitchenIngredients.emptyContainer(stack))continue;
            any=true;if(KitchenIngredients.water(stack))continue;
            int rgb=KitchenIngredients.liquid(stack)?KitchenIngredients.liquidColor(stack):FoodColors.main(stack);
            r+=rgb>>16&255;g+=rgb>>8&255;b+=rgb&255;count++;
        }
        if(!any)return;
        int rgb=count==0?0x77B9D8:FoodColors.lighter((r/count<<16)|(g/count<<8)|b/count,.14F);
        if(!pot.getItem(7).isEmpty())rgb=FoodColors.broth(pot.getLevel(),pot.getItem(7));
        var sprite=Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ResourceLocation.parse("minecraft:block/water_still"));
        var consumer=buffers.getBuffer(RenderType.translucent());var matrix=pose.last();
        float min=4.01F/16F,max=11.99F/16F,y=height/16F;
        float[][] points={{min,y,min,0,0},{min,y,max,0,1},{max,y,max,1,1},{max,y,min,1,0}};
        for(var p:points)consumer.addVertex(matrix,p[0],p[1],p[2]).setColor(rgb>>16&255,rgb>>8&255,rgb&255,215)
            .setUv(sprite.getU(p[3]),sprite.getV(p[4])).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
            .setLight(light).setNormal(matrix,0,1,0);
    }
    private WorldSoup(){}
}
