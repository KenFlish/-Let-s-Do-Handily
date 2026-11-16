package ru.codex.stirandcharm.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.pockyl.diegesis.api.client.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.satisfy.farm_and_charm.core.block.CookingPotBlock;
import ru.codex.stirandcharm.RoasterLid;
/** Native geometry and UVs, with the Candlelight spoon kept inside the open pot. */
final class PotBody implements NodePainter {
    private final StationView view;private final boolean spoon;
    PotBody(StationView view,boolean spoon){this.view=view;this.spoon=spoon;}
    public void paint(PoseStack pose,MultiBufferSource buffers,int light){
        var original=view.level().getBlockState(view.pos());
        boolean candlelight=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(original.getBlock()).getNamespace().equals("candlelight");
        if(!candlelight && !original.hasProperty(CookingPotBlock.COOKING))return;
        var state=candlelight?original.getBlock().defaultBlockState().setValue(CookingPotBlock.FACING,Direction.NORTH):original
            .setValue(CookingPotBlock.FACING,Direction.NORTH).setValue(CookingPotBlock.COOKING,false).setValue(CookingPotBlock.STAGE,CookingPotBlock.CookpotStage.NORMAL);
        if(candlelight)state=copyFlag(state,original,"needs_support");
        if(candlelight && spoon)state=PotModels.farmPot();
        pose.pushPose();pose.translate(0,.025F,0);
        if(spoon)pose.translate(-8,candlelight?3:0,-8);
        pose.scale(16,16,16);
        PotModels.draw(pose,buffers,light,state,q -> candlelight && !spoon ? PotModels.belowRim(q) : PotModels.spoon(q)==spoon,candlelight && !spoon);
        pose.popPose();
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static net.minecraft.world.level.block.state.BlockState copyFlag(net.minecraft.world.level.block.state.BlockState to,
            net.minecraft.world.level.block.state.BlockState from,String name) {
        net.minecraft.world.level.block.state.properties.Property property=from.getBlock().getStateDefinition().getProperty(name);
        if(property==null || !to.hasProperty(property))return to;
        Comparable value=from.getValue(property);
        return to.setValue(property,value);
    }
}
