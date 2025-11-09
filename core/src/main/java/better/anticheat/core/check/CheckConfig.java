package better.anticheat.core.check;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.configuration.ConfigSection;
import better.anticheat.core.punishment.PunishmentGroup;
import lombok.Getter;
import lombok.Setter;
import wtf.spare.sparej.fastlist.FastObjectArrayList;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class CheckConfig {

    private boolean enabled;
    private int alertVL, verboseVL, decay, combatMitigationTicksOnAlert, combatMitigationTicksOnVerbose;
    private final List<PunishmentGroup> punishmentGroups = new FastObjectArrayList<>();

    public CheckConfig(BetterAnticheat plugin, ConfigSection section, String category, String name) {
        enabled = section.getOrSetBooleanWithComment("enabled", true, "Whether the check should be enabled for players.");

        // No use in wasting more time loading.
        if (!enabled) return;

        alertVL = section.getOrSetIntegerWithComment("alert-vl", 1, "At what VL should alerts start to be sent?");
        verboseVL = section.getOrSetIntegerWithComment("verbose-vl", 1, "At what VL should verbose start to be sent?");
        decay = section.getOrSetIntegerWithComment("decay", 1200000, "How many MS should pass before VL start to decay?");

        String lowerCategory = category.toLowerCase(), lowerName = name.toLowerCase();
        var isCombatAdjacent = lowerCategory.contains("combat") || lowerCategory.contains("place") || lowerCategory.contains("heuristic") || lowerName.contains("aim");
        combatMitigationTicksOnAlert = section.getOrSetIntegerWithComment("combat-mitigation-ticks-on-alert", isCombatAdjacent ? 40 : 0, "How long should combat be mitigated on alert (in ticks)?");
        combatMitigationTicksOnVerbose = section.getOrSetIntegerWithComment("combat-mitigation-ticks-on-verbose", isCombatAdjacent ? 5 : 0, "How long should combat be mitigated on verbose (in ticks)?");

        if (!section.hasNode("punishment-groups")) {
            List<String> groups = new ArrayList<>();
            if (plugin.getPunishmentManager().getPunishmentGroup(category.toLowerCase()) != null) {
                groups.add(category.toLowerCase());
            } else {
                groups.add("default");

                if (plugin.getPunishmentManager().getPunishmentGroup("default") == null) {
                    plugin.getDataBridge().logWarning("Punishment group 'default' does not exist. The " + name + " check will not have any punishments.");
                }
            }
            section.getOrSetStringListWithComment("punishment-groups", groups, "This is a list of punishment groups that this check will belong to.");
        }
        final var punishmentGroupNames = section.getList(String.class, "punishment-groups");
        if (punishmentGroupNames.isEmpty()) return;
        for (final var groupName : punishmentGroupNames.get()) {
            final var group = plugin.getPunishmentManager().getPunishmentGroup(groupName);
            if (group != null) {
                punishmentGroups.add(group);
            } else {
                plugin.getDataBridge().logWarning("Punishment group '" + groupName + "' does not exist. The " + name + " check will not have any punishments.");
            }
        }
    }
}
