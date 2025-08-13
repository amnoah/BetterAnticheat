package better.anticheat.paper.listener;

import better.anticheat.core.BetterAnticheat;
import com.github.retrooper.packetevents.PacketEvents;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Random;

/**
 * Handles ML-based combat damage modification for Paper.
 * Monitors player damage events and applies modifications based on CMLTracker averages.
 */
public class CombatDamageListener implements Listener {

    private final Random random = new Random();

    /**
     * Handles entity damage events to apply ML-based combat modifications.
     * Calculates overall average from all MLCheck history arrays and applies
     * hit cancellation or damage reduction based on configuration.
     *
     * @param event the damage event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void mitigateReduceDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player bukkitPlayer)) return;

        final var user = PacketEvents.getAPI().getPlayerManager().getUser(bukkitPlayer);
        if (user == null) return;

        final var player = BetterAnticheat.getInstance().getPlayerManager().getPlayer(user);
        if (player == null) return;

        final var mitigationTracker = player.getMitigationTracker();
        if (mitigationTracker == null) return;

        if (!BetterAnticheat.getInstance().isMitigationCombatDamageEnabled()) return;

        if (mitigationTracker.getMitigationTicks().get() <= 0) return;

        if (random.nextDouble() * 100.0 < BetterAnticheat.getInstance().getMitigationCombatDamageCancellationChance()) {
            event.setCancelled(true);
            return;
        }

        final double damageMultiplier = BetterAnticheat.getInstance().getMitigationCombatDamageDealtDecrease();
        final double damagePercentage = Math.max(0.1, (100.0 - damageMultiplier)) / 100.0;

        final double originalDamage = event.getDamage();
        final double newDamage = originalDamage * damagePercentage;
        event.setDamage(newDamage);

        if (event.getEntity() instanceof Player && !event.getEntity().getVelocity().isZero()) {
            final double knockbackMultiplier = BetterAnticheat.getInstance().getMitigationCombatKnockbackDealtDecrease();
            final double knockbackPercentage = Math.max(0.0, (100.0 - knockbackMultiplier) / 100.0);
            event.getEntity().setVelocity(event.getEntity().getVelocity().multiply(knockbackPercentage));
        }
    }

    /**
     * Increases the damage you take if you are cheating.
     *
     * @param event the damage event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void mitigateIncreaseDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player bukkitPlayer)) return;

        final var user = PacketEvents.getAPI().getPlayerManager().getUser(bukkitPlayer);
        if (user == null) return;

        final var player = BetterAnticheat.getInstance().getPlayerManager().getPlayer(user);
        if (player == null) return;

        final var mitigationTracker = player.getMitigationTracker();
        if (mitigationTracker == null) return;

        if (!BetterAnticheat.getInstance().isMitigationCombatDamageEnabled()) return;

        if (mitigationTracker.getMitigationTicks().get() <= 0) return;

        final double damageMultiplier = BetterAnticheat.getInstance().getMitigationCombatDamageTakenIncrease();
        final double damagePercentage = 1 + Math.max(0.0, (100.0 - damageMultiplier) / 100.0);

        final double originalDamage = event.getDamage();
        final double newDamage = originalDamage * damagePercentage;
        event.setDamage(newDamage);
    }
}