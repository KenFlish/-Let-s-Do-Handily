package ru.codex.stirandcharm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pockyl.diegesis.api.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.AABB;

/** Only the hovered wooden handle is outlined; the native block supplies its model. */
final class HandleOutline implements NodePainter {
    private final StationView view;
    HandleOutline(StationView view){this.view=view;}
    public void paint(PoseStack pose,MultiBufferSource buffers,int light){
        if(view.isFocused() && view.node("dump_handle").map(NodeView::isHovered).orElse(false))
            LevelRenderer.renderLineBox(pose,buffers.getBuffer(RenderType.lines()),new AABB(-2.02,2.98,6.98,4.02,5.02,9.02),1,.91F,.75F,1);
    }
}
