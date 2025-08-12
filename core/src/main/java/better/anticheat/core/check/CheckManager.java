package better.anticheat.core.check;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.impl.chat.HiddenChatCheck;
import better.anticheat.core.check.impl.chat.ImpossibleCompletionCheck;
import better.anticheat.core.check.impl.chat.ImpossibleMessageCheck;
import better.anticheat.core.check.impl.combat.*;
import better.anticheat.core.check.impl.dig.DigBlockFacePositionCheck;
import better.anticheat.core.check.impl.dig.DigOrderCheck;
import better.anticheat.core.check.impl.dig.MultiBreakCheck;
import better.anticheat.core.check.impl.dig.RepeatedDigCheck;
import better.anticheat.core.check.impl.flying.*;
import better.anticheat.core.check.impl.heuristic.AimStabilizationCheck;
import better.anticheat.core.check.impl.heuristic.CombatAccelerationCheck;
import better.anticheat.core.check.impl.heuristic.LinearAimDeviationCheck;
import better.anticheat.core.check.impl.heuristic.MicroMovementCheck;
import better.anticheat.core.check.impl.misc.*;
import better.anticheat.core.check.impl.packet.BalanceCheck;
import better.anticheat.core.check.impl.packet.PostCheck;
import better.anticheat.core.check.impl.place.CursorPositionCheck;
import better.anticheat.core.check.impl.place.PlaceBlockFacePositionCheck;
import better.anticheat.core.configuration.ConfigSection;
import better.anticheat.core.configuration.ConfigurationFile;
import better.anticheat.core.player.Player;

import java.util.*;

/**
 * This manager provides a centralized way to distribute checks to players.
 */
public class CheckManager {

    private final BetterAnticheat plugin;

    /**
     * Initialize the CheckManager object.
     */
    public CheckManager(BetterAnticheat plugin) {
        this.plugin = plugin;
    }

    /**
     * Retrieve a copy of checks for the given player.
     */
    public List<Check> getChecks(Player player) {
        // Create an initial copy of each check that needs to be distributed to players.
        List<Check> checks = Arrays.asList(
                // Chat Checks
                new HiddenChatCheck(plugin, player),
                new ImpossibleCompletionCheck(plugin, player),
                new ImpossibleMessageCheck(plugin, player),

                // Combat Checks
                new ActionInteractOrderCheck(plugin, player),
                new DualClickCheck(plugin, player),
                new InvalidInteractionPositionCheck(plugin, player),
                new InvalidReleaseValuesCheck(plugin, player),
                new InvalidUseActionsCheck(plugin, player),
                new MultipleActionCheck(plugin, player),
                new MultipleHitCheck(plugin, player),
                new NoSwingCombatCheck(plugin, player),
                new SelfHitCheck(plugin, player),
                new SlotInteractOrderCheck(plugin, player),

                // Dig Checks
                new DigBlockFacePositionCheck(plugin, player),
                new DigOrderCheck(plugin, player),
                new MultiBreakCheck(plugin, player),
                new RepeatedDigCheck(plugin, player),

                // Flying Checks
                new ArtificialFlyingCheck(plugin, player),
                new ArtificialPositionCheck(plugin, player),
                new FlyingSequenceCheck(plugin, player),
                new ImpossiblePositionCheck(plugin, player),
                new ImpossibleRotationCheck(plugin, player),
                new RepeatedRotationCheck(plugin, player),
                new RepeatedSteerCheck(plugin, player),

                // Heuristic Checks
                new AimStabilizationCheck(plugin, player),
                new CombatAccelerationCheck(plugin, player),
                new LinearAimDeviationCheck(plugin, player),
                new MicroMovementCheck(plugin, player),

                // Misc Checks
                new ImpossibleHorseJumpCheck(plugin, player),
                new ImpossibleSlotCheck(plugin, player),
                new LargeNameCheck(plugin, player),
                new MultipleSlotCheck(plugin, player),
                new SmallRenderCheck(plugin, player),

                // Packet Checks
                new BalanceCheck(plugin, player),
                new PostCheck(plugin, player),

                // Place Checks
                new CursorPositionCheck(plugin, player),
                new PlaceBlockFacePositionCheck(plugin, player)
        );

        // Check what checks should be removed from the player's list.
        Iterator<Check> checkIterator = checks.iterator();
        while (checkIterator.hasNext()) {
            Check check = checkIterator.next();

            // Filter by feature requirements (if they are present). If the player doesn't meet them, remove the check.
            final var info = check.getClass().getAnnotation(CheckInfo.class);
            if (info != null && info.requirements() != null) {
                var requirements = info.requirements();
                boolean ok = true;
                for (final var req : requirements) {
                    if (!req.matches(player)) {
                        ok = false;
                        break;
                    }
                }

                if (!ok) {
                    checkIterator.remove();
                    continue;
                }
            }

            ConfigurationFile file = plugin.getFile(check.getConfig());
            ConfigSection node = file.getRoot();
            node = node.getConfigSectionOrCreate(check.getCategory(), check.getName());
            check.load(node);

            if (!check.isEnabled()) checkIterator.remove();
        }

        return checks;
    }
}
