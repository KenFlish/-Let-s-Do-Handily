package ru.codex.stirandcharm.client;

import java.util.HashMap;
import java.util.Map;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.scene.CameraGlance;
import com.pockyl.diegesis.api.scene.CameraRig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import ru.codex.stirandcharm.StirAndCharm;

/**
 * Picks the side of a Handily station before Diegesis starts the approach, so the camera never flies
 * to the front first. The side is remembered for the open, and the interface nodes are turned by it.
 */
public final class HandilyCamera {
    private static final Map<String, Integer> SIDES = new HashMap<>();

    private static String key(BlockPos pos) {
        var level = Minecraft.getInstance().level;
        return (level == null ? "" : level.dimension().location()) + "@" + pos.asLong();
    }

    /** The side chosen for this opening: 0 front, 1 east, 2 south, 3 west. */
    public static int side(BlockPos pos, Direction facing) {
        Integer known = SIDES.get(key(pos));
        if (known != null) return known;
        int side = compute(pos, facing, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        remember(pos, side);
        return side;
    }

    /** Chooses the side from the camera at the moment the approach begins. */
    public static void choose(BlockPos pos, Direction facing) {
        remember(pos, compute(pos, facing, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()));
    }

    static int compute(BlockPos pos, Direction facing, Vec3 from) {
        double dx = from.x - pos.getX() - .5, dz = from.z - pos.getZ() - .5;
        if (Math.abs(dx) + Math.abs(dz) < .001) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                dx = player.getX() - pos.getX() - .5;
                dz = player.getZ() - pos.getZ() - .5;
            }
        }
        var towards = Direction.getNearest((float) dx, 0, (float) dz);
        return Math.floorMod(towards.get2DDataValue() - facing.get2DDataValue(), 4);
    }

    public static CameraGlance glance(CameraRig rig, int side) {
        return rig.glance("approach_" + side).orElse(null);
    }

    public static boolean handily(StationView view) {
        return view.type().id().getNamespace().equals(StirAndCharm.ID);
    }

    public static boolean handily(com.pockyl.diegesis.impl.client.ClientStation station) {
        return station != null && station.type().id().getNamespace().equals(StirAndCharm.ID)
            && !station.type().id().equals(StirAndCharm.id("stove"));
    }

    private static void remember(BlockPos pos, int side) {
        if (SIDES.size() > 128) SIDES.clear();
        SIDES.put(key(pos), side);
    }

    private HandilyCamera() {}
}