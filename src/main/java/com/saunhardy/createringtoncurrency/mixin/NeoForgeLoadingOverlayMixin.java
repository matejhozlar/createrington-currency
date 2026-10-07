package com.saunhardy.createringtoncurrency.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.neoforge.client.loading.NeoForgeLoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = NeoForgeLoadingOverlay.class, remap = false)
public abstract class NeoForgeLoadingOverlayMixin {

    @WrapWithCondition(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/fml/earlydisplay/DisplayWindow;addMojangTexture(I)V"),
            remap = false
    )
    private static boolean createringtoncurrency$skipMojangLogo(DisplayWindow window, int textureId) {
        return false;
    }
}
