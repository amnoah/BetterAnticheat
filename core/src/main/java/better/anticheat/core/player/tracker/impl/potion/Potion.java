package better.anticheat.core.player.tracker.impl.potion;

import lombok.Getter;
import lombok.Setter;
import wtf.spare.sparej.incrementer.IntIncrementer;

@Getter
@Setter
public class Potion {

    private int amplifier, duration;
    public IntIncrementer activeCount = null;

    public Potion() {}

    public Potion(int amplifier, int duration) {
        this.amplifier = amplifier;
        this.duration = duration;
    }

    public boolean isActive() {
        if (duration < 0) return true;
        return activeCount.get() <= duration;
    }
}
