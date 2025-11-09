package better.anticheat.core.check.impl.combat;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckInfo;
import better.anticheat.core.player.Player;
import better.anticheat.core.util.entity.BoundingBoxSize;
import better.anticheat.core.util.EasyLoops;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import lombok.extern.slf4j.Slf4j;

/**
 * This check validates the target field of the INTERACT_AT packet.
 * <a href="https://minecraft.wiki/w/Java_Edition_protocol/Packets?oldid=3062733#Interact">wiki.vg</a>
 */
@CheckInfo(name = "InvalidInteractionPosition", category = "combat")
@Slf4j
public class InvalidInteractionPositionCheck extends Check {
    /**
     * Margin of error due to non-strict MC FP32/64 coercion precision.
     * Is somewhat a heuristic value.
     */
    public static final double FP_MARGIN = 0.0001;

    public static final EntityPose[] STRAIGHT_POSES = {
            EntityPose.STANDING,
            EntityPose.FALL_FLYING,
            EntityPose.SPIN_ATTACK,
            EntityPose.LONG_JUMPING,
            EntityPose.CROAKING,
            EntityPose.USING_TONGUE,
            EntityPose.DIGGING,
            EntityPose.SNIFFING,
            EntityPose.SHOOTING,
            EntityPose.INHALING
    };

    public InvalidInteractionPositionCheck(BetterAnticheat plugin, Player player) {
        super(plugin, player);
    }

    @Override
    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            final var wrapper = new WrapperPlayClientInteractEntity(event);

            // This field only exists for INTERACT_AT interactions, not standard interact or attack types.
            if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT) {
                if (wrapper.getTarget().isPresent()) {
                    fail("Interact type of " + wrapper.getAction().name() + " has unwanted target location.");
                }
                return;
            }

            // The field is mandatory.
            if (wrapper.getTarget().isEmpty()) {
                fail("INTERACT_AT packet but no target location.");
                return;
            }

            final var packetTarget = wrapper.getTarget().get();

            // Process the entity, also the ender dragon has a fucked hitbox set lol
            final var entity = player.getEntityTracker().getEntities().get(wrapper.getEntityId());
            if (entity == null || entity.getType() == EntityTypes.ENDER_DRAGON) return;

            // At this point, we may need to exempt some poses. Iterate twice to check if the pose is allowed. Scales well as STRAIGHT_POSES is of constant size.
            if (
                    EasyLoops.allMatch(
                            entity.getPoses(),
                            (pose) -> {
                                for (final var straightPose : STRAIGHT_POSES) {
                                    if (pose == straightPose | pose == null) {
                                        return true;
                                    }
                                }

                                return false;
                            }
                    )
            ) {
                log.debug("Entity {} has an invalid pose: {}", entity.getType().getName().getKey(), entity.getPoses());
                return;
            }

            // Continue with the check.
            final var height = BoundingBoxSize.getHeight(entity);
            final var width = BoundingBoxSize.getWidth(entity);

            // X and Z are equal on both sides, but Y is different top and bottom.
            final var x = Math.abs(packetTarget.getX());
            final var y = packetTarget.getY();
            final var z = Math.abs(packetTarget.getZ());

            if (x > width + FP_MARGIN) {
                fail("X offset of " + x + " is greater than width of " + width);
            } else if (z > width + FP_MARGIN) {
                fail("Z offset of " + z + " is greater than width of " + width);
            } else if (y > height + FP_MARGIN) {
                fail("Y offset of " + y + " is greater than height of " + height);
            } else if (y < -FP_MARGIN) {
                fail("Y offset of " + y + " is less than min offset of " + entity.getType().getName().getKey());
            }
        }
    }
}