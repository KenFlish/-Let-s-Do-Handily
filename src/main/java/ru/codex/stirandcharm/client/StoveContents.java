package ru.codex.stirandcharm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pockyl.diegesis.api.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.*;

/** One source of item rendering both behind closed doors and at the open station. */
final class StoveContents {
    static void install(StationView view,String node,String group,int index,float size){
        view.node(node).ifPresent(n->n.setPainter((pose,buffers,light)->{
            var stack=view.state().getItems(group)[index];
            item(view,stack,size,pose,buffers,light);
            if(view.isFocused() && n.isHovered() && stack.getCount()>1){
                var font=Minecraft.getInstance().font;pose.pushPose();pose.translate(size*.6F,.05F,-.1F);
                pose.mulPose(Axis.YP.rotationDegrees(180));pose.scale(.045F,-.045F,.045F);
                font.drawInBatch(Integer.toString(stack.getCount()),0,0,0xFFFFFFFF,true,pose.last().pose(),buffers,
                    net.minecraft.client.gui.Font.DisplayMode.NORMAL,0,light);pose.popPose();
            }
        }));
    }
    static void item(StationView view,ItemStack stack,float size,PoseStack pose,MultiBufferSource buffers,int light){
        if(stack.isEmpty())return;var mc=Minecraft.getInstance();
        boolean block=mc.getItemRenderer().getModel(stack,view.level(),null,0).isGui3d();
        pose.pushPose();pose.translate(0,block?size*.25F:size*.5F,0);pose.mulPose(Axis.YP.rotationDegrees(180));pose.scale(size,size,size);
        mc.getItemRenderer().renderStatic(stack,ItemDisplayContext.FIXED,light,OverlayTexture.NO_OVERLAY,pose,buffers,view.level(),0);pose.popPose();
    }
    private StoveContents(){}
}
