package better.anticheat.core.util.entity;

import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@ToString
@Getter
public enum ActivePlayerPose {
    STANDING(0.6, 1.8),
    FALL_FLYING(0.6, 0.6),
    SLEEPING(0.2, 0.2),
    SWIMMING(0.6, 0.6),
    SPIN_ATTACK(0.6, 0.6),
    CROUCHING(0.6, 1.5),
    DYING(0.2, 0.2);

    private final double width;
    private final double overallHeight;

    public static ActivePlayerPose from(final EntityPose pose) {
        return switch (pose) {
            case FALL_FLYING -> ActivePlayerPose.FALL_FLYING;
            case SLEEPING -> ActivePlayerPose.SLEEPING;
            case SWIMMING -> ActivePlayerPose.SWIMMING;
            case SPIN_ATTACK -> ActivePlayerPose.SPIN_ATTACK;
            case CROUCHING -> ActivePlayerPose.CROUCHING;
            case DYING -> ActivePlayerPose.DYING;
            default -> ActivePlayerPose.STANDING;
        };
    }
}