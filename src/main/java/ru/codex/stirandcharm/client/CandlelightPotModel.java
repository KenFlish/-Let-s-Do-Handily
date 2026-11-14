package ru.codex.stirandcharm.client;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import ru.codex.stirandcharm.*;

/** A permanently open Candlelight pot. Native heated variants cannot remove its floor. */
@EventBusSubscriber(modid=StirAndCharm.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class CandlelightPotModel {
    private static Map<String,BakedModel> rawShells=Map.of();
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event){
        var shells=new HashMap<String,BakedModel>();
        event.getModels().forEach((id,model)->{
            String key=id.toString();if(key.startsWith("candlelight:cooking_pot#") && key.contains("stage=normal"))shells.putIfAbsent(shellKey(key),model);
        });
        rawShells=Map.copyOf(shells);
        event.getModels().replaceAll((id,model)->{
            String key=id.toString();if(!key.startsWith("candlelight:cooking_pot#"))return model;
            var shell=shells.getOrDefault(shellKey(key),model);
            return new BakedModelWrapper<>(model){
                private List<BakedQuad> body(List<BakedQuad> all){
                    var out=new ArrayList<BakedQuad>();
                    for(var quad:all)if(PotModels.belowRim(quad)){
                        out.add(quad);if(quad.getDirection()==Direction.DOWN)out.add(PotModels.interior(quad));
                    }
                    return out;
                }
                public List<BakedQuad> getQuads(BlockState s,Direction side,RandomSource random){return Settings.CANDLELIGHT.get()?body(shell.getQuads(s,side,random)):originalModel.getQuads(s,side,random);}
                public List<BakedQuad> getQuads(BlockState s,Direction side,RandomSource random,ModelData data,RenderType layer){return Settings.CANDLELIGHT.get()?body(shell.getQuads(s,side,random,data,layer)):originalModel.getQuads(s,side,random,data,layer);}
            };
        });
    }
    private static String shellKey(String key){return (key.contains("facing=east")?"e":key.contains("facing=south")?"s":key.contains("facing=west")?"w":"n")+key.contains("needs_support=true");}
    static BakedModel raw(BlockState state){
        if(!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals("candlelight:cooking_pot"))return null;
        return rawShells.get(shellKey(state.toString()));
    }
}
