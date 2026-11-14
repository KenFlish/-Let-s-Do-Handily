package ru.codex.stirandcharm.client;

import com.pockyl.diegesis.api.client.StationView;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;

/** Change the camera, keeping the working surface aligned with the actual block. */
final class ApproachCamera {
    static void keep(StationView view){if(view.isFocused() && view.currentGlance().isEmpty())open(view);}
    static void open(StationView view) {
        var player=Minecraft.getInstance().player;
        if(player==null)return;
        double dx=player.getX()-view.pos().getX()-.5, dz=player.getZ()-view.pos().getZ()-.5;
        var towards=Direction.getNearest((float)dx,0,(float)dz);
        int turns=Math.floorMod(towards.get2DDataValue()-view.facing().get2DDataValue(),4);
        // Minecraft horizontal direction indices advance clockwise; scene coordinates face north.
        float angle=-turns*90F;
        view.glance("approach_"+turns);
        view.node("approach_ui").ifPresent(n -> n.rotateTo(0,angle,0,0));
    }
    private ApproachCamera(){}
}
