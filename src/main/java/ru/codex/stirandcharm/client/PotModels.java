package ru.codex.stirandcharm.client;
import java.util.*;
import java.util.function.Predicate;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.*;
/** Selects existing quads, preserving their UVs. Coordinates are native block units. */
final class PotModels {
    static boolean belowRim(BakedQuad quad){int[] v=quad.getVertices();int stride=v.length/4;for(int i=0;i<4;i++)if(Float.intBitsToFloat(v[i*stride+1])>.751F)return false;return true;}
    static boolean lid(BakedQuad quad){return RoasterBody.minY(quad)>=13F/16F-.001F;}
    static boolean spoon(BakedQuad quad){
        if(!quad.getSprite().contents().name().getPath().equals("block/window_sill"))return false;
        int[] v=quad.getVertices();int stride=v.length/4;for(int i=0;i<4;i++)if(Float.intBitsToFloat(v[i*stride+1])>.51F)return true;return false;
    }
    static void draw(PoseStack pose,MultiBufferSource buffers,int light,BlockState state,Predicate<BakedQuad> part,boolean both){
        var renderer=Minecraft.getInstance().getBlockRenderer();var raw=CandlelightPotModel.raw(state);var installed=raw==null?renderer.getBlockModel(state):raw;
        var model=new BakedModelWrapper<>(installed){
            private List<BakedQuad> select(List<BakedQuad> all){var out=new ArrayList<BakedQuad>();for(var q:all)if(part.test(q)){out.add(q);if(both)out.add(interior(q));}return out;}
            public List<BakedQuad> getQuads(BlockState s,Direction side,RandomSource random,ModelData data,RenderType layer){return select(originalModel.getQuads(s,side,random,data,layer));}
            public List<BakedQuad> getQuads(BlockState s,Direction side,RandomSource random){return select(originalModel.getQuads(s,side,random));}
        };
        renderer.getModelRenderer().renderModel(pose.last(),buffers.getBuffer(RenderType.cutout()),state,model,1,1,1,light,OverlayTexture.NO_OVERLAY,ModelData.EMPTY,null);
    }
    static BlockState farmPot(){return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.parse("farm_and_charm:cooking_pot")).defaultBlockState();}
    static BakedQuad interior(BakedQuad quad){
        var reversed=RoasterBody.reverse(quad);
        if(quad.getDirection()!=Direction.DOWN || java.lang.Math.abs(RoasterBody.minY(quad))>.001F)return reversed;
        int[] data=reversed.getVertices().clone();int stride=data.length/4;
        for(int i=0;i<4;i++)data[i*stride+1]=Float.floatToRawIntBits(.0125F);
        return new BakedQuad(data,reversed.getTintIndex(),Direction.UP,reversed.getSprite(),reversed.isShade());
    }
    static BakedQuad rotate(BakedQuad quad,Vector3f pivot,Quaternionf turn){
        int[] data=quad.getVertices().clone();int stride=data.length/4;
        var n=quad.getDirection().getNormal();var normal=turn.transform(new Vector3f(n.getX(),n.getY(),n.getZ()));
        int packed=((int)(normal.x*127)&255)|(((int)(normal.y*127)&255)<<8)|(((int)(normal.z*127)&255)<<16);
        for(int i=0;i<4;i++){int j=i*stride;var p=new Vector3f(Float.intBitsToFloat(data[j]),Float.intBitsToFloat(data[j+1]),Float.intBitsToFloat(data[j+2]));p.sub(pivot).rotate(turn).add(pivot);data[j]=Float.floatToRawIntBits(p.x);data[j+1]=Float.floatToRawIntBits(p.y);data[j+2]=Float.floatToRawIntBits(p.z);data[j+7]=packed;}
        return new BakedQuad(data,quad.getTintIndex(),Direction.getNearest(normal.x,normal.y,normal.z),quad.getSprite(),quad.isShade());
    }
    private PotModels(){}
}
