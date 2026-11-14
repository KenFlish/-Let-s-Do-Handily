package ru.codex.stirandcharm.client;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.*;
import ru.codex.stirandcharm.StirAndCharm;
/** The world model and collision state agree even when no Diegesis scene exists. */
@EventBusSubscriber(modid=StirAndCharm.ID, value=Dist.CLIENT, bus=EventBusSubscriber.Bus.MOD)
public final class RoasterWorldModel {
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((id, model) -> {
            String key = id.toString();
            if (!key.startsWith("farm_and_charm:roaster#") || !key.contains("handily_open=true")) return model;
            boolean hanging = key.contains("hanging=true");
            float lidY = hanging ? 8F : 6F;
            float yaw = key.contains("facing=east") ? -90 : key.contains("facing=south") ? -180 : key.contains("facing=west") ? -270 : 0;
            var facing = new Quaternionf().rotationY((float)java.lang.Math.toRadians(yaw));
            var pivot = facing.transform(new Vector3f(0,lidY/16F,.25F)).add(.5F,0,.5F);
            var axis = facing.transform(new Vector3f(1,0,0));
            var turn = new Quaternionf().rotationAxis((float)java.lang.Math.toRadians(105),axis.x,axis.y,axis.z);
            return new BakedModelWrapper<>(model) {
                private List<BakedQuad> open(List<BakedQuad> all) {
                    var out = new ArrayList<BakedQuad>();
                    for (var q : all) {
                        String sprite = q.getSprite().contents().name().getPath();
                        boolean lid = sprite.equals("block/roaster_parts") && RoasterBody.minY(q) >= lidY/16F - .001F;
                        if (lid) { var raised = rotate(q, pivot, turn); out.add(raised); out.add(RoasterBody.reverse(raised)); }
                        else if (!(sprite.equals("block/roaster_bottom") && q.getDirection() == Direction.UP)) {
                            out.add(q);
                            if (sprite.equals("block/roaster_bottom") || sprite.equals("block/roaster_side")) out.add(RoasterBody.reverse(q));
                        }
                    }
                    return out;
                }
                @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) { return open(originalModel.getQuads(state,side,random)); }
                @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType layer) { return open(originalModel.getQuads(state,side,random,data,layer)); }
            };
        });
    }
    private static BakedQuad rotate(BakedQuad quad, Vector3f pivot, Quaternionf turn) {
        int[] data = quad.getVertices().clone(); int stride = data.length / 4;
        var n = quad.getDirection().getNormal();
        var normal = turn.transform(new Vector3f(n.getX(),n.getY(),n.getZ()));
        int packed = ((int)(normal.x*127)&255) | (((int)(normal.y*127)&255)<<8) | (((int)(normal.z*127)&255)<<16);
        for (int i=0;i<4;i++) {
            int j=i*stride;
            var p=new Vector3f(Float.intBitsToFloat(data[j]),Float.intBitsToFloat(data[j+1]),Float.intBitsToFloat(data[j+2]));
            p.sub(pivot).rotate(turn).add(pivot);
            data[j]=Float.floatToRawIntBits(p.x); data[j+1]=Float.floatToRawIntBits(p.y); data[j+2]=Float.floatToRawIntBits(p.z);
            data[j+7]=packed;
        }
        return new BakedQuad(data,quad.getTintIndex(),Direction.getNearest(normal.x,normal.y,normal.z),quad.getSprite(),quad.isShade());
    }
}
