package better.anticheat.core.player.tracker.impl.entity;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.DataBridge;
import better.anticheat.core.player.Player;
import better.anticheat.core.player.tracker.Tracker;
import better.anticheat.core.player.tracker.impl.PositionTracker;
import better.anticheat.core.player.tracker.impl.confirmation.ConfirmationTracker;
import better.anticheat.core.player.tracker.impl.entity.type.EntityData;
import better.anticheat.core.player.tracker.impl.entity.type.EntityTrackerState;
import better.anticheat.core.player.tracker.impl.entity.type.SplitEntityUpdate;
import better.anticheat.core.util.MathUtil;
import better.anticheat.core.util.entity.BoundingBoxSize;
import better.anticheat.core.util.type.entity.AxisAlignedBB;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanFunction;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import wtf.spare.sparej.fastlist.FastObjectArrayList;
import wtf.spare.sparej.incrementer.LongIncrementer;
import wtf.spare.sparej.xstate.bistate.DoubleBiState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class EntityTracker extends Tracker {
    public EntityTracker(final Player player, final ConfirmationTracker confirmationTracker,
                         final PositionTracker positionTracker, final DataBridge<?> bridge) {
        super(player);
        this.confirmationTracker = confirmationTracker;
        this.positionTracker = positionTracker;
        this.bridge = bridge;
        this.supportsTickEnd = getPlayer().getUser().getClientVersion()
                .isNewerThanOrEquals(ClientVersion.V_1_21_2);
        this.plugin = player.getPlugin();
    }

    // General data
    private final boolean supportsTickEnd;
    private final BetterAnticheat plugin;

    // Persistent data
    @Getter
    private final Int2ObjectMap<EntityData> entities = new Int2ObjectRBTreeMap<>();
    @Getter
    private final ArrayDeque<SplitEntityUpdate> awaitingUpdates = new ArrayDeque<>();

    // Session data
    private final ConfirmationTracker confirmationTracker;
    private final PositionTracker positionTracker;
    private final DataBridge<?> bridge;

    // Temporary buffers to avoid allocating each time
    private final ObjectArrayList<EntityTrackerState> stateBuffer = new ObjectArrayList<>(30);
    private final FastObjectArrayList<EntityTrackerState> stateBuffer2 = new FastObjectArrayList<>(30);
    private final Int2ObjectOpenHashMap<EntityTrackerState> treeShakeMap = new Int2ObjectOpenHashMap<>();

    private final LongIncrementer fullSizeTreeShakeTimer = new LongIncrementer();

    private int totalMovesThisTick = 0;
    private boolean tickEndSinceFlying = false;

    @Override
    public void handlePacketPlaySend(final PacketPlaySendEvent event) {
        switch (event.getPacketType()) {
            case SPAWN_ENTITY: {
                final var wrapper = new WrapperPlayServerSpawnEntity(event);
                this.createEntity(wrapper.getEntityId(), wrapper.getPosition(), wrapper.getEntityType());
                break;
            }
            case SPAWN_LIVING_ENTITY: {
                final var wrapper = new WrapperPlayServerSpawnLivingEntity(event);
                this.createEntity(wrapper.getEntityId(), wrapper.getPosition(), wrapper.getEntityType());
                break;
            }
            case SPAWN_PLAYER: {
                final var wrapper = new WrapperPlayServerSpawnPlayer(event);
                this.createEntity(wrapper.getEntityId(), wrapper.getPosition(), EntityTypes.PLAYER);
                break;
            }
            case ENTITY_RELATIVE_MOVE: {
                final var wrapper = new WrapperPlayServerEntityRelativeMove(event);
                this.relMove(wrapper.getEntityId(), wrapper.getDeltaX(), wrapper.getDeltaY(), wrapper.getDeltaZ());
                break;
            }
            case ENTITY_RELATIVE_MOVE_AND_ROTATION: {
                final var wrapper = new WrapperPlayServerEntityRelativeMoveAndRotation(event);
                this.relMove(wrapper.getEntityId(), wrapper.getDeltaX(), wrapper.getDeltaY(), wrapper.getDeltaZ());
                break;
            }
            case ENTITY_TELEPORT: {
                final var wrapper = new WrapperPlayServerEntityTeleport(event);
                this.teleport(wrapper.getEntityId(), wrapper.getPosition().getX(), wrapper.getPosition().getY(),
                        wrapper.getPosition().getZ());
                break;
            }
            case ENTITY_METADATA: {
                final var wrapper = new WrapperPlayServerEntityMetadata(event);
                this.handleMetadata(wrapper);
                break;
            }
            case DESTROY_ENTITIES: {
                final var wrapper = new WrapperPlayServerDestroyEntities(event);
                this.destroyEntities(wrapper.getEntityIds());
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void handlePacketPlayReceive(final PacketPlayReceiveEvent event) {
        // WrapperPlayClientPlayerFlying is the base class for position, look, and position_look
        final var type = event.getPacketType();

        if (WrapperPlayClientPlayerFlying.isFlying(type)) {
            // Always run living update on flying
            this.tickEndSinceFlying = false;
            this.onLivingUpdate();

            // If client doesn't support tick end, emulate "end of tick" once per flying sequence
            if (supportsTickEnd) {
                return;
            }

            for (EntityData data : entities.values()) {
                if (data.getTicksSinceMove().get() >= 0)
                    data.getTicksSinceMove().increment();
            }

            if (!this.tickEndSinceFlying) {
                this.tickEndSinceFlying = true;
                return;
            }
        }

        if (supportsTickEnd && type == PacketType.Play.Client.CLIENT_TICK_END) {
            /*
             * This is a value that can be important for some aim checks.
             * After a start confirmation, it will remain as -1 until the after confirmation. Then, it will begin to tick.
             * I'd prefer for this to happen at the end of the tick rather than on the living update.
             */
            for (EntityData data : entities.values()) {
                if (data.getTicksSinceMove().get() >= 0)
                    data.getTicksSinceMove().increment();
            }

            if (!this.tickEndSinceFlying) {
                this.tickEndSinceFlying = true;
                return;
            }

            this.onLivingUpdate();
        }
    }

    public void createEntity(final int entityId, final @NotNull Vector3d position, final @NotNull EntityType type) {
        this.createEntity(entityId, position, type, 0);
    }

    public void destroyEntities(final int[] entityId) {
        // Not sure if I should be using after confirmation here
        this.confirmationTracker.confirm().onBegin(() -> {
            for (final var id : entityId) {
                entities.remove(id);
            }
        });
    }

    /**
     * Creates an entity
     */
    public void createEntity(final int entityId, final @NotNull Vector3d position, final @NotNull EntityType type,
                             final int retries) {
        if (this.entities.containsKey(entityId)) {
            // Prevent performance issues.
            if (retries < 10) {
                bridge.runTaskLater(getPlayer().getUser(), () -> createEntity(entityId, position, type, retries + 1),
                        5);
            }
            return;
        }

        final var entityData = new EntityData(entityId, type);
        entityData.setWidth(BoundingBoxSize.getWidth(entityData));
        entityData.setHeight(BoundingBoxSize.getHeight(entityData));

        entityData.setServerPosX(new DoubleBiState(position.getX()));
        entityData.setServerPosY(new DoubleBiState(position.getY()));
        entityData.setServerPosZ(new DoubleBiState(position.getZ()));

        final var root = new EntityTrackerState(null, entityData,
                createEntityBox(entityData.getWidth(), entityData.getHeight(), position),
                position.getZ(), position.getY(), position.getX());

        entityData.setRootState(root);
        this.entities.put(entityId, entityData);
    }

    /**
     * Handles entity metadata updates from the server.
     * Processes only the metadata of type ENTITY_POSE and updates the entity's poses accordingly.
     * Utilizes a confirmation mechanism to ensure consistency before and after metadata application.
     *
     * @param wrapper the metadata packet containing entity ID and metadata entries
     */
    public void handleMetadata(final WrapperPlayServerEntityMetadata wrapper) {
        final var eid = wrapper.getEntityId();
        final var entity = this.entities.get(eid);
        if (entity == null) {
            return;
        }

        for (final var entityMetadatum : wrapper.getEntityMetadata()) {
            if (entityMetadatum.getType() == EntityDataTypes.ENTITY_POSE) {
                final var confirmation = confirmationTracker.confirm();

                confirmation.onBegin(() -> entity.getPoses().addNew((EntityPose) entityMetadatum.getValue()));
                confirmation.onAfterConfirm(() -> entity.getPoses().flushOld());
            }
        }
    }

    /**
     * Handle relative moves for an entity
     */
    public void relMove(final int entityId, final double deltaX, final double deltaY, final double deltaZ) {
        var confirmation = confirmationTracker.confirm();
        if (!this.entities.containsKey(entityId)) {
            confirmation.onAfterConfirm(() -> relMove(entityId, deltaX, deltaY, deltaZ));
            return;
        }

        final var entity = this.entities.get(entityId);
        final var newState = new FastObjectArrayList<EntityTrackerState>();

        confirmation.onBegin(() -> {
            log.debug("Started relative move for entity {} at {}, {}, {}", entityId,
                    entity.getServerPosX().getCurrent(), entity.getServerPosY().getCurrent(),
                    entity.getServerPosZ().getCurrent());
            final var originalRoot = entity.getRootState().cloneWithoutChildren();

            entity.getServerPosX().addNew(entity.getServerPosX().getCurrent() + deltaX);
            entity.getServerPosY().addNew(entity.getServerPosY().getCurrent() + deltaY);
            entity.getServerPosZ().addNew(entity.getServerPosZ().getCurrent() + deltaZ);

            stateBuffer.clear();
            stateBuffer2.clear();
            recursivelyRelMovePre(entity.getRootState(), 0);
            newState.addAll(stateBuffer);
            stateBuffer.clear();

            this.awaitingUpdates.add(new SplitEntityUpdate(entity, originalRoot, entity.getServerPosX().getCurrent(),
                    entity.getServerPosY().getCurrent(), entity.getServerPosZ().getCurrent()));

            entity.getTicksSinceMove().set(-1);
        });

        confirmation.onAfterConfirm(() -> {
            log.debug("Completed relative move for entity {} at {}, {}, {}", entityId,
                    entity.getServerPosX().getCurrent(), entity.getServerPosY().getCurrent(),
                    entity.getServerPosZ().getCurrent());

            // Update all then shake tree
            entity.getServerPosX().flushOld();
            entity.getServerPosY().flushOld();
            entity.getServerPosZ().flushOld();

            final var theDelta = MathUtil.hypot(deltaX, deltaY, deltaZ);
            var removedCnt = 0;

            for (final var neww : newState) {
                final var dist = neww.distance(neww.getParent());
                if (dist < neww.getPotentialOffsetAmount() || dist <= theDelta) {
                    entity.getTreeSize().decrement(neww.getChildren().size() + 1);
                    neww.getParent().getChildren().removeExact(neww);
                    removedCnt++;
                } else {
                    for (final var child : neww.getChildren()) {
                        setPositionAndRotation2(child, entity.getServerPosX().getCurrent(),
                                entity.getServerPosY().getCurrent(), entity.getServerPosY().getCurrent());
                    }
                    setPositionAndRotation2(neww, entity.getServerPosX().getCurrent(),
                            entity.getServerPosY().getCurrent(), entity.getServerPosY().getCurrent());
                }
            }

            for (final var edata : this.awaitingUpdates.toArray(new SplitEntityUpdate[0])) {
                if (edata.getData().equals(entity)) {
                    removedCnt -= edata.getFlyings().get() == 0 ? 0 : (edata.getFlyings().get() - 1);
                    this.awaitingUpdates.remove(edata);
                }
            }

            if ((newState.size() - removedCnt) > 0) {
                shakeTree(entity);
            }

            entity.getTicksSinceMove().set(0);
        });
    }

    /**
     * handles a relmove packet on PRE tranny
     */
    public void recursivelyRelMovePre(final EntityTrackerState state, final int depth) {
        if (stateBuffer2.indexOfExact(state) != -1)
            return;

        if (depth < 7) {
            // This + depth checking can remove very low chance branches.
            final boolean shouldClone = Math.abs(state.getPosX() - state.getData().getServerPosX().getCurrent()) > 0.005
                    || Math.abs(state.getPosY() - state.getData().getServerPosY().getCurrent()) > 0.005
                    || Math.abs(state.getPosZ() - state.getData().getServerPosZ().getCurrent()) > 0.005;

            if (shouldClone) {
                // Use fast awaiting powered tracking here instead if possible.
                if (this.plugin.isEntityTrackerFastAwaitingUpdate()
                        && state.getOtherPlayerMPPosRotationIncrements() <= 0) {
                    final var childDepth = depth + 1;
                    for (final var child : state.getChildren()) {
                        recursivelyRelMovePre(child, childDepth);
                    }
                    return;
                } else {
                    // Clone first - the children will be cloned themselves. This improves performance a little.
                    final var neww = state.newChild(state, false, false);

                    // Recursively run
                    final var childDepth = depth + 1;
                    for (final var child : state.getChildren()) {
                        stateBuffer2.add(child);
                        recursivelyRelMovePre(child, childDepth);
                    }

                    // Add the clone after doing recursion to avoid accidentally setting the pos on
                    // the old entity state.
                    state.getData().getTreeSize().increment(1);
                    state.getChildren().add(neww);

                    // Tick this on post instead.
                    this.stateBuffer.add(neww);
                }
            } else {
                final var childDepth = depth + 1;
                for (final var child : state.getChildren()) {
                    stateBuffer2.add(child);
                    recursivelyRelMovePre(child, childDepth);
                }
            }
        } else {
            // Do not add to state buf 2
            final var childDepth = depth + 1;
            for (final var child : state.getChildren()) {
                recursivelyRelMovePre(child, childDepth);
            }
        }

        // Set the data
        setPositionAndRotation2(state, state.getData().getServerPosX().getCurrent(),
                state.getData().getServerPosY().getCurrent(), state.getData().getServerPosZ().getCurrent());
    }

    public void teleport(final int entityId, final double x, final double y, final double z) {
        var confirmation = this.confirmationTracker.confirm();
        if (!this.entities.containsKey(entityId)) {
            confirmation.onBegin((a) -> teleport(entityId, x, y, z));
            return;
        }

        final var entity = this.entities.get(entityId);
        final var newState = new FastObjectArrayList<EntityTrackerState>();

        confirmation.onBegin((a) -> {
            log.debug("teleporting to {} {} {}", x, y, z);
            entity.getServerPosX().addNew(x);
            entity.getServerPosY().addNew(y);
            entity.getServerPosZ().addNew(z);

            stateBuffer.clear();
            recursivelyTeleportPre(entity.getRootState(), 0);
            newState.addAll(stateBuffer);
            stateBuffer.clear();

            entity.getTicksSinceMove().set(-1);
        });

        confirmation.onAfterConfirm((a) -> {
            log.debug("flushed to {} {} {} due to teleport, beginning tree update", entity.getServerPosX().getCurrent(),
                    entity.getServerPosY().getCurrent(), entity.getServerPosZ().getCurrent());

            // Update all then shake tree
            entity.getServerPosX().flushOld();
            entity.getServerPosY().flushOld();
            entity.getServerPosZ().flushOld();

            for (final var neww : newState) {
                for (final var child : neww.getChildren()) {
                    setPositionAndRotation2(child, entity.getServerPosX().getCurrent(),
                            entity.getServerPosY().getCurrent(), entity.getServerPosZ().getCurrent());
                }
                setPositionAndRotation2(neww, entity.getServerPosX().getCurrent(), entity.getServerPosY().getCurrent(),
                        entity.getServerPosZ().getCurrent());
            }

            // We just did a tp, we can prune some old branches. This will improve
            // performance.
            treeShrinkRecursive(entity.getRootState(), 0, 4);

            // Do a basic tree shake to remove duplicates
            shakeTree(entity);

            entity.getTicksSinceMove().set(0);
        });
    }

    /**
     * Living update
     */
    public void onLivingUpdate() {
        // Handle splits
        if (this.plugin.isEntityTrackerFastAwaitingUpdate()) {
            // This is an optimistic, lightweight estimation based split handling algorithm
            for (final var awaitingUpdate : this.awaitingUpdates) {
                if (awaitingUpdate.getFlyings().get() > 3) {
                    continue;
                }
                final var oldState = awaitingUpdate.getOldState();
                final int increments = 4 - awaitingUpdate.getFlyings().get();
                if (increments > 0) {
                    final double d0 = (awaitingUpdate.getX() - oldState.getPosX()) / increments;
                    final double d1 = (awaitingUpdate.getY() - oldState.getPosY()) / increments;
                    final double d2 = (awaitingUpdate.getZ() - oldState.getPosZ()) / increments;

                    oldState.addOffsetABS(d0, d1, d2);
                }
            }
        } else {
            for (final var awaitingUpdate : this.awaitingUpdates) {
                // Max of 3 updates.
                if (awaitingUpdate.getFlyings().increment() < 2 || awaitingUpdate.getFlyings().get() > 5) {
                    continue;
                }
                final var newUpdate = awaitingUpdate.getOldState().newChild(awaitingUpdate.getData().getRootState(),
                        false);
                setPositionAndRotation2(newUpdate, awaitingUpdate.getX(), awaitingUpdate.getY(),
                        awaitingUpdate.getZ());

                // Add the new child.
                awaitingUpdate.getData().getTreeSize().increment(newUpdate.getChildren().size() + 1);
                awaitingUpdate.getData().getRootState().getChildren().add(newUpdate);
            }
        }

        // Tick the entities
        for (final var value : this.entities.values()) {
            this.totalMovesThisTick = 0;
            final var cnt = onLivingUpdateRecursive(value.getRootState());

            // Tree shake if needed
            if ((float) cnt > (value.getTreeSize().get() / 4.0F)) {
                shakeTree(value);
                totalMovesThisTick += cnt;
            }
        }
    }

    /**
     * Shake a tree
     *
     * @param entityData
     */
    public synchronized void shakeTree(final @NotNull EntityData entityData) {
        try {
            if (entityData.getTreeSize().get() >= (this.plugin.isEntityTrackerFastAwaitingUpdate() ? 28 : 20)) {
                fullSizeTreeShakeTimer.increment();
            }

            if (fullSizeTreeShakeTimer.get() % 40.0 == 0.0) {
                // Get rid of not very useful data, and do emergency cleanup if >> 180
                final var treeSize = entityData.getTreeSize().get();
                final var maxDelta = treeSize > 90 ? 0.12 : treeSize > 60 ? 0.03 : treeSize > 32 ? 0.025 : 0.015;

                shakeTreeRecursive(entityData.getRootState(), (state) -> {
                    var statee = (EntityTrackerState) state;
                    var hashCode = statee.liteHashCode();
                    var remove = treeShakeMap.get(hashCode) == statee
                            || (statee.getParent() != null && statee.distance(statee.getParent()) < maxDelta);
                    if (!remove && !treeShakeMap.containsKey(hashCode)) {
                        treeShakeMap.put(hashCode, statee);
                    }

                    return remove;
                });
            } else {
                // Get rid of blatant duplicates
                shakeTreeRecursive(entityData.getRootState(), (state) -> {
                    var statee = (EntityTrackerState) state;
                    var hashCode = statee.hashCodePositionsAndIncrementsOnly();
                    var remove = treeShakeMap.get(hashCode) == statee;
                    if (!remove && !treeShakeMap.containsKey(hashCode)) {
                        treeShakeMap.put(hashCode, statee);
                    }

                    return remove;
                });
            }

            // do not run instantly, wait a little.
            if (positionTracker.getTicks() % 20 == 0.0 || fullSizeTreeShakeTimer.get() % 50.0 == 0.0) {
                // Run a special task on massively oversized trees
                // This is an emergency task when things get bad
                if (entityData.getTreeSize().get() > 120) {
                    treeShrinkRecursive(entityData.getRootState(), 0, 6);
                }
            }
        } finally {
            this.treeShakeMap.clear();
        }
    }

    /**
     * Create the bounding box for an entity
     */
    public @NotNull AxisAlignedBB createEntityBox(final float width, final float height,
                                                  final @NotNull Vector3d vector3d) {
        return new AxisAlignedBB(vector3d.getX(), vector3d.getY(), vector3d.getZ(), width, height);
    }

    public @NotNull List<EntityData> getEntitiesWithinAABBExcludingEntity(final int entityId,
                                                                          final @NotNull AxisAlignedBB bb) {
        final var entities = new ArrayList<EntityData>();
        for (final var value : this.entities.values()) {
            if (value.getId() == entityId) {
                continue;
            }
            if (value.getRootState().getBb().intersectsWith(bb)) {
                entities.add(value);
            }
        }

        return entities;
    }

    /**
     * handles a teleport packet on PRE tranny
     */
    public void recursivelyTeleportPre(final EntityTrackerState state, final int depth) {
        // Heavy sane depth checking, as we don't need crazy shit for tps.
        if (depth > 4 || (Math.abs(state.getData().getServerPosX().getCurrent() - state.getOtherPlayerMPX()) < 0.005 &&
                Math.abs(state.getData().getServerPosY().getCurrent() - state.getOtherPlayerMPY()) < 0.005 &&
                Math.abs(state.getData().getServerPosZ().getCurrent() - state.getOtherPlayerMPZ()) < 0.005)) {
            setPositionAndRotation2(state, state.getData().getServerPosX().getCurrent(),
                    state.getData().getServerPosY().getCurrent(), state.getData().getServerPosZ().getCurrent());
            for (final var child : state.getChildren()) {
                recursivelyTeleportPre(child, depth + 1);
            }
        } else {
            // Clone first
            final var neww = state.newChild(state, false, false);

            // Recursively run
            for (final var child : state.getChildren()) {
                recursivelyTeleportPre(child, depth + 1);
            }

            // Add the clone after doing recursion to avoid accidentally setting the pos on
            // the old entity state.
            state.getData().getTreeSize().increment(1 + neww.getChildren().size());
            state.getChildren().add(neww);

            // Tick this on post instead.
            this.stateBuffer.add(neww);

            // Set the data
            setPositionAndRotation2(state, state.getData().getServerPosX().getCurrent(),
                    state.getData().getServerPosY().getCurrent(), state.getData().getServerPosZ().getCurrent());
        }
    }

    private void setPositionAndRotation2(final EntityTrackerState state, final double x, final double y,
                                         final double z) {
        state.setOtherPlayerMPX(x);
        state.setOtherPlayerMPY(y);
        state.setOtherPlayerMPZ(z);
        state.setOtherPlayerMPPosRotationIncrements(3);
    }

    /**
     * Ticks an entity
     *
     * @return the updates
     */
    public int onLivingUpdateRecursive(final EntityTrackerState state) {
        int cnt = 0;
        if (state.getOtherPlayerMPPosRotationIncrements() > 0) {
            final double d0 = state.getPosX()
                    + (state.getOtherPlayerMPX() - state.getPosX()) / state.getOtherPlayerMPPosRotationIncrements();
            final double d1 = state.getPosY()
                    + (state.getOtherPlayerMPY() - state.getPosY()) / state.getOtherPlayerMPPosRotationIncrements();
            final double d2 = state.getPosZ()
                    + (state.getOtherPlayerMPZ() - state.getPosZ()) / state.getOtherPlayerMPPosRotationIncrements();

            // there used to be an old add offset call here

            state.setOtherPlayerMPPosRotationIncrements(state.getOtherPlayerMPPosRotationIncrements() - 1);

            // setPosition
            final var lastPosX = state.getPosX();
            final var lastPosY = state.getPosY();
            final var lastPosZ = state.getPosZ();

            state.setPosX(d0);
            state.setPosY(d1);
            state.setPosZ(d2);

            float f = state.getData().getWidth() / 2.0F;
            state.getBb().setMinX(state.getPosX() - f);
            state.getBb().setMinY(state.getPosY());
            state.getBb().setMinZ(state.getPosZ() - f);

            state.getBb().setMinX(state.getPosX() + f);
            state.getBb().setMinY(state.getPosY() + state.getData().getHeight());
            state.getBb().setMinZ(state.getPosZ() + f);

            // Total moves this tick
            if (Math.abs(lastPosX - state.getPosX()) > 0.0005D || Math.abs(lastPosY - state.getPosY()) > 0.0005D
                    || Math.abs(lastPosZ - state.getPosZ()) > 0.0005D) {
                this.totalMovesThisTick++;

                if (state.getOtherPlayerMPPosRotationIncrements() == 0) {
                    cnt++;
                }
            }
        } else if (state.getOtherPlayerMPPosRotationIncrements() == 0) {
            state.setPotentialOffsetAmountX(0);
            state.setPotentialOffsetAmountY(0);
            state.setPotentialOffsetAmountZ(0);
            state.setOtherPlayerMPPosRotationIncrements(-1);
        }

        final var children = state.getChildren();
        final var childrenArray = children.getRawArray();
        for (int i = 0; i < children.size(); i++) {
            final var child = childrenArray[i];
            cnt += onLivingUpdateRecursive((EntityTrackerState) child);
        }

        return cnt;
    }

    private void treeShrinkRecursive(final EntityTrackerState entityTrackerState, final int depth, final int maxDepth) {
        if (depth >= maxDepth) {
            entityTrackerState.getData().getTreeSize().decrement(entityTrackerState.getChildren().size());
            entityTrackerState.getChildren().clear();
        } else {
            // Also remove children if it's ridiculous
            int childCount = 0;
            stateBuffer2.clear();
            final var children = entityTrackerState.getChildren();
            var childrenArray = children.getRawArray();
            for (int i = 0; i < children.size(); i++) {
                if (childCount++ > depth * 5) {
                    final var child = childrenArray[i];
                    stateBuffer2.add((EntityTrackerState) child);
                }
            }

            final var state2Array = stateBuffer2.getRawArray();
            for (int i = 0; i < stateBuffer2.size(); i++) {
                final var state2 = state2Array[i];
                children.removeExact(state2);
            }

            // Tree shrink shit
            final var childDepth = depth + 1;

            childrenArray = children.getRawArray();
            for (int i = 0; i < children.size(); i++) {
                final var child = childrenArray[i];
                treeShrinkRecursive((EntityTrackerState) child, childDepth, maxDepth);
            }
        }
    }

    /**
     * Runs the actual logic for tree shaking.
     */
    private void shakeTreeRecursive(final EntityTrackerState entityTrackerState,
                                    final Object2BooleanFunction<EntityTrackerState> shouldDelete) {
        stateBuffer.clear(); // Flush old buffers.

        // Remove duplicated entries, and copy their children to the parent (current) node.
        for (final var child : entityTrackerState.getChildren()) {
            if (shouldDelete.getBoolean(child)) {
                // Decrement and remove
                entityTrackerState.getData().getTreeSize().decrement();
                entityTrackerState.getChildren().removeExact(child);

                // Copy the entry to the parent.
                for (final var childChild : child.getChildren()) {
                    stateBuffer.add(childChild);
                }
            }
        }

        // If state buffer is not empty, then add it to children, and call this again until it works.
        if (!stateBuffer.isEmpty()) {
            // Copy and flush the data buffer.
            entityTrackerState.getData().getTreeSize().increment(stateBuffer.size());
            entityTrackerState.getChildren().addAll(stateBuffer);
            stateBuffer.clear();
            // Recurse
            shakeTreeRecursive(entityTrackerState, shouldDelete);
            return; // Skip the following code as recursion will handle it.
        }

        // Tree shake all the children.
        // Use an array to avoid alloc.
        final var childrenArray = entityTrackerState.getChildren().getRawArray();
        final var childrenLen = entityTrackerState.getChildren().size();
        for (int i = 0; i < childrenLen; i++) {
            final var child = childrenArray[i];

            shakeTreeRecursive((EntityTrackerState) child, shouldDelete);
        }
    }

    /**
     * Gets all entities colliding with aabb
     *
     * @param bbThis the aabb to check entities with
     * @param out    the list to add entities to
     * @return the entities
     */
    public List<EntityData> getCollidingEntities(final AxisAlignedBB bbThis, final List<EntityData> out) {
        for (final var edata : this.entities.values()) {
            if (edata.getId() == getPlayer().getUser().getEntityId())
                continue;

            final var bb = edata.getRootState().getBb();

            if (bb != null) {
                if (bb.distance(bbThis) <= 4) {
                    out.add(edata);
                }
            }
        }
        return out;
    }
}
