package better.anticheat.core.player.tracker.impl;

import better.anticheat.core.player.Player;
import better.anticheat.core.player.tracker.Tracker;
import better.anticheat.core.player.tracker.impl.potion.Potion;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.attribute.Attribute;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.potion.PotionType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import wtf.spare.sparej.xstate.manystate.ObjectManyState;

import java.util.Map;

public class AttributeTracker extends Tracker {

    private final Map<Attribute, ObjectManyState<Double>> attributeMap = new Object2ObjectArrayMap<>();

    public AttributeTracker(Player player) {
        super(player);
    }

    @Override
    public void handlePacketPlaySend(PacketPlaySendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.UPDATE_ATTRIBUTES) return;
        WrapperPlayServerUpdateAttributes wrapper = new WrapperPlayServerUpdateAttributes(event);
        if (wrapper.getEntityId() != player.getUser().getEntityId()) return;

        var confirmation = player.getConfirmationTracker().confirm();

        for (WrapperPlayServerUpdateAttributes.Property property : wrapper.getProperties()) {
            confirmation.onBegin(() -> attributeMap.get(property.getAttribute()).addNew(property.calcValue()));

        }
    }
}
