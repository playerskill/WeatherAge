package ru.player_skill.weatherage.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import ru.player_skill.weatherage.WeatherAgePlugin;

import java.util.List;

public class CommandRunner {
    public static void runCommands(List<String> commands, WeatherAgePlugin plugin) {
        if (commands == null || commands.isEmpty()) return;

        for (String cmd : commands) {
            if (cmd == null) continue;
            String trimmed = cmd.trim();
            if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("none")) continue;

            // Выполняем от имени консоли, чтобы не зависеть от прав игроков
            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), trimmed);
        }
    }
}
