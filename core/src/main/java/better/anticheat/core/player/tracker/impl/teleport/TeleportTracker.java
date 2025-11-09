package better.anticheat.core.player.tracker.impl.teleport;

import better.anticheat.core.player.Player;
import better.anticheat.core.player.tracker.Tracker;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerRotation;
import lombok.Getter;
import wtf.spare.sparej.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * This tracker compensates teleportation packets and checks whether a player's movement matches an eligible teleport.
 */
public class TeleportTracker extends Tracker {

    private final List<Teleport> eligibleTeleports = new ArrayList<>(), remove = new ArrayList<>();

    @Getter
    private boolean teleported = true, positionTeleported = true, rotationTeleported = true, missedTeleport = false;
    private boolean positionLastTick = false, positionThisTick = false;

    private final boolean supportsTickEnd;

    public TeleportTracker(Player player) {
        super(player);
        this.supportsTickEnd = player.getUser().getClientVersion()
                .isNewerThanOrEquals(ClientVersion.V_1_21_2);
    }

    @Override
    public void handlePacketPlayReceive(PacketPlayReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CLIENT_TICK_END) handleTickEnd();

        if (!WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) return;
        final var wrapper = new WrapperPlayClientPlayerFlying(event);

        if (!supportsTickEnd) handleTickEnd();

        if (wrapper.hasPositionChanged()) positionThisTick = true;

        for (final var teleport : eligibleTeleports) {
            if (match(teleport, wrapper)) {
                if (teleport.getPosition() != null) positionTeleported = true;
                if (teleport.getRotation() != null) rotationTeleported = true;
                teleported = true;
                teleport.setHandled(true);
            }

            /*
             * Players receive teleports sequentially, so if they haven't handled this teleport yet then they can't
             * handle any future ones.
             * Another intended feature: we allow players to potentially qualify for a teleport multiple times. We could
             * be wrong and they hadn't actually teleported (such as the scenario a player is standing still, teleports
             * to themself, they have a network lag spike after confirming the pre, and then they start moving. Once
             * their network resumes they would teleport but we would have already wrongly assumed they had teleported).
             * This could allow for potential exploitation of the anticheat, but it can be fixed by lag compensating
             * more. The more things we have lag compensated, the more harmful it is for a player to modify this
             * behavior.
             * This could also be fixed with a prediction system, but that's a long ways out.
             */
            // Note: Commented out for now to try to increase handler performance!
            //if (!teleport.isHandled()) break;
        }
    }

    /**
     * Handle compensating different types of teleport packets.
     */
    @Override
    public void handlePacketPlaySend(PacketPlaySendEvent event) {
        switch (event.getPacketType()) {
            case ENTITY_TELEPORT -> {
                WrapperPlayServerEntityTeleport wrapper = new WrapperPlayServerEntityTeleport(event);
                if (wrapper.getEntityId() != player.getUser().getEntityId()) return;
                Teleport teleport = new Teleport(wrapper.getPosition(), new Pair<>(wrapper.getPitch(), wrapper.getYaw()), wrapper.getRelativeFlags(), wrapper.isOnGround());
                handleTeleport(teleport);
            }
            case PLAYER_ROTATION -> {
                WrapperPlayServerPlayerRotation wrapper = new WrapperPlayServerPlayerRotation(event);
                Teleport teleport = new Teleport(null, new Pair<>(wrapper.getPitch(), wrapper.getYaw()), null, null);
                handleTeleport(teleport);
            }
            case PLAYER_POSITION_AND_LOOK -> {
                WrapperPlayServerPlayerPositionAndLook wrapper = new WrapperPlayServerPlayerPositionAndLook(event);
                Teleport teleport = new Teleport(wrapper.getPosition(), new Pair<>(wrapper.getPitch(), wrapper.getYaw()), wrapper.getRelativeFlags(), null);
                handleTeleport(teleport);
            }
        }
    }

    /**
     * Handles a tickend. Is a separate method for compatibility so we can support pre 1.21.2.
     */
    private void handleTickEnd() {
        positionTeleported = rotationTeleported = missedTeleport = teleported = false;
        positionLastTick = positionThisTick;
        positionThisTick = false;

        /*
         * Due to the current design of our confirmation system, teleports do not actually resolve within our
         * confirmation window. Rather, they resolve in the tick following the post transaction (apart from world
         * change teleports! Which resolve in time for some reason?). So, we handle it here in the following tick if
         * needed.
         */
        for (Teleport teleport : remove) {
            eligibleTeleports.remove(teleport);
            if (!teleport.isHandled()) missedTeleport = true;
        }
        remove.clear();
    }

    /**
     * Handle compensating teleports. When the initial confirmation returns, we start including the teleport. When the
     * post confirmation returns, we remove the teleport.
     */
    private void handleTeleport(Teleport teleport) {
        var confirm = player.getConfirmationTracker().confirm();
        confirm.onBegin(() -> {
            eligibleTeleports.add(teleport);
        });
        confirm.onAfterConfirm(() -> {
            remove.add(teleport);
        });
    }

    /**
     * This method checks whether the given Teleport matches the player's current Flying packet. To check this, we see
     * if the onGround status, position, and rotation matches. We also account for relative movements,
     */
    private boolean match(Teleport teleport, WrapperPlayClientPlayerFlying wrapper) {
        /*
         * Player Rotations and Player Position and Look packets don't define a ground status.
         * Entity Teleport define a ground status.
         */
        ground:
        {
            if (teleport.getOnGround() == null) break ground;
            if (!teleport.getOnGround().equals(wrapper.isOnGround())) return false;
        }

        /*
         * Entity Teleport and Player Position and Look define a position.
         * Player Rotations don't define a position.
         */
        position:
        {
            if (teleport.getPosition() == null) break position;
            if (!wrapper.hasPositionChanged()) return false;
            double x = teleport.getPosition().getX(), y = teleport.getPosition().getY(), z = teleport.getPosition().getZ();
            boolean relX = false, relY = false, relZ = false;

            if (teleport.getRelativeFlags() != null) {
                if (teleport.getRelativeFlags().has(RelativeFlag.X)) {
                    x += player.getPositionTracker().getLastX();
                    relX = true;
                }
                if (teleport.getRelativeFlags().has(RelativeFlag.Y)) {
                    y += player.getPositionTracker().getLastY();
                    relY = true;
                }
                if (teleport.getRelativeFlags().has(RelativeFlag.Z)) {
                    z += player.getPositionTracker().getLastZ();
                    relZ = true;
                }
            }

            if (!compareCoordinate(x, wrapper.getLocation().getX(), relX)) return false;
            if (!compareCoordinate(y, wrapper.getLocation().getY(), relY)) return false;
            if (!compareCoordinate(z, wrapper.getLocation().getZ(), relZ)) return false;
        }

        /*
         * Currently no teleport packets don't include a rotation, but I don't trust Mojang so we futureproof!
         */
        rotation:
        {
            if (teleport.getRotation() == null) break rotation;
            if (!wrapper.hasRotationChanged()) return false;

            float pitch = teleport.getRotation().getX(), yaw = teleport.getRotation().getY();

            if (teleport.getRelativeFlags() != null) {
                if (teleport.getRelativeFlags().has(RelativeFlag.YAW) || teleport.getRelativeFlags().has(RelativeFlag.PITCH))
                    break rotation;

                /*
                // Rotation relatives seems to be messing up a lot right now. So, we don't care about it until we figure
                // out.
                if (teleport.getRelativeFlags().has(RelativeFlag.PITCH)) {
                    // Pitch is bounded -90 to 90
                    if (pitch < 0) {
                        pitch = Math.max(-90, player.getRotationTracker().getLastPitch() + pitch);
                    } else {
                        pitch = Math.min(90, player.getRotationTracker().getLastPitch() + pitch);
                    }
                }
                if (teleport.getRelativeFlags().has(RelativeFlag.YAW)) yaw += player.getRotationTracker().getLastYaw();
                 */
            }

            // Verify equality.
            if (pitch != wrapper.getLocation().getPitch() || yaw != wrapper.getLocation().getYaw()) return false;
        }

        return true;
    }

    /**
     * See ArtificialPositionCheck.java for an explanation behind the value used here.
     * Check if the player's coordinate is accurate for their teleport.
     */
    private boolean compareCoordinate(double first, double second, boolean relative) {
        /*
         * If the teleport is not a relative, it must be an exact match to the teleport.
         * If a teleport is relative, it means that it is based on the previous position.
         * If the player did not send a position packet in their last tick, it means they could have desynced and their
         * movement could be off by up to 2.0E-4 (see ArtificialPositionCheck.java for an explanation on the value).
         */
        if (relative) {
            double leniency;
            if (positionLastTick) leniency = 0;
            else leniency = 2.0E-4;
            return Math.abs(first - second) <= leniency;
        } else return first == second;
    }
}