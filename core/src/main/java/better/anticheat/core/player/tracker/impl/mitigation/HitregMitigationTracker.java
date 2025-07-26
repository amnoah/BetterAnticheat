package better.anticheat.core.player.tracker.impl.mitigation;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.player.Player;
import better.anticheat.core.player.tracker.Tracker;
import better.anticheat.core.util.entity.RayCastUtil;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAnimation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import wtf.spare.sparej.incrementer.IntIncrementer;

import java.util.List;
import java.util.Optional;

/**
 * Handles actual mitigations.
 * WARNING: THIS TRACKER DOES NOT HANDLE SEND PACKETS FOR PERFORMANCE REASONS
 */
public class HitregMitigationTracker extends Tracker {
    @Getter
    private final IntIncrementer mitigationTicks = new IntIncrementer(0);
    private final IntIncrementer unprocessedFakeCounter = new IntIncrementer(0);
    private final IntIncrementer hitCancelCounter = new IntIncrementer(0);
    private final BetterAnticheat betterAnticheat;

    public HitregMitigationTracker(Player player, BetterAnticheat betterAnticheat) {
        super(player);
        this.betterAnticheat = betterAnticheat;
    }

    @Override
    public void handlePacketPlayReceive(@NotNull final PacketPlayReceiveEvent event) {
        switch (event.getPacketType()) {
            case INTERACT_ENTITY -> {
                final WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
                // Skip non attack packets
                if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

                // Skip if unprocessed fake packet
                // Cancel if cancel counter requires it.
                // TODO: Uncomment if the silent packet sending gets broken again.
                // if (this.unprocessedFakeCounter.get() <= 0) {
                if (this.hitCancelCounter.get() > 0) {
                    this.hitCancelCounter.decrementOrMin(0);
                    event.setCancelled(true);
                }
                // }

                // Decrement unprocessed fake packet counter
                this.unprocessedFakeCounter.decrementOrMin(0);

                // Now, handle hit debounce mitigations.
                if (this.mitigationTicks.get() <= 0 || !this.betterAnticheat.isMitigationCombatTickEnabled())
                    return;
                final boolean isAttackTooFast = this.player.getCmlTracker().getTicksSinceLastAttack() < this.betterAnticheat.getMitigationCombatTickDuration();
                final boolean isMitigating = this.mitigationTicks.get() > 0;

                final boolean tickCheckFailed = isAttackTooFast && isMitigating;

                // Hit debounce/cps limit mitigation
                if (tickCheckFailed) {
                    event.setCancelled(true);
                }
            }
            case ANIMATION -> {
                // Anti lag
                if (player.getActionTracker().getTicksSinceAttack().get() > 10_000) {
                    return;
                }

                // Anti dig problem
                if (player.getActionTracker().getTicksSinceDigging().get() < 10) {
                    return;
                }

                // Anti place problem
                if (player.getActionTracker().getTicksSincePlace().get() < 5) {
                    return;
                }

                // Swing logic
                final WrapperPlayClientAnimation packet = new WrapperPlayClientAnimation(event);
                if (packet.getHand() == InteractionHand.OFF_HAND) return;

                // Get this, we will be using it a ton.
                final Location playerPos = new Location(
                        player.getPositionTracker().getX(),
                        player.getPositionTracker().getY(),
                        player.getPositionTracker().getZ(),
                        player.getRotationTracker().getYaw(),
                        player.getRotationTracker().getPitch()
                );

                // Get the possible yaws
                final var yaws = new double[]{
                        playerPos.getYaw(),
                        player.getRotationTracker().getLastYaw()
                };

                // Get the possible pitches
                final var pitches = new double[]{
                        player.getRotationTracker().getPitch(),
                        player.getRotationTracker().getLastPitch()
                };

                // Get the possible locations
                final var positions = List.of(
                        playerPos
                );

                // Prevent major performance issues in crowded areas.
                var scanned = 0;
                for (final var entity : player.getEntityTracker().getEntities().values()) {
                    // Check for entities where all axises are less than 6 blocks from the player.
                    final var closeEnough = Math.abs(entity.getServerPosX().getCurrent() - playerPos.getX()) < 6 && Math.abs(entity.getServerPosY().getCurrent() - playerPos.getY()) < 6 && Math.abs(entity.getServerPosZ().getCurrent() - playerPos.getZ()) < 6;
                    if (!closeEnough) continue;

                    if (entity.getType() != EntityTypes.PLAYER) continue;
                    // Only increment after determining if it's a local player.
                    if (scanned++ > 20) break;

                    // Only expand against cheaters.
                    final var otherPlayer = BetterAnticheat.getInstance().getPlayerManager().getPlayerByEntityId(entity.getId());
                    if (otherPlayer == null) continue;
                    if (otherPlayer.getMitigationTracker().getMitigationTicks().get() <= 0) continue;

                    // Raycast the hitbox

                    // 0.005 is movement offset.
                    // 0.2 is the hitbox cheat I want to give people.
                    var marginOfError = 0.005 + 0.2;

                    final var rayCastResult = RayCastUtil.checkNormalPose(entity, yaws, pitches, positions, marginOfError, 0.1);

                    // Inside entity, attack anyways because fuck cheaters
                    // if (rayCastResult.isCollided()) continue;

                    // Check hitbox matched and the distance is accceptable
                    if (!(rayCastResult.getDistance() > 0 && rayCastResult.getDistance() < 3.5))
                        continue;

                    // Valid hit detected
                    // Increment the counters that are used on hit
                    // this.unprocessedFakeCounter.increment(); // TODO: Use this if the receive packet is not silent for some reason
                    hitCancelCounter.increment();

                    // Send packet
                    player.getUser().receivePacketSilently(new WrapperPlayClientInteractEntity(
                            entity.getId(),
                            WrapperPlayClientInteractEntity.InteractAction.ATTACK,
                            packet.getHand(),
                            Optional.of(new Vector3f((float) rayCastResult.getVector().getX(), (float) rayCastResult.getVector().getY(), (float) rayCastResult.getVector().getZ())),
                            Optional.of(player.getActionTracker().isSneaking()))
                    );
                    return;
                }
            }
            case CLIENT_TICK_END -> {
                this.mitigationTicks.decrementOrMin(0);
                this.hitCancelCounter.set(0);
            }
        }
    }
}
