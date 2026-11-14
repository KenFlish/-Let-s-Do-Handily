package ru.codex.stirandcharm.client;

import java.util.ArrayList;
import java.util.List;
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
import ru.codex.stirandcharm.Settings;
import ru.codex.stirandcharm.StirAndCharm;

/**
 * The native cooking-pot model draws its own opaque soup at the same height as Handily's surface.
 * Only that quad is removed; One surface remains, and the pot's visible floor is kept.
 */
@EventBusSubscriber(modid = StirAndCharm.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class FarmPotModel {
    @SubscribeEvent
    public static void bake(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((id, model) -> {
            String key = id.toString();
            if (!key.startsWith("farm_and_charm:cooking_pot#")
                || !key.contains("stage=cooking") && !key.contains("stage=filled")) return model;
            return new BakedModelWrapper<>(model) {
                private List<BakedQuad> body(List<BakedQuad> all) {
                    if (!Settings.POT.get()) return all;
                    var out = new ArrayList<BakedQuad>();
                    for (var quad : all) {
                        String sprite = quad.getSprite().contents().name().getPath();
                        if (sprite.equals("block/cooking_pot_soup")) continue;
                        if (sprite.equals("block/cooking_pot_bottom") && RoasterBody.minY(quad) <= .001F
                            && quad.getDirection() == Direction.DOWN) {
                            out.add(floor(quad));
                            continue;
                        }
                        out.add(quad);
                    }
                    return out;
                }
                @Override public List<BakedQuad> getQuads(BlockState s, Direction side, RandomSource random) {
                    return body(originalModel.getQuads(s, side, random));
                }
                @Override public List<BakedQuad> getQuads(BlockState s, Direction side, RandomSource random, ModelData data, RenderType layer) {
                    return body(originalModel.getQuads(s, side, random, data, layer));
                }
            };
        });
    }

    /** The pot's underside quad, turned up and lifted to the native floor's top (2 px). */
    private static BakedQuad floor(BakedQuad quad) {
        var reversed = RoasterBody.reverse(quad);
        int[] data = reversed.getVertices().clone();
        int stride = data.length / 4;
        for (int i = 0; i < 4; i++) data[i * stride + 1] = Float.floatToRawIntBits(2F / 16F);
        return new BakedQuad(data, quad.getTintIndex(), Direction.UP, quad.getSprite(), quad.isShade());
    }

    private FarmPotModel() {}
}