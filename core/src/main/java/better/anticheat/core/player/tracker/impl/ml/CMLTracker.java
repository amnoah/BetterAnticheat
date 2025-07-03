package better.anticheat.core.player.tracker.impl.ml;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.player.Player;
import better.anticheat.core.player.tracker.Tracker;
import better.anticheat.core.util.EntityMath;
import better.anticheat.core.util.MathUtil;
import better.anticheat.core.util.ml.ModelConfig;
import better.anticheat.core.util.type.fastlist.ArrayDoubleEvictingList;
import better.anticheat.core.util.type.fastlist.ord.OrderedArrayDoubleEvictingList;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

@Slf4j
@Getter
@Setter
public class CMLTracker extends Tracker {
    public static final Object MODEL_LOCK = new Object();

    public CMLTracker(Player player) {
        super(player);
        // Do this once per player because config reloads.
        this.expectedModels = BetterAnticheat.getInstance().getModelConfigs();
    }

    private final Map<String, ModelConfig> expectedModels;
    private final ArrayList<MLCheck> internalChecks = new ArrayList<>();
    private final ArrayList<double[][]> recording = new ArrayList<>();
    private final OrderedArrayDoubleEvictingList previousYaws = new OrderedArrayDoubleEvictingList(5);
    private final OrderedArrayDoubleEvictingList previousYawOffsets = new OrderedArrayDoubleEvictingList(5);
    private final OrderedArrayDoubleEvictingList previousEnhancedYawOffsets = new OrderedArrayDoubleEvictingList(5);
    private int lastEntityId;
    private boolean recordingNow = false;

    public void onPlayerInit() {
        this.expectedModels.forEach((name, modelConfig) -> this.internalChecks.add(new MLCheck(getPlayer(), modelConfig)));
        this.internalChecks.forEach(this.getPlayer().getChecks()::addLast);
    }

    @Override
    public void handlePacketPlayReceive(final PacketPlayReceiveEvent event) {
        switch (event.getPacketType()) {
            case INTERACT_ENTITY -> {
                // Handle checks, and recording.
                final var wrapper = new WrapperPlayClientInteractEntity(event);

                final var switchedIds = wrapper.getEntityId() != this.lastEntityId;

                if (switchedIds) {
                    this.lastEntityId = wrapper.getEntityId();
                }

                if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK || switchedIds) return;

                final var recordingEntry = new double[][]{
                        Arrays.copyOf(previousYaws.getArray(), previousYaws.getArray().length),
                        Arrays.copyOf(previousYawOffsets.getArray(), previousYawOffsets.getArray().length),
                        Arrays.copyOf(previousEnhancedYawOffsets.getArray(), previousEnhancedYawOffsets.getArray().length)
                };

                final var targetTracker = getPlayer().getEntityTracker();
                final var target = targetTracker.getEntities().get(lastEntityId);

                if (target != null && target.getHeight() >= 1.5f) {
                    for (final var internalCheck : this.internalChecks) {
                        internalCheck.handle(recordingEntry);
                    }
                }

                if (!recordingNow) return;

                this.recording.add(recordingEntry);
            }

            case PLAYER_FLYING, PLAYER_POSITION, PLAYER_ROTATION, PLAYER_POSITION_AND_ROTATION -> {
                // Track target and yaws.
                final var targetTracker = getPlayer().getEntityTracker();
                final var target = targetTracker.getEntities().get(lastEntityId);

                if (target == null || target.getHeight() < 1.5f) {
                    return;
                }

                final var targetBox = target.getRootState().getBb();

                final var targetCentre = new Vector3d(targetBox.posX(), targetBox.posY(), targetBox.posZ());

                final var position = getPlayer().getPositionTracker();
                final var rots = getPlayer().getRotationTracker();
                final var player = new Vector3d(position.getX(), position.getY(), position.getZ());
                final double[] offsets = EntityMath.getOffsetFromLocation(player, targetCentre, rots.getYaw(), rots.getPitch());

                this.previousYawOffsets.push(offsets[0]);
                this.previousYaws.push(rots.getDeltaYaw());
                this.previousEnhancedYawOffsets.push(offsets[0] - rots.getDeltaYaw());
            }
        }
    }

    @Override
    public void handlePacketPlaySend(final PacketPlaySendEvent event) {

    }

    public static class MLCheck extends Check {
        private final ModelConfig modelConfig;
        private final ArrayDoubleEvictingList history;
        private final DecimalFormat df = new DecimalFormat("#.####");

        public MLCheck(final Player player, final ModelConfig modelConfig) {
            super();

            this.modelConfig = modelConfig;
            this.player = player;

            super.load(this.modelConfig.getConfigSection());

            this.setName("ML Aim: " + modelConfig.getDisplayName());

            if (!isEnabled()) {
                log.info("[BetterAnticheat] [ML] {} is currently disabled", getName());
            }

            this.history = new ArrayDoubleEvictingList(modelConfig.getSamples());
        }

        public void handle(final double[][] data) {
            if (!isEnabled()) return;

            synchronized (MODEL_LOCK) {
                this.history.push(this.modelConfig.getClassifierFunction().apply(data));
            }

            if (!this.history.isFull()) {
                log.info("[BetterAnticheat] [ML] {} still recording {} as {}", player.getUser().getName(), getName(), this.history.getCount());
                return;
            }

            final var avg = MathUtil.getAverage(this.history.getArray());

            if (avg < modelConfig.getThreshold()) {
                log.info("[BetterAnticheat] [ML] {} passed {} as {}", player.getUser().getName(), getName(), df.format(avg));
                return;
            }

            fail("ML-" + history);
        }
    }
}
