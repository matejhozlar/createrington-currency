package com.saunhardy.createringtoncurrency.mixin;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class CreateMixinPlugin implements IMixinConfigPlugin {
    private static final boolean CREATE_LOADED;
    private static final Set<String> CREATE_MIXINS = Set.of("com.saunhardy.createringtoncurrency.mixin.TrainCrashMixin");

    static {
        CREATE_LOADED = LoadingModList.get().getModFileById("create") != null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return CREATE_LOADED || !CREATE_MIXINS.contains(mixinClassName);
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
