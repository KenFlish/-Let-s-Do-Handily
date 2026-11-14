package ru.codex.stirandcharm.mixin.candlelight;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Candlelight is optional: its shape fix is applied only when the mod is installed. */
public final class CandlelightMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        try {
            var loaded = ModList.get();
            if (loaded != null) return loaded.isLoaded("candlelight");
            var loading = LoadingModList.get();
            return loading != null && loading.getModFileById("candlelight") != null;
        } catch (RuntimeException early) {
            return false;
        }
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}