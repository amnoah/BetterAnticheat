package better.anticheat.core.player.tracker.impl;

import better.anticheat.core.player.Player;
import better.anticheat.core.player.tracker.Tracker;
import better.anticheat.core.player.tracker.impl.confirmation.ConfirmationTracker;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.attribute.Attribute;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClientStatus;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateHealth;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import wtf.spare.sparej.xstate.manystate.BooleanManyState;
import wtf.spare.sparej.xstate.manystate.DoubleManyState;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Getter
@Slf4j
public class PlayerStatusTracker extends Tracker {
    private final BooleanManyState isDead = new BooleanManyState(10);
    private final ConfirmationTracker confirmationTracker;
    private final Map<Attribute, DoubleManyState> attributes = new HashMap<>();

    public PlayerStatusTracker(final Player player, final ConfirmationTracker confirmationTracker) {
        super(player);
        this.confirmationTracker = confirmationTracker;

        // Default attribute values.
        final var entityInteractionRange = attributes.computeIfAbsent(Attributes.ENTITY_INTERACTION_RANGE, (ignored) -> new DoubleManyState(10));
        final var blockInteractionRange = attributes.computeIfAbsent(Attributes.BLOCK_INTERACTION_RANGE, (ignored) -> new DoubleManyState(10));
        final var scale = attributes.computeIfAbsent(Attributes.SCALE, (ignored) -> new DoubleManyState(10));

        entityInteractionRange.addNew(3.0);
        blockInteractionRange.addNew(5.0);
        scale.addNew(1.0);

        /*
        entityInteractionRange.flushOld();
        blockInteractionRange.flushOld();
        scale.flushOld();
         */
    }

    /*
     * Packet handling.
     */

    @Override
    public void handlePacketPlaySend(final PacketPlaySendEvent event) {
        switch (event.getPacketType()) {
            case PacketType.Play.Server.UPDATE_HEALTH -> {
                final var wrapper = new WrapperPlayServerUpdateHealth(event);

                confirmationTracker
                        .confirm()
                        .onBegin(() -> {
                            isDead.addNew(wrapper.getHealth() == 0);
                            log.debug("added to {} due to health", wrapper.getHealth() == 0);
                        })
                        .onAfterConfirm(() -> {
                            isDead.flushOld();
                            log.debug("flushed to {} due to health", wrapper.getHealth() == 0);
                        });
            }

            case DEATH_COMBAT_EVENT -> confirmationTracker
                    .confirm()
                    .onBegin(() -> {
                        isDead.addNew(true);
                        log.debug("added to true due to death");
                    })
                    .onAfterConfirm(() -> {
                        isDead.flushOld();
                        log.debug("flushed to true due to death");
                    });

            case UPDATE_ATTRIBUTES -> {
                final var wrapper = new WrapperPlayServerUpdateAttributes(event);
                if (wrapper.getEntityId() != player.getUser().getEntityId()) return;

                final var confirmationState = confirmationTracker.confirm();

                for (final var property : wrapper.getProperties()) {
                    confirmationState.onBegin(() -> {
                        final var state = attributes.computeIfAbsent(property.getAttribute(), k -> new DoubleManyState(10));
                        state.addNew(property.getValue());
                        log.debug("added to {} due to {}", property.getValue(), property.getAttribute());
                    }).onAfterConfirm(() -> {
                        final var state = attributes.get(property.getAttribute());
                        if (state != null) {
                            state.flushOld();
                            log.debug("flushed to {} due to {}", property.getValue(), property.getAttribute());
                        }
                    });
                }
            }
        }
    }

    public double[] getMostLikelyPoses() {
        if (!attributes.containsKey(Attributes.SCALE)) log.error("No scale attribute, attrs: {}", attributes);
        final var scale = attributes.get(Attributes.SCALE).getCurrent();

        // Standing, sneaking then Elytra
        return new double[]{1.62 * scale, 1.27 * scale, 0.4 * scale};
    }

    public void handlePacketPlayReceive(final PacketPlayReceiveEvent event) {
        switch (event.getPacketType()) {
            case CLIENT_STATUS -> {
                final var wrapper = new WrapperPlayClientClientStatus(event);

                if (Objects.requireNonNull(wrapper.getAction()) == WrapperPlayClientClientStatus.Action.PERFORM_RESPAWN) {
                    isDead.addNew(false);
                    // Once client says alive, flush ALL the deads, as client state is all that matters.
                    // This is done to prevent disabler exploits where you selectively delay the response to certain packets.
                    for (int i = 0; i < 4; i++) {
                        isDead.flushOld();
                    }
                    log.debug("added and flushed to false due to respawn");
                }
            }
        }
    }
}