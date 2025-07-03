package better.anticheat.core.player;

import better.anticheat.core.DataBridge;
import better.anticheat.core.check.Check;
import better.anticheat.core.check.CheckManager;
import better.anticheat.core.player.tracker.impl.PositionTracker;
import better.anticheat.core.player.tracker.impl.RotationTracker;
import better.anticheat.core.player.tracker.impl.confirmation.ConfirmationTracker;
import better.anticheat.core.player.tracker.impl.entity.EntityTracker;
import better.anticheat.core.player.tracker.impl.ml.CMLTracker;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.player.User;
import lombok.Getter;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Player implements Closeable {

    @Getter
    private final User user;
    @Getter
    private final PositionTracker positionTracker;
    @Getter
    private final RotationTracker rotationTracker;
    @Getter
    private final ConfirmationTracker confirmationTracker;
    @Getter
    private final EntityTracker entityTracker;
    @Getter
    private final CMLTracker cmlTracker;

    @Getter
    private List<Check> checks = null;

    private final List<Closeable> closeables = new ArrayList<>();

    public Player(final User user, final DataBridge dataBridge) {
        this.user = user;
        this.positionTracker = new PositionTracker(this);
        this.rotationTracker = new RotationTracker(this);
        this.confirmationTracker = new ConfirmationTracker(this);
        this.entityTracker = new EntityTracker(this, this.confirmationTracker, this.positionTracker, dataBridge);
        this.cmlTracker = new CMLTracker(this);
        load();

        closeables.add(dataBridge.registerTickListener(user, this.confirmationTracker::sendTickKeepaliveNoFlush));
    }

    /*
     * Handle packets.
     */
    public void handleReceivePacket(PacketPlayReceiveEvent event) {
        this.positionTracker.handlePacketPlayReceive(event);
        this.rotationTracker.handlePacketPlayReceive(event);
        this.confirmationTracker.handlePacketPlayReceive(event);
        this.entityTracker.handlePacketPlayReceive(event);
        this.cmlTracker.handlePacketPlayReceive(event);

        for (Check check : this.checks) {
            if (!check.isEnabled()) continue;
            check.handleReceivePlayPacket(event);
        }
    }

    public void handleSendPacket(PacketPlaySendEvent event) {
        this.positionTracker.handlePacketPlaySend(event);
        this.rotationTracker.handlePacketPlaySend(event);
        this.confirmationTracker.handlePacketPlaySend(event);
        this.entityTracker.handlePacketPlaySend(event);
        this.cmlTracker.handlePacketPlaySend(event);

        for (Check check : this.checks) {
            if (!check.isEnabled()) continue;
            check.handleSendPlayPacket(event);
        }
    }

    /*
     *
     */

    public void load() {
        if (checks == null) checks = CheckManager.getChecks(this);
        else for (Check check : checks) check.load();

        // Load CML generated checks.
        this.cmlTracker.onPlayerInit();
    }

    @Override
    public void close() throws IOException {
        for (final var closeable : this.closeables) {
            if (closeable == null) continue;
            closeable.close();
        }
    }
}