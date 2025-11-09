package better.anticheat.core.command.impl;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.command.Command;
import better.anticheat.core.command.CommandInfo;
import better.anticheat.core.configuration.ConfigSection;
import better.anticheat.core.player.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import revxrsal.commands.annotation.CommandPlaceholder;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.command.CommandActor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This command toggles alerts for players, assuming they have the correct permissions.
 * Without sending this command, players will not get sent any alerts of players cheating.
 */
@CommandInfo(name = "alerts", parent = BACCommand.class)
public class AlertsCommand extends Command {

    private String[] changeOthersPerms;

    public AlertsCommand(BetterAnticheat plugin) {
        super(plugin);
    }

    @CommandPlaceholder
    public void onCommand(final CommandActor actor, @Optional final Player target) {
        if (!hasPermission(actor)) return;

        final var player = getPlayerFromActor(actor);
        if (player == null) {
            sendReply(actor, Component.text("You must be a player to run this command.").color(TextColor.color(0xFF0000)));
            return;
        }

        if (target == null || target.getUser().getUUID() == player.getUser().getUUID()) {
            player.setAlerts(!player.isAlerts());
            sendReply(actor, Component.text("Alerts have been " + (player.isAlerts() ? "enabled" : "disabled") + ".").color(TextColor.color(0x00FF00)));
        } else {
            if (!plugin.getDataBridge().hasPermission(player.getUser(), changeOthersPerms)) {
                sendReply(actor, Component.text("You do not have permission to toggle alerts for other players.").color(TextColor.color(0xFF0000)));
                return;
            }

            target.setAlerts(!target.isAlerts());
            sendReply(actor, Component.text("Alerts for " + target.getUser().getName() + " have been " + (target.isAlerts() ? "enabled" : "disabled") + ".").color(TextColor.color(0x00FF00)));
        }
    }

    @Override
    public void load(ConfigSection section) {
        super.load(section);

        List<String> changeOthersPermsList = section.getOrSetStringListWithComment(
                "change-others-permissions",
                Arrays.asList(
                        "better.anticheat.alerts.others",
                        "example.permission.node"
                ),
                "If the player has any of these permissions, they can set alerts for other players."
        );
        changeOthersPerms = new String[changeOthersPermsList.size()];
        for (int i = 0; i < changeOthersPermsList.size(); i++) changeOthersPerms[i] = changeOthersPermsList.get(i);
    }
}
