package ru.player_skill.weatherage;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.World;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WeatherAgeCommand implements CommandExecutor {

    private final WeatherAgePlugin plugin;
    // Приветственное сообщение, которое показываем даже без прав
    private static final String INTRO_MESSAGE = ChatColor.translateAlternateColorCodes('&',
        "&bWeather&3Age &7- &fплагин для управления погодой от &6PlayeR_SkiLL&f."
    );

    public WeatherAgeCommand(WeatherAgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Сначала всегда показываем приветственное сообщение (даже если нет прав)
        sender.sendMessage(INTRO_MESSAGE);

        if (args.length == 0) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cИспользование: /weatherage <reload|sun|rain|storm> [world]"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                handleReload(sender);
                break;
            case "sun":
            case "rain":
            case "storm":
                handleForceWeather(sender, subCommand, args);
                break;
            default:
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cНеизвестная подкоманда: " + subCommand));
                return true;
        }

        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("weatherage.admin") && !(sender instanceof Player)) {
            // Консоль может релоадить всегда, игроки — только по пермишену
            sender.sendMessage(plugin.getMessages().get("no-permission"));
            return;
        }

        plugin.reloadConfig();
        plugin.loadWhitelist();
        plugin.loadCustomWeather();
        plugin.loadVanillaWeather();
        plugin.validateWorlds();

        sender.sendMessage(plugin.getMessages().get("reload-success"));
    }

    private void handleForceWeather(CommandSender sender, String weatherType, String[] args) {
        if (!sender.hasPermission("weatherage.force")) {
            sender.sendMessage(plugin.getMessages().get("no-permission"));
            return;
        }

        World targetWorld;
        if (args.length >= 2) {
            String worldName = args[1];
            targetWorld = Bukkit.getWorld(worldName);
            if (targetWorld == null) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cМир '" + worldName + "' не найден."));
                return;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cДля консоли обязательно укажите мир: /wage sun <world>"));
                return;
            }
            targetWorld = ((Player) sender).getWorld();
        }

        String worldName = targetWorld.getName();

        // Проверка: разрешены ли команды для этого мира
        if (!plugin.isWorldAllowedForCommand(worldName)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cВ этом мире принудительная смена погоды запрещена (мир не в whitelist или всегда солнечный)."));
            return;
        }

        String msgKey;
        boolean isRain = "rain".equals(weatherType);
        boolean isStorm = "storm".equals(weatherType);

        // Здесь можно позже добавить реальную логику установки погоды.
        // Сейчас просто возвращаем сообщения из конфига.
        if ("sun".equals(weatherType)) {
            msgKey = "forced-sun";
            targetWorld.setWeatherDuration(0); // отменяем дождь/грозу
            targetWorld.setThundering(false);
        } else if (isRain) {
            msgKey = "forced-rain";
            targetWorld.setWeatherDuration(Integer.MAX_VALUE / 2); // долгий дождь
            targetWorld.setThundering(false);
        } else { // storm
            msgKey = "forced-storm";
            targetWorld.setWeatherDuration(Integer.MAX_VALUE / 2);
            targetWorld.setThundering(true);
        }

        String message = plugin.getMessages().get(msgKey);
        if (message != null) {
            message = message.replace("%world%", worldName);
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return new ArrayList<>(List.of("reload", "sun", "rain", "storm"));
        }
        if (args.length == 2) {
            List<String> worlds = new ArrayList<>();
            for (World w : Bukkit.getWorlds()) {
                if (plugin.isWorldAllowedForCommand(w.getName())) {
                    worlds.add(w.getName());
                }
            }
            Collections.sort(worlds);
            return worlds;
        }
        return null;
    }
}
