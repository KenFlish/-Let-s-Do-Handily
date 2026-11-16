package ru.codex.stirandcharm.mixin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.satisfy.farm_and_charm.core.block.entity.CuttingBoardBlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;
/** Keep queued input in the native saved pending slot; one native completion consumes one item. */
@Mixin(CuttingBoardBlockEntity.class)
public abstract class BoardQueue implements ru.codex.stirandcharm.BoardSelection, ru.codex.stirandcharm.ChopState {
    @Shadow private ItemStack pending;
    @Unique private ItemStack handily$queued=ItemStack.EMPTY;
    @Unique private boolean handily$valid;
    @Unique private net.minecraft.resources.ResourceLocation handily$recipe;
    @Unique private net.minecraft.nbt.CompoundTag handily$cuts=new net.minecraft.nbt.CompoundTag();
    @Unique private final net.minecraft.core.NonNullList<ItemStack> handily$outputs=net.minecraft.core.NonNullList.withSize(8,ItemStack.EMPTY);
    public net.minecraft.core.NonNullList<ItemStack> handily$outputs(){return handily$outputs;}
    public net.minecraft.nbt.CompoundTag handily$cuts(){return handily$cuts.copy();}
    public void handily$cuts(net.minecraft.nbt.CompoundTag cuts){handily$cuts=cuts.copy();handily$sync();}
    public net.minecraft.resources.ResourceLocation handily$recipe(){return handily$recipe;}
    public void handily$recipe(net.minecraft.resources.ResourceLocation recipe){handily$recipe=recipe;handily$sync();}
    @Inject(method="saveAdditional",at=@At("TAIL"),remap=false)
    private void handily$save(net.minecraft.nbt.CompoundTag tag,net.minecraft.core.HolderLookup.Provider registries,CallbackInfo ci){if(handily$recipe!=null)tag.putString("HandilyRecipe",handily$recipe.toString());tag.put("HandilyCuts",handily$cuts);var output=new net.minecraft.nbt.CompoundTag();net.minecraft.world.ContainerHelper.saveAllItems(output,handily$outputs,registries);tag.put("HandilyLegacyOutputs",output);}
    @Inject(method="loadAdditional",at=@At("TAIL"),remap=false)
    private void handily$load(net.minecraft.nbt.CompoundTag tag,net.minecraft.core.HolderLookup.Provider registries,CallbackInfo ci){handily$recipe=net.minecraft.resources.ResourceLocation.tryParse(tag.getString("HandilyRecipe"));handily$cuts=tag.getCompound("HandilyCuts").copy();handily$outputs.clear();net.minecraft.world.ContainerHelper.loadAllItems(tag.getCompound("HandilyLegacyOutputs"),handily$outputs,registries);}
    @Invoker("markUpdated") public abstract void handily$sync();
    @org.spongepowered.asm.mixin.gen.Accessor("pendingChops") public abstract void handily$pendingChops(int value);
    @Inject(method="chopPending",at=@At("HEAD"),remap=false)
    private void handily$before(Level level,CallbackInfo ci){
        handily$queued=pending.copy();
        var self=(CuttingBoardBlockEntity)(Object)this;
        handily$valid=net.satisfy.farm_and_charm.core.util.Strippables.isAxe(self.getKnife())
            ?net.satisfy.farm_and_charm.core.util.Strippables.strip(pending).isPresent()
            :level.getRecipeManager().getRecipeFor(net.satisfy.farm_and_charm.core.registry.RecipeTypeRegistry.CUTTING_BOARD_RECIPE_TYPE.get(),new net.minecraft.world.item.crafting.SingleRecipeInput(pending),level).isPresent();
    }
    @Inject(method="chopPending",at=@At("TAIL"),remap=false)
    private void handily$after(Level level,CallbackInfo ci){
        if(handily$valid && pending.isEmpty() && handily$queued.getCount()>1){pending=handily$queued.copyWithCount(handily$queued.getCount()-1);handily$sync();}
    }
}
