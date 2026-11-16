package ru.codex.stirandcharm.mixin;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.satisfy.farm_and_charm.core.block.entity.*;
import net.satisfy.foundation.util.ImplementedInventory;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import ru.codex.stirandcharm.*;

@Mixin({CookingPotBlockEntity.class, RoasterBlockEntity.class})
public abstract class ServingInventory extends BlockEntity implements MealVessel, BrothState {
    protected ServingInventory(BlockEntityType<?> type,BlockPos pos,BlockState state) { super(type,pos,state); }
    @Unique private float handily$mixture;
    @Unique private long handily$poured=Long.MIN_VALUE;
    public long handily$poured(){return handily$poured;}
    public void handily$poured(long tick){handily$poured=tick;setChanged();}
    public float handily$mixture(){return handily$mixture;}
    public void handily$mixture(float amount){float next=Math.clamp(amount,0,1);if(next!=handily$mixture){handily$mixture=next;setChanged();}}
    @Unique private ItemStack handily$dish = ItemStack.EMPTY;
    @Unique private int handily$pending;
    @Unique private int handily$before;
    @Invoker("generateOutputItem") protected abstract ItemStack handily$output(Recipe<?> recipe, RegistryAccess access);
    public ItemStack handily$vessel() { return handily$dish; }
    public int handily$unserved() { return handily$pending; }
    public void handily$unserved(int count) { handily$pending = Math.max(0, count); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider provider) { return saveWithoutMetadata(provider); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void setChanged() {
        super.setChanged();
        if (level!=null && !level.isClientSide) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);
    }

    @Inject(method="canCraft", at=@At("HEAD"), cancellable=true, remap=false)
    private void handily$withoutDish(Recipe<?> recipe, RegistryAccess access, CallbackInfoReturnable<Boolean> ci) {
        if (!MealServing.enabled(this) || recipe == null) return;
        ItemStack expected = handily$output(recipe, access), out = ((Container)this).getItem(7);
        boolean fits = !expected.isEmpty() && (out.isEmpty() || ItemStack.isSameItemSameComponents(out, expected)
            && out.getCount() + expected.getCount() <= out.getMaxStackSize());
        if (!out.isEmpty() && handily$pending>0 && !ItemStack.isSameItemSameComponents(handily$dish,MealServing.dish(recipe))) fits=false;
        ci.setReturnValue(fits);
    }
    @Inject(method="craft", at=@At("HEAD"), remap=false)
    private void handily$beforeCraft(Recipe<?> recipe, RegistryAccess access, CallbackInfo ci) {
        handily$before = ((Container)this).getItem(7).getCount();
        if (handily$before == 0) handily$pending = 0;
    }
    @Inject(method="craft", at=@At("TAIL"), remap=false)
    private void handily$afterCraft(Recipe<?> recipe, RegistryAccess access, CallbackInfo ci) {
        if (!MealServing.enabled(this)) return;
        if((Object)this instanceof CookingPotBlockEntity)handily$mixture=0;
        int added = ((Container)this).getItem(7).getCount() - handily$before;
        ItemStack dish = MealServing.dish(recipe);
        if (added > 0 && !dish.isEmpty()) { handily$dish = dish; handily$pending += added; ((Container)this).setChanged(); }
    }
    @Inject(method="saveAdditional", at=@At("TAIL"), remap=false)
    private void handily$save(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        tag.putFloat("HandilyMixture",handily$mixture);
        tag.putLong("HandilyPoured",handily$poured);
        tag.putInt("HandilyUnserved", handily$pending);
        if (!handily$dish.isEmpty()) tag.put("HandilyVessel", handily$dish.save(provider));
    }
    @Inject(method="loadAdditional", at=@At("TAIL"), remap=false)
    private void handily$load(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        handily$mixture=Math.clamp(tag.getFloat("HandilyMixture"),0,1);
        handily$poured=tag.contains("HandilyPoured")?tag.getLong("HandilyPoured"):Long.MIN_VALUE;
        handily$pending = Math.max(0, tag.getInt("HandilyUnserved"));
        handily$dish = ItemStack.parseOptional(provider, tag.getCompound("HandilyVessel"));
    }
    /** Overrides ImplementedInventory's default method, also used by ordinary menus and hoppers. */
    public ItemStack removeItem(int slot, int amount) {
        if (slot == 7 && handily$pending > 0) return MealServing.remove((Container)this, amount);
        ItemStack out = ContainerHelper.removeItem(((ImplementedInventory)this).getItems(), slot, amount);
        if (!out.isEmpty()) ((Container)this).setChanged();
        return out;
    }
}
