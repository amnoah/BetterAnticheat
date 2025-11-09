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
import better.anticheat.core.check.impl.heuristic.MicroAimMovementCheck;
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

    private final Map<Class, Check> cachedChecksMap = new HashMap<>();

    /**
     * Initialize the CheckManager object.
     */
    public CheckManager(BetterAnticheat plugin) {
        this.plugin = plugin;
        List<Check> cachedChecks = createChecks(null);
        for (Check check : cachedChecks) {
            cachedChecksMap.put(check.getClass(), check);
        }
    }

    /**
     * Retrieve a complete list of checks for the given player.
     * Note: These checks will not have config settings loaded in.
     */
    public List<Check> createChecks(Player player) {
        return Arrays.asList(
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
                new MicroAimMovementCheck(plugin, player),

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
    }

    /**
     * Retrieve a copy of checks for the given player. This is appropriate for player usage.
     */
    public List<Check> getChecksForPlayer(Player player) {
        // Create an initial copy of each check that needs to be distributed to players.
        List<Check> checks = createChecks(player);

        // Check what checks should be removed from the player's list.
        Iterator<Check> checkIterator = checks.iterator();
        while (checkIterator.hasNext()) {
            Check check = checkIterator.next();

            // Verify that the player has the necessary client feature requirements to run the check.

            boolean ok = true;
            for (final var req : check.getFeatureRequirements()) {
                if (!req.matches(player)) {
                    ok = false;
                    break;
                }
            }

            if (!ok) {
                checkIterator.remove();
                continue;
            }

            // Grab the cached config for the check.
            check.setCheckConfig(cachedChecksMap.get(check.getClass()).getCheckConfig());

            // No use in keeping disabled checks.
            if (!check.getCheckConfig().isEnabled()) checkIterator.remove();
        }

        return checks;
    }

    /**
     * Load the check manager, caching the config settings for checks.
     */
    public void load() {
        int enabled = 0;
        for (Check check : cachedChecksMap.values()) {
            ConfigurationFile file = plugin.getConfigurationManager().getConfigurationFile(check.getConfig());
            ConfigSection node = file.getRoot();
            if (node == null) continue;
            node = node.getConfigSectionOrCreate(check.getCategory(), check.getName());
            check.load(node);
            if (check.getCheckConfig().isEnabled()) enabled++;
        }

        plugin.getDataBridge().logInfo("Loaded " + cachedChecksMap.size() + " checks, with " + enabled + " being enabled.");
    }
}
