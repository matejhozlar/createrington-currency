package com.saunhardy.createringtoncurrency.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.saunhardy.createringtoncurrency.events.TrainCrashHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.*;

@Mixin(value = Train.class, remap = false)
public abstract class TrainCrashMixin {

    @Shadow public UUID id;
    @Shadow public Component name;
    @Shadow public double speed;
    @Shadow public boolean derailed;
    @Shadow public List<Carriage> carriages;
    @Shadow public @Nullable UUID owner;
    @Shadow public @Nullable Player backwardsDriver;
    @Shadow public TrackGraph graph;

    @Inject(method = "crash", at = @At("HEAD"), remap = false)
    private void createringtoncurrency$onTrainCrash(CallbackInfo ci) {
        // crash() returns early if already derailed, so mirror that check
        if (this.derailed) return;

        String trainName = this.name != null ? this.name.getString() : "Unknown";
        int carriageCount = this.carriages != null ? this.carriages.size() : 0;

        // Extract position, driver, and passengers from carriage entities
        double[] pos = null;
        String dimension = null;
        UUID driverUuid = null;
        List<TrainCrashHandler.PlayerInfo> passengers = new ArrayList<>();

        if (this.carriages != null) {
            final double[][] posHolder = {null};
            final String[] dimHolder = {null};

            for (Carriage carriage : this.carriages) {
                try {
                    carriage.forEachPresentEntity(entity -> {
                        // Grab position from the first present entity we find
                        if (posHolder[0] == null) {
                            Vec3 entityPos = entity.position();
                            posHolder[0] = new double[]{entityPos.x, entityPos.y, entityPos.z};
                            dimHolder[0] = entity.level().dimension().location().toString();
                        }

                        // Get controlling player (driver)
                        Optional<UUID> controlling = entity.getControllingPlayer();
                        if (controlling.isPresent()) {
                            passengers.add(new TrainCrashHandler.PlayerInfo(
                                    controlling.get(), null, true));
                        }

                        // Get all passengers
                        for (Entity passenger : entity.getIndirectPassengers()) {
                            if (passenger instanceof Player p) {
                                boolean isDriver = controlling.isPresent()
                                        && controlling.get().equals(p.getUUID());
                                passengers.add(new TrainCrashHandler.PlayerInfo(
                                        p.getUUID(), p.getName().getString(), isDriver));
                            }
                        }
                    });
                } catch (Exception ignored) {
                }
            }

            pos = posHolder[0];
            dimension = dimHolder[0];

            // Fallback: get position from track graph if no entity was loaded
            if (pos == null && this.graph != null) {
                try {
                    TravellingPoint point = this.carriages.get(0).getLeadingPoint();
                    if (point.node1 != null && point.edge != null) {
                        Vec3 graphPos = point.getPosition(this.graph);
                        pos = new double[]{graphPos.x, graphPos.y, graphPos.z};
                        dimension = point.node1.getLocation().dimension.location().toString();
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // Deduplicate passengers and resolve driver UUID
        Map<UUID, TrainCrashHandler.PlayerInfo> deduped = new LinkedHashMap<>();
        for (TrainCrashHandler.PlayerInfo p : passengers) {
            deduped.merge(p.uuid(), p, (existing, incoming) -> new TrainCrashHandler.PlayerInfo(
                    existing.uuid(),
                    existing.name() != null ? existing.name() : incoming.name(),
                    existing.isDriver() || incoming.isDriver()));
        }

        // Find driver UUID
        for (TrainCrashHandler.PlayerInfo p : deduped.values()) {
            if (p.isDriver()) {
                driverUuid = p.uuid();
                break;
            }
        }

        // Backwards driver
        String backwardsDriverName = null;
        UUID backwardsDriverUuid = null;
        if (this.backwardsDriver != null) {
            backwardsDriverUuid = this.backwardsDriver.getUUID();
            backwardsDriverName = this.backwardsDriver.getName().getString();
        }

        TrainCrashHandler.reportCrash(this.id, trainName, this.speed, carriageCount,
                pos, dimension, this.owner, driverUuid,
                new ArrayList<>(deduped.values()),
                backwardsDriverUuid, backwardsDriverName);
    }
}
