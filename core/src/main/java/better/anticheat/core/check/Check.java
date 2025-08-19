package better.anticheat.core.check;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.configuration.ConfigSection;
import better.anticheat.core.player.Player;
import better.anticheat.core.player.Violation;
import better.anticheat.core.punishment.PunishmentGroup;
import better.anticheat.core.util.ChatUtil;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.apache.fory.ThreadSafeFory;
import wtf.spare.sparej.fastlist.FastObjectArrayList;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;

/**
 * This is the basis for a check in the anticheat. There are a few core components to understand:
 * 1. To use the constructor with only BetterAnticheat, you must use the @CheckInfo annotation on the class.
 * 2. The "vl" is the amount of violations a player has on this check. This will automatically increase when flagged.
 * 3. You can listen to packets coming from the client through handleReceivePlayPacket.
 * 4. You can listen to packets coming from the server through handleSendPlayPacket.
 * 5. The fail method indicates that a player is cheating.
 * 6. If you override the config handling, you must super.load()!
 */
public abstract class Check {

    protected BetterAnticheat plugin;
    @Getter
    protected Player player;

    @Getter @Setter
    private String name, category, config;
    @Getter
    private final boolean experimental;

    @Getter @Setter
    private boolean enabled = false;
    private int alertVL = 10, verboseVL = 1;
    private int decay = -1;
    private int combatMitigationTicksOnAlert = 0;
    private int combatMitigationTicksOnVerbose = 0;
    @Getter
    private final List<PunishmentGroup> punishmentGroups = new FastObjectArrayList<>();

    @Getter
    private int vl = 0;
    private long lastAlertMS = 0;
    private long lastVerboseMS = 0;


    /**
     * Construct the check via info provided in CheckInfo annotation.
     * This is the recommended approach but requires a @CheckInfo annotation on implementations.
     */
    public Check() {
        CheckInfo info = this.getClass().getAnnotation(CheckInfo.class);
        if (info == null) throw new InvalidParameterException("No CheckInfo annotation on class: " + this.getClass().getName() + "!");

        // Copy values from annotation.
        name = info.name();
        category = info.category();
        config = info.config();
        experimental = info.experimental();
    }

    /**
     * Construct the check via info provided in CheckInfo annotation.
     * This is the recommended approach but requires a @CheckInfo annotation on implementations.
     */
    public Check(BetterAnticheat plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        CheckInfo info = this.getClass().getAnnotation(CheckInfo.class);
        if (info == null) throw new InvalidParameterException("No CheckInfo annotation on class: " + this.getClass().getName() + "!");

        // Copy values from annotation.
        name = info.name();
        category = info.category();
        config = info.config();
        experimental = info.experimental();
    }

    /**
     * Construct the check via parameters.
     */
    public Check(BetterAnticheat plugin, Player player, String name, String category, String config, boolean experimental) {
        this.plugin = plugin;
        this.name = name;
        this.category = category;
        this.config = config;
        this.experimental = experimental;
    }

    public void handleReceivePlayPacket(PacketPlayReceiveEvent event) {
    }

    public void handleSendPlayPacket(PacketPlaySendEvent event) {
    }

    /*
     * Functions.
     */

    /**
     * Handles failing the check for the player with an empty debug.
     */
    protected void fail() {
        fail(null);
    }

    /**
     * Handles failing the check for the player with a debug.
     */
    protected void fail(Object debug) {
        fail(debug, false);
    }

    /**
     * Handles failing the check for the player with a debug.
     */
    protected void fail(Object debug, final boolean verboseOnly) {
        final long currentMS = System.currentTimeMillis();
        int newVl = 0;

        final var punishmentGroupHashs = new int[punishmentGroups.size()];
        for (int i = 0; i < punishmentGroups.size(); i++) {
            punishmentGroupHashs[i] = punishmentGroups.get(i).getNameHash();
        }
        player.getViolations().add(new Violation(this, punishmentGroupHashs, currentMS, 1));

        if (decay > 0) {
            final long minCreationTime = currentMS - decay;
            for (final var it = player.getViolations().iterator(); it.hasNext(); ) {
                final var v = it.next();
                if (v.getCreationTime() < minCreationTime && v.getCheck() == this) {
                    it.remove();
                }

                if (v.getCheck() == this) {
                    newVl += v.getVl();
                }
            }
        } else {
            for (final var violation : player.getViolations()) {
                if (violation.getCheck() == this) {
                    newVl += violation.getVl();
                }
            }
        }

        vl = Math.min(10000, newVl + 1);

        final var smallestDeltaMS = currentMS - Math.min(lastAlertMS, lastVerboseMS);
        final var deltaAlertMS = currentMS - lastAlertMS;
        final var deltaVerboseMS = currentMS - lastVerboseMS;
        final var verboseLimit = this.plugin.getAlertCooldown() / this.plugin.getVerboseCooldownDivisor();

        if (vl >= alertVL && !verboseOnly) {
            player.getMitigationTracker().getMitigationTicks().increment(combatMitigationTicksOnAlert);
        } else if (vl >= verboseVL) {
            player.getMitigationTracker().getMitigationTicks().increment(combatMitigationTicksOnVerbose);
        }

        /*
         * Now check if we should alert, and dispatch the alert if required.
         * 1. Ensure alerts are enabled (alertVL != -1)
         * 2. Ensure vl is high enough to alert (vl >= alertVL)
         * 3. Ensure the anti-spam cooldown has elapsed (elapsed >= alertCooldown)
         */
        if (alertVL != -1 && vl >= Math.min(alertVL, verboseVL) && smallestDeltaMS >= verboseLimit) {
            var message = this.plugin.getAlertMessage();
            if (!message.isEmpty()) {
                // Build the basic message body.
                message = message.replaceAll("%vl%", String.valueOf(vl));
                message = message.replaceAll("%type%", name);
                message = message.replaceAll("%username%", player.getUser().getName());
                message = ChatUtil.translateColors(message);
                Component finalMessage = Component.text(message);

                // Add the click command to the message. Kyori Adventure's syntax is horrible.
                String click = this.plugin.getClickCommand();
                if (!click.isEmpty())
                    finalMessage = finalMessage.clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/" + click.replaceAll("%username%", player.getUser().getName())));

                // Assemble and add the hover message.
                final var hoverBuild = new StringBuilder();
                for (final var string : this.plugin.getAlertHover()) {
                    hoverBuild.append(string
                                    .replaceAll("%clientversion%", player.getUser().getClientVersion().getReleaseName())
                                    .replaceAll("%debug%", debug == null ? "NO DEBUG" : debug.toString()))
                            .append("\n");
                }

                // An empty message will be length 2 due to the \n on the end.
                if (hoverBuild.length() > 2)
                    finalMessage = finalMessage.hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, Component.text(ChatUtil.translateColors(hoverBuild.substring(0, hoverBuild.length() - 1)))));

                if (this.plugin.isTestMode()) player.getUser().sendMessage(finalMessage);
                else {
                    // Now we know we are sending to staff, we need to determine if we use a verbose, or an alert.
                    // First, we try a verbose, if alert is too low, or cooldown has not elapsed. Otherwise, alert.
                    if (this.vl >= this.verboseVL && (this.vl < this.alertVL || verboseOnly ||
                            deltaAlertMS < this.plugin.getAlertCooldown()) &&
                            deltaVerboseMS >= verboseLimit) {
                        this.plugin.getPlayerManager().sendVerbose(finalMessage);
                        this.lastVerboseMS = currentMS;
                    } else if (this.vl >= this.alertVL && deltaAlertMS >= this.plugin.getAlertCooldown() &&
                            !verboseOnly) {
                        this.plugin.getPlayerManager().sendAlert(finalMessage);
                        this.lastAlertMS = currentMS;
                    }
                }
            }
        }

        /*
         * We have two punishment systems: modulo and strict.
         * Modulo assumes the punishment should be run whenever the vl is divisible by the setting amount.
         * Strict assumes the punishment should be run whenever the vl is the setting amount.
         */
        plugin.getPunishmentManager().runPunishments(this);
    }

    /**
     *
     */
    public void load(ConfigSection section) {
        if (section == null) {
            enabled = false;
            return;
        }

        enabled = section.getOrSetBooleanWithComment("enabled", true, "Whether the check should be enabled for players.");

        // No use in wasting more time loading.
        if (!enabled) return;

        alertVL = section.getOrSetIntegerWithComment("alert-vl", 1, "At what VL should alerts start to be sent?");
        verboseVL = section.getOrSetIntegerWithComment("verbose-vl", 1, "At what VL should verbose start to be sent?");
        decay = section.getOrSetIntegerWithComment("decay", 1200000, "How many MS should pass before VL start to decay?");

        String lowerCategory = category.toLowerCase(), lowerName = name.toLowerCase();
        var isCombatAdjacent = lowerCategory.contains("combat") || lowerCategory.contains("place") || lowerCategory.contains("heuristic") || lowerName.contains("aim");
        combatMitigationTicksOnAlert = section.getOrSetInteger("combat-mitigation-ticks-on-alert", isCombatAdjacent ? 40 : 0);
        combatMitigationTicksOnVerbose = section.getOrSetInteger("combat-mitigation-ticks-on-verbose", isCombatAdjacent ? 5 : 0);

        if (!section.hasNode("punishment-groups")) {
            List<String> groups = new ArrayList<>();
            if (plugin.getPunishmentManager().getPunishmentGroup(category) != null) {
                groups.add(category);
            } else {
                groups.add("default");

                if (plugin.getPunishmentManager().getPunishmentGroup("default") == null) {
                    plugin.getDataBridge().logWarning("Punishment group 'default' does not exist. The " + name + " check will not have any punishments.");
                }
            }
            section.setList(String.class, "punishment-groups", groups);
        }
        final var punishmentGroupNames = section.getList(String.class, "punishment-groups");

        punishmentGroups.clear();
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