package com.saunhardy.createringtoncurrency.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.sighs.apricityui.render.FontDrawer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.ExecutorService;

@Mixin(value = FontDrawer.class, remap = false)
public abstract class FontDrawerMixin {

    @Shadow
    @Final
    private static Map<String, FontDrawer.FontEntry> CACHE;

    @Redirect(
            method = "requestAsyncRaster",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/ExecutorService;execute(Ljava/lang/Runnable;)V"),
            remap = false
    )
    private static void createringtoncurrency$rasterOnRenderThread(ExecutorService executor, Runnable task) {
        task.run();
    }

    @Inject(
            method = "textureEntry",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/sighs/apricityui/render/FontDrawer;requestAsyncRaster(Lcom/sighs/apricityui/style/Text;Ljava/lang/String;Ljava/lang/String;Lcom/sighs/apricityui/render/FontDrawer$RasterMode;Lcom/sighs/apricityui/render/FontDrawer$TextQuadMode;Z)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true,
            remap = false
    )
    private static void createringtoncurrency$useFreshRaster(CallbackInfoReturnable<FontDrawer.FontEntry> cir, @Local(ordinal = 1) String key) {
        FontDrawer.drainCompletedRasters();
        FontDrawer.FontEntry entry = CACHE.get(key);
        if (entry != null) cir.setReturnValue(entry);
    }
}
