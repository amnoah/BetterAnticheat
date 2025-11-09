package better.anticheat.core.player.tracker.impl.mitigation;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.player.Player;
import better.anticheat.core.player.tracker.Tracker;
import better.anticheat.core.util.entity.raycast.SimpleRayCastUtil;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
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
    private final IntIncrementer compensationUnprocessedFakeCounter = new IntIncrementer(0);
    private final IntIncrementer compensationHitCancelCounter = new IntIncrementer(0);
    @Getter
    private final IntIncrementer hitCancelQueueCounter = new IntIncrementer(0);
    private final BetterAnticheat betterAnticheat;
    private final SimpleRayCastUtil simpleRayCastUtil;
    private final boolean supportsTickEnd;

    public HitregMitigationTracker(Player player, BetterAnticheat betterAnticheat) {
        super(player);
        this.betterAnticheat = betterAnticheat;
        this.simpleRayCastUtil = new SimpleRayCastUtil(betterAnticheat);
        this.supportsTickEnd = player.getUser().getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_21_2);
    }

    @Override
    public void handlePacketPlayReceive(@NotNull final PacketPlayReceiveEvent event) {
        switch (event.getPacketType()) {
            case INTERACT_ENTITY -> {

                final WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
                // Skip non attack packets
                if (packet.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

                // Handle other checks cancel requests first.
                // This is only really used for reach.
                if (this.hitCancelQueueCounter.get() > 0) {
                    this.hitCancelQueueCounter.decrementOrMin(0);
                    event.setCancelled(true);
                    return;
                }

                if (!betterAnticheat.isMitigationCombatDamageHitregEnabled()) return;
                // Skip if unprocessed fake packet
                // Cancel if cancel counter requires it.
                // TODO: Uncomment if the silent packet sending gets broken again.
                // if (this.unprocessedFakeCounter.get() <= 0) {
                if (this.compensationHitCancelCounter.get() > 0) {
                    this.compensationHitCancelCounter.decrementOrMin(0);
                    event.setCancelled(true);
                }
                // }

                // Decrement unprocessed fake packet counter
                this.compensationUnprocessedFakeCounter.decrementOrMin(0);

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
                // Do not run if not enabled
                if (!betterAnticheat.isMitigationCombatDamageHitregEnabled()) return;

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

                final var poses = this.player.getPlayerStatusTracker().getMostLikelyPoses();

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

                    final var rayCastResult = this.simpleRayCastUtil.checkNormalPose(entity, yaws, pitches, poses, positions, marginOfError, 0.1);

                    // Inside entity, attack anyways because fuck cheaters
                    // if (rayCastResult.isCollided()) continue;

                    final var reachAttribute = this.player.getPlayerStatusTracker().getAttributes().get(Attributes.ENTITY_INTERACTION_RANGE).getCurrent();

                    final var baseReach = reachAttribute == null ? 3.0 : reachAttribute;
                    final var reachLimit = baseReach + 1;
                    // Check hitbox matched and the distance is accceptable
                    if (!(rayCastResult.getDistance() > 0 && rayCastResult.getDistance() < reachLimit))
                        continue;

                    // Valid hit detected
                    // Increment the counters that are used on hit
                    // this.unprocessedFakeCounter.increment(); // TODO: Use this if the receive packet is not silent for some reason
                    compensationHitCancelCounter.increment();

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
            case CLIENT_TICK_END -> tick();

            // Legacy support
            case PLAYER_FLYING,
                 PLAYER_POSITION,
                 PLAYER_ROTATION,
                 PLAYER_POSITION_AND_ROTATION -> {
                if (!this.supportsTickEnd) {
                    tick();
                }
            }
        }
    }

    private void tick() {
        this.mitigationTicks.decrementOrMin(0);
        this.compensationHitCancelCounter.set(0);
    }
}
