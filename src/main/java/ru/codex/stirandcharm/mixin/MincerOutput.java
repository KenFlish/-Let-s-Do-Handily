package ru.codex.stirandcharm.mixin;
import com.pockyl.diegesis.api.Diegesis;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.satisfy.farm_and_charm.core.block.entity.MincerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Constant;
import ru.codex.stirandcharm.Settings;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.satisfy.farm_and_charm.core.recipe.MincerRecipe;
import java.util.List;
/** Hold native output for collection while in a scene; resume normal ejection on exit. */
@Mixin(MincerBlockEntity.class)
public abstract class MincerOutput {
    private static boolean handily$working(Level level, BlockPos pos) {
        return level instanceof ServerLevel server && Diegesis.activeStation(server, pos)
            .filter(s -> !s.users().isEmpty()).isPresent();
    }
    @Inject(method = "dropItemsInOutputSlot", at = @At("HEAD"), cancellable = true, remap = false)
    private void handily$hold(Level level, BlockPos pos, BlockState state, MincerBlockEntity be, CallbackInfo ci) {
        if (handily$working(level, pos)) ci.cancel();
    }
    @Inject(method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/satisfy/farm_and_charm/core/block/entity/MincerBlockEntity;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void handily$full(Level level, BlockPos pos, BlockState state, MincerBlockEntity be, CallbackInfo ci) {
        if (!handily$working(level, pos) || be.getItem(1).isEmpty()) return;
        ItemStack input=be.getItem(0), output=be.getItem(1);
        var next=ru.codex.stirandcharm.Appliance.MINCER.recipes(level).stream()
            .filter(h -> h.value().getIngredients().stream().anyMatch(i -> i.test(input))).findFirst();
        if (input.isEmpty() || next.isEmpty()) { ci.cancel(); return; }
        ItemStack made=next.get().value().getResultItem(level.registryAccess());
        if (!ItemStack.isSameItemSameComponents(output,made) || output.getCount()+made.getCount()>output.getMaxStackSize()) ci.cancel();
    }
    @ModifyConstant(method="tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/satisfy/farm_and_charm/core/block/entity/MincerBlockEntity;)V", constant=@Constant(floatValue=20F), remap=false)
    private float handily$longer(float original) { return Settings.MINCER.get() ? 80F : original; }
    @Redirect(method="tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/satisfy/farm_and_charm/core/block/entity/MincerBlockEntity;)V", at=@At(value="INVOKE",target="Lnet/satisfy/farm_and_charm/core/block/entity/MincerBlockEntity;setItem(ILnet/minecraft/world/item/ItemStack;)V"), remap=false)
    private void handily$batch(MincerBlockEntity be,int slot,ItemStack stack) {
        if (slot==1 && be.getLevel()!=null && handily$working(be.getLevel(),be.getBlockPos()) && !be.getItem(1).isEmpty()) {
            ItemStack old=be.getItem(1);
            be.setItem(1,old.copyWithCount(old.getCount()+stack.getCount()));
        } else be.setItem(slot,stack);
    }
    @Redirect(method="tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/satisfy/farm_and_charm/core/block/entity/MincerBlockEntity;)V", at=@At(value="INVOKE",target="Lnet/satisfy/farm_and_charm/core/block/entity/MincerBlockEntity;getRecipe(Ljava/util/List;Lnet/minecraft/core/NonNullList;)Lnet/satisfy/farm_and_charm/core/recipe/MincerRecipe;"), remap=false)
    private MincerRecipe handily$inputOnly(MincerBlockEntity be,List<RecipeHolder<MincerRecipe>> recipes,NonNullList<ItemStack> inventory) {
        return recipes.stream().map(RecipeHolder::value).filter(r -> r.getInput().test(be.getItem(0))).findFirst().orElse(null);
    }
}
