package better.anticheat.core.punishment;

import better.anticheat.core.BetterAnticheat;
import better.anticheat.core.check.Check;
import better.anticheat.core.configuration.ConfigSection;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class PunishmentManager {

    private final BetterAnticheat plugin;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Map<String, PunishmentGroup> punishmentGroups = new ConcurrentHashMap<>();

    public void load() {
        punishmentGroups.clear();
        ConfigSection section = plugin.getConfigurationManager().getConfigurationFile("settings.conf").getRoot().getConfigSectionOrCreate("punishment-groups");

        if (!section.hasNode("default")) {
            final var defaultNode = section.getConfigSectionOrCreate("default");
            defaultNode.getOrSetStringList(
                    "per-group-punishments",
                    List.of("10:say %username% has accumulated 10 total VL!")
            );
            defaultNode.getOrSetStringList(
                    "per-check-punishments",
                    Arrays.asList(
                            "1:say %username% would be kicked for %type%!",
                            "5:say %username% would be banned for %type%!",
                            "10:[webhook]"
                    )
            );
        }

        for (ConfigSection groupSection : section.getChildren()) {
            String groupName = (String) groupSection.getKey();
            Map<Integer, List<String>> perGroupPunishments = parsePunishments(groupSection.getOrSetStringList("per-group-punishments", Collections.emptyList()), groupName);
            Map<Integer, List<String>> perCheckPunishments = parsePunishments(groupSection.getOrSetStringList("per-check-punishments", Collections.emptyList()), groupName);
            punishmentGroups.put(groupName, new PunishmentGroup(groupName, perGroupPunishments, perCheckPunishments));
        }
    }

    private Map<Integer, List<String>> parsePunishments(List<String> punishmentList, String groupName) {
        Map<Integer, List<String>> punishments = new HashMap<>();
        for (String punishment : punishmentList) {
            if (punishment == null) continue;
            String[] elements = punishment.split(":", 2);
            if (elements.length != 2) {
                plugin.getDataBridge().logWarning("Could not parse punishment '" + punishment + "' in group '" + groupName + "'. Invalid format.");
                continue;
            }
            try {
                int vl = Integer.parseInt(elements[0]);
                punishments.computeIfAbsent(vl, k -> new ArrayList<>()).add(elements[1]);
            } catch (NumberFormatException e) {
                plugin.getDataBridge().logWarning("Could not parse punishment '" + punishment + "' in group '" + groupName + "'. Invalid VL number.");
            }
        }
        return punishments;
    }

    public PunishmentGroup getPunishmentGroup(String groupName) {
        return punishmentGroups.get(groupName);
    }

    public void runPunishments(Check check) {
        int checkVl = check.getVl();
        for (final var group : check.getPunishmentGroups()) {
            int groupVl = 0;
            for (final var violation : check.getPlayer().getViolations()) {
                for (int groupNameHash : violation.getGroupNameHashs()) {
                    if (groupNameHash != group.getNameHash()) continue;

                    groupVl += violation.getVl();
                }
            }

            if (plugin.isPunishmentModulo()) {
                // Handle group punishments
                for (int punishVl : group.getPerGroupPunishments().keySet()) {
                    if (punishVl == 0) continue;
                    if (groupVl % punishVl == 0) {
                        runPunishment(check, punishVl, group.getPerGroupPunishments());
                    }
                }
                // Handle check punishments
                for (int punishVl : group.getPerCheckPunishments().keySet()) {
                    if (punishVl == 0) continue;
                    if (checkVl % punishVl == 0) {
                        runPunishment(check, punishVl, group.getPerCheckPunishments());
                    }
                }
            } else {
                // Handle group punishments
                if (group.getPerGroupPunishments().containsKey(groupVl)) {
                    runPunishment(check, groupVl, group.getPerGroupPunishments());
                }
                // Handle check punishments
                if (group.getPerCheckPunishments().containsKey(checkVl)) {
                    runPunishment(check, checkVl, group.getPerCheckPunishments());
                }
            }
        }
    }


    private void runPunishment(Check check, int vl, Map<Integer, List<String>> punishmentMap) {
        List<String> punishment = punishmentMap.get(vl);
        if (punishment != null) {
            for (String command : punishment) {
                if (command.startsWith("[mitigate")) {
                    String[] parts = command.split(" ");
                    if (parts.length == 2) {
                        try {
                            int ticks = Integer.parseInt(parts[1].replace("]", ""));
                            check.getPlayer().getMitigationTracker().getMitigationTicks().set(ticks);
                        } catch (NumberFormatException e) {
                            plugin.getDataBridge().logWarning("Invalid mitigate ticks in punishment: " + command);
                        }
                    }
                } else if (command.startsWith("[webhook]")) {
                    sendWebhook(check, vl);
                } else {
                    command = command.replaceAll("%username%", check.getPlayer().getUser().getName());
                    command = command.replaceAll("%type%", check.getName());
                    command = command.replaceAll("%vl%", Integer.toString(vl));
                    plugin.getDataBridge().sendCommand(command);
                }
            }
        }
    }

    private void sendWebhook(Check check, int vl) {
        String webhookUrl = plugin.getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        String message = plugin.getWebhookMessage();
        message = message.replaceAll("%username%", check.getPlayer().getUser().getName());
        message = message.replaceAll("%type%", check.getName());
        message = message.replaceAll("%vl%", Integer.toString(vl));

        try {
            Map<String, String> data = new HashMap<>();
            data.put("content", message);
            String json = JSON.toJSONString(data);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            plugin.getDataBridge().logWarning("Failed to send webhook: " + e.getMessage());
        }
    }
}
