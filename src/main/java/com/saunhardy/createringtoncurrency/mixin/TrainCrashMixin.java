package com.saunhardy.createringtoncurrency.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.saunhardy.createringtoncurrency.events.TrainCrashHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;

@Mixin(value = Train.class, remap = false)
public abstract class TrainCrashMixin {

    @Shadow public UUID id;
    @Shadow public Component name;
    @Shadow public double speed;
    @Shadow public boolean derailed;
    @Shadow public List<Carriage> carriages;

    @Inject(method = "crash", at = @At("HEAD"), remap = false)
    private void createringtoncurrency$onTrainCrash(CallbackInfo ci) {
        // crash() returns early if already derailed, so mirror that check
        if (this.derailed) return;

        String trainName = this.name != null ? this.name.getString() : "Unknown";
        int carriageCount = this.carriages != null ? this.carriages.size() : 0;

        // Extract position from first carriage entity
        double[] pos = null;
        String dimension = null;

        if (this.carriages != null && !this.carriages.isEmpty()) {
            try {
                final double[][] posHolder = {null};
                final String[] dimHolder = {null};

                this.carriages.get(0).forEachPresentEntity(entity -> {
                    Vec3 entityPos = entity.position();
                    posHolder[0] = new double[]{entityPos.x, entityPos.y, entityPos.z};
                    dimHolder[0] = entity.level().dimension().location().toString();
                });

                pos = posHolder[0];
                dimension = dimHolder[0];
            } catch (Exception ignored) {
                // Position extraction failed, continue without it
            }
        }

        TrainCrashHandler.reportCrash(this.id, trainName, this.speed, carriageCount, pos, dimension);
    }
}
