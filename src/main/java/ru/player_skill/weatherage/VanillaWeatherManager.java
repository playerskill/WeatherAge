package ru.player_skill.weatherage;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VanillaWeatherManager {

    private final WeatherAgePlugin plugin;
    private final Map<String, ConfigurationSection> sections = new HashMap<>();

    public VanillaWeatherManager(WeatherAgePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        sections.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("vanilla-weather");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            if (key.equalsIgnoreCase("thunder-check-delay")) continue;
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;
            // Comma-separated world groups: "World3,World4"
            for (String worldName : key.split(",")) {
                sections.put(worldName.trim().toLowerCase(Locale.ROOT), section);
            }
        }
    }

    public boolean isManaged(World world) {
        return sections.containsKey(world.getName().toLowerCase(Locale.ROOT));
    }

    /** Sends the weather message to the world's chat and runs the commands (console). */
    public void announce(World world, String type) {
        ConfigurationSection section = sections.get(world.getName().toLowerCase(Locale.ROOT));
        if (section == null) return;

        String message = messageOf(section, type);
        if (message != null && !message.trim().isEmpty()) {
            String colored = color(message.replace("%world%", world.getName()));
            for (Player player : world.getPlayers()) {
                player.sendMessage(colored);
            }
        }

        runCommands(world, section, type);
    }

    /** Direct key lookup first, then case-insensitive fallback. */
    private String messageOf(ConfigurationSection section, String type) {
        String message = section.getString(type);
        if (message != null) return message;
        for (String key : section.getKeys(false)) {
            if (key.equalsIgnoreCase(type) && section.isString(key)) {
                return section.getString(key);
            }
        }
        return null;
    }

    private void runCommands(World world, ConfigurationSection section, String type) {
        Object commandsRaw = section.get("commands");
        if (commandsRaw == null) return;

        if (commandsRaw instanceof ConfigurationSection) {
            ConfigurationSection commands = (ConfigurationSection) commandsRaw;
            List<String> list = commands.getStringList(type);
            if (list.isEmpty()) {
                // Case-insensitive fallback for the per-type key
                for (String key : commands.getKeys(false)) {
                    if (key.equalsIgnoreCase(type)) {
                        list = commands.getStringList(key);
                        break;
                    }
                }
            }
            dispatch(world, list);
        } else if (commandsRaw instanceof List) {
            // Flat list form: commands common for every weather type
            List<String> list = new ArrayList<>();
            for (Object o : (List<?>) commandsRaw) {
                if (o instanceof String) list.add((String) o);
            }
            dispatch(world, list);
        }
    }

    private void dispatch(World world, List<String> commands) {
        for (String command : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    command.replace("%world%", world.getName()));
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
