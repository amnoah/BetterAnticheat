package better.anticheat.core.player.tracker.impl.ml;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.player.Player;
import better.anticheat.core.player.tracker.Tracker;
import better.anticheat.core.util.MathUtil;
import better.anticheat.core.util.entity.EntityMath;
import better.anticheat.core.util.ml.ModelConfig;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import wtf.spare.sparej.fastlist.FastObjectArrayList;
import wtf.spare.sparej.fastlist.evicting.ord.OrderedArrayDoubleEvictingList;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    private final List<MLCheck> internalChecks = new FastObjectArrayList<>();
    private final ArrayList<double[][]> recording = new ArrayList<>();
    private final OrderedArrayDoubleEvictingList previousYaws = new OrderedArrayDoubleEvictingList(10);
    private final OrderedArrayDoubleEvictingList previousYawOffsets = new OrderedArrayDoubleEvictingList(10);
    private final OrderedArrayDoubleEvictingList previousEnhancedYawOffsets = new OrderedArrayDoubleEvictingList(10);
    private int lastEntityId;
    private double averageScore;
    private boolean recordingNow = false;
    private int ticksSinceLastAttack = 0;
    private boolean firstHitProcessed = false;

    public void onPlayerInit() {
        this.expectedModels
                .forEach((name, modelConfig) -> this.internalChecks.add(new MLCheck(getPlayer(), modelConfig)));
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
                } else {
                    log.trace("Did not switch entity ids: {} == {}", wrapper.getEntityId(), this.lastEntityId);
                }

                if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK || switchedIds)
                    return;
                this.ticksSinceLastAttack = 0;

                if (!this.firstHitProcessed) {
                    this.firstHitProcessed = true;
                    final var plugin = player.getPlugin();
                    if (!this.recordingNow &&
                            plugin.isAutoRecordEnabled() &&
                            plugin.getDataBridge().hasPermission(getPlayer().getUser(),
                                    plugin.getAutoRecordPermission())) {
                        this.recordingNow = true;
                        log.info("Auto-recording enabled for {}", getPlayer().getUser().getName());
                    }
                }

                final var recordingEntry = new double[][] {
                        Arrays.copyOf(previousYaws.getArray(), previousYaws.getArray().length),
                        Arrays.copyOf(previousYawOffsets.getArray(), previousYawOffsets.getArray().length),
                        Arrays.copyOf(previousEnhancedYawOffsets.getArray(),
                                previousEnhancedYawOffsets.getArray().length)
                };

                final var targetTracker = getPlayer().getEntityTracker();
                final var target = targetTracker.getEntities().get(lastEntityId);

                if (target != null && target.getHeight() >= 1.5f) {
                    for (final var internalCheck : this.internalChecks) {
                        internalCheck.handle(recordingEntry);
                    }
                }

                if (!this.recordingNow)
                    return;

                this.recording.add(recordingEntry);
            }

            case PLAYER_FLYING, PLAYER_POSITION, PLAYER_ROTATION, PLAYER_POSITION_AND_ROTATION -> {
                // Track target and yaws.
                final var targetTracker = getPlayer().getEntityTracker();
                final var target = targetTracker.getEntities().get(lastEntityId);

                if (target == null || target.getHeight() < 1.5f) {
                    log.debug("No target: {}", lastEntityId);
                    return;
                }

                final var targetBox = target.getRootState().getBb();

                final var targetCentre = new Vector3d(targetBox.posX(), targetBox.posY(), targetBox.posZ());

                final var position = getPlayer().getPositionTracker();
                final var rots = getPlayer().getRotationTracker();
                final var player = new Vector3d(position.getX(), position.getY(), position.getZ());
                final double[] offsets = EntityMath.getOffsetFromLocation(player, targetCentre, rots.getYaw(),
                        rots.getPitch());

                this.previousYawOffsets.push(offsets[0]);
                this.previousYaws.push(rots.getDeltaYaw());
                this.previousEnhancedYawOffsets.push(offsets[0] - rots.getDeltaYaw());
            }

            case CLIENT_TICK_END -> {
                this.ticksSinceLastAttack++;
                double totalSum = 0.0;
                int totalCount = 0;

                // Calculate overall average from all MLCheck history arrays, and use this to
                // determine if we should be mitigating now.
                for (final var mlCheck : getInternalChecks()) {
                    if (!mlCheck.getHistory().isFull())
                        continue;

                    final double[] historyArray = mlCheck.getHistory().getArray();
                    for (final double value : historyArray) {
                        totalSum += value;
                        totalCount++;
                    }
                }

                if (totalCount == 0)
                    return;

                final double overallAverage = totalSum / totalCount;

                this.averageScore = overallAverage;
            }
        }
    }

    @Override
    public void handlePacketPlaySend(final PacketPlaySendEvent event) {

    }

    public static class MLCheck extends Check {
        private final ModelConfig modelConfig;
        @Getter
        private final OrderedArrayDoubleEvictingList history;

        public MLCheck(final Player player, final ModelConfig modelConfig) {
            super(BetterAnticheat.getInstance(), "ML Aim: " + modelConfig.getDisplayName(), "", "", false);

            this.modelConfig = modelConfig;
            this.player = player;

            super.load(this.modelConfig.getConfigSection());

            if (!isEnabled()) {
                log.debug("[BetterAnticheat] [ML] {} is currently disabled", getName());
            }

            this.history = new OrderedArrayDoubleEvictingList(modelConfig.getSamples());
        }

        public void handle(final double[][] data) {
            if (!isEnabled())
                return;

            synchronized (MODEL_LOCK) {
                this.history.push(this.modelConfig.getClassifierFunction().apply(data));
            }

            if (!this.history.isFull()) {
                log.debug("[BetterAnticheat] [ML] {} still recording {} as {}", player.getUser().getName(), getName(),
                        this.history.getCount());
                return;
            }

            final var avg = MathUtil.getAverage(this.history.getArray());

            // We use `Math.round(modelConfig.getThreshold() - 0.5)` instead of
            // `Math.floor(modelConfig.getThreshold())`, but they will both return the same
            // integer result if all numbers are divisible by 0.5, but they are not.
            final var extendedCheck = MathUtil.getConsecutiveAboveX(modelConfig.getAlertThreshold() - 0.5,
                    this.history.getArray()) > Math.max(Math.min(3, modelConfig.getSamples() / 2),
                            Math.round(modelConfig.getAlertThreshold() - 0.5))
                    && avg >= modelConfig.getAlertThreshold();
            final var basicCheck = avg >= modelConfig.getAlertThreshold() + 0.5;

            if (basicCheck || extendedCheck) {
                fail("ML " + MathUtil.DF_FOUR_PLACES.format(avg) + " via " + this.history);
                this.player.getMitigationTracker().getMitigationTicks().increment(15);
                return;
            }

            final var mitigationExtendedCheck = MathUtil.getConsecutiveAboveX(
                    modelConfig.getMitigationThreshold() - 0.5,
                    this.history.getArray()) > Math.max(Math.min(3, modelConfig.getSamples() / 2),
                            Math.round(modelConfig.getMitigationThreshold() - 0.5))
                    && avg >= modelConfig.getMitigationThreshold();
            final var mitigationBasicCheck = avg >= modelConfig.getMitigationThreshold() + 0.5;

            if (mitigationExtendedCheck || mitigationBasicCheck) {
                fail("ML " + MathUtil.DF_FOUR_PLACES.format(avg) + " via " + this.history, true);
                this.player.getMitigationTracker().getMitigationTicks().increment(modelConfig.getMitigationTicks());
                return;
            }

            log.debug("[BetterAnticheat] [ML] {} passed {} as {}", player.getUser().getName(), getName(),
                    MathUtil.DF_FOUR_PLACES.format(avg));
        }
    }
}
