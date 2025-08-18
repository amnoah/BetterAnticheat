package better.anticheat.core.player.tracker.impl.potion;

import better.anticheat.core.player.Player;
import better.anticheat.core.player.tracker.Tracker;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.potion.PotionType;
import com.github.retrooper.packetevents.protocol.potion.PotionTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRemoveEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import wtf.spare.sparej.incrementer.IntIncrementer;
import wtf.spare.sparej.xstate.manystate.ObjectManyState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PotionTracker extends Tracker {

    private static final Potion EMPTY_POTION = new Potion(0, -1);

    private final Map<PotionType, ObjectManyState<Potion>> potionMap = new Object2ObjectArrayMap<>();

    public PotionTracker(Player player) {
        super(player);

        for (PotionType potionType : PotionTypes.values()) {
            ObjectManyState<Potion> newManyState = new ObjectManyState<>();
            newManyState.addNew(EMPTY_POTION);
            potionMap.put(potionType, newManyState);
        }
    }

    public ObjectManyState<Potion> getPotion(PotionType potionType) {
        return potionMap.get(potionType);
    }

    @Override
    public void handlePacketPlayReceive(PacketPlayReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.CLIENT_TICK_END) return;

        // Prevent concurrent modifications.
        List<PotionType> removals = new ArrayList<>();

        /*
         * TODO: Figure out if this is necessary.
         * If I'm totally honest, I don't know whether the server or client has definitive control of when a potion
         * has ended. I'd assume based on
        for (PotionType key : potionMap.keySet()) {
            ObjectManyState<Potion> potions = potionMap.get(key);

            if (potions.getCurrent().getActiveCount() != null) {
                potions.getCurrent().getActiveCount().increment();
                if (!potions.getCurrent().isActive()) removals.add(key);
                continue;
            }

            if (potions.getOld() != null && potions.getOld().getActiveCount() != null) {
                potions.getOld().getActiveCount().increment();
                if (!potions.getOld().isActive()) potions.flushOld();
            }
        }

        for (PotionType potionType : removals) potionMap.remove(potionType);
         */
    }

    @Override
    public void handlePacketPlaySend(PacketPlaySendEvent event) {
        switch (event.getPacketType()) {
            case ENTITY_EFFECT -> {
                WrapperPlayServerEntityEffect wrapper = new WrapperPlayServerEntityEffect(event);
                if (wrapper.getEntityId() != player.getUser().getEntityId()) return;

                var confirmation = player.getConfirmationTracker().confirm();

                Potion potion = new Potion(wrapper.getEffectAmplifier(), wrapper.getEffectDurationTicks());
                confirmation.onBegin(() -> {
                    potionMap.get(wrapper.getPotionType()).addNew(potion);
                });
                confirmation.onAfterConfirm(() -> {
                    potionMap.get(wrapper.getPotionType()).flushOld();
                    potion.setActiveCount(new IntIncrementer(0));
                });
            }
            case REMOVE_ENTITY_EFFECT -> {
                WrapperPlayServerRemoveEntityEffect wrapper = new WrapperPlayServerRemoveEntityEffect(event);
                if (wrapper.getEntityId() != player.getUser().getEntityId()) return;

                event.getUser().sendMessage("Removed potion!");

                var confirmation = player.getConfirmationTracker().confirm();

                /*
                 * When this packet is beginning to be received, we should begin to transition to an empty potion state
                 * for this effect. By the time it has been officially received, we should remove the old potion that
                 * was in there.
                 */
                confirmation.onBegin(() -> potionMap.get(wrapper.getPotionType()).addNew(EMPTY_POTION));
                confirmation.onAfterConfirm(() -> potionMap.get(wrapper.getPotionType()).flushOld());
            }
        }
    }
}
