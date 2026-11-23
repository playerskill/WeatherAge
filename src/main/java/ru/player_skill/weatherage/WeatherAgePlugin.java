package ru.player_skill.weatherage.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import ru.player_skill.weatherage.WeatherAgePlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Обработчик команды /weatherage.
 *
 * Подкоманды:
 *   /weatherage               — информация о плагине и авторе.
 *   /weatherage reload        — перезагрузка конфига (требуется weatherage.admin).
 *   /weatherage sun|rain|storm [мир] — принудительная смена погоды
 *                               (требуется weatherage.admin). Без аргумента мир
 *                               берётся тот, где стоит игрок; для консоли —
 *                               первый мир сервера.
 */
public final class WeatherAgeCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS =
            Arrays.asList("reload", "sun", "rain", "storm");

    private final WeatherAgePlugin plugin;

    public WeatherAgeCommand(WeatherAgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("weatherage")) {
            return false;
        }

        // /weatherage — приветственное сообщение с информацией об авторе
        if (args.length == 0) {
            sender.sendMessage(color(
                "&bWeather&3Age &7— &fплагин для управления погодой в мирах сервера. " +
                "&7Разработан &6PlayeR_SkiLL&f."
            ));
            return true;
        }

        String sub = args[0].toLowerCase();

        // ---------- /weatherage reload ----------
        if ("reload".equals(sub)) {
            if (!sender.hasPermission("weatherage.admin")) {
                sendFromConfig(sender, "messages.no-permission",
                        "&cУ вас нет прав для выполнения этой команды.");
                return true;
            }

            plugin.reloadAll();
            sendFromConfig(sender, "messages.reload-success",
                    "&aКонфигурация WeatherAge успешно перезагружена.");
            plugin.getLogger().info(sender.getName() + " перезагрузил конфиг WeatherAge.");
            return true;
        }

        // ---------- /weatherage sun|rain|storm [world] ----------
        if ("sun".equals(sub) || "rain".equals(sub) || "storm".equals(sub)) {
            if (!sender.hasPermission("weatherage.admin")) {
                sendFromConfig(sender, "messages.no-permission",
                        "&cУ вас нет прав на принудительную смену погоды.");
                return true;
            }

            World target = resolveWorld(sender, args);
            if (target == null) {
                sender.sendMessage(color("&cНе удалось определить мир. Укажите его явно: /weatherage "
                        + sub + " <имя_мира>"));
                return true;
            }

            // Проверка whitelist: миры вне списка всегда солнечные, принудительно менять нельзя
            if (!plugin.isWhitelisted(target)) {
                sendFromConfig(sender, "messages.not-whitelisted",
                        "&cВ этом мире погода всегда ясная и не может быть изменена.");
                return true;
            }

            // Вызываем именно твой метод forceWeather — он корректно обрабатывает группы
            plugin.forceWeather(target, sub);

            String msgKey = "messages.forced-" + sub;
            String defMsg;
            if ("sun".equals(sub)) {
                defMsg = "&aПогода в мире &7%world% &aустановлена на &eСолнце&a.";
            } else if ("rain".equals(sub)) {
                defMsg = "&aПогода в мире &7%world% &aустановлена на &bДождь&a.";
            } else {
                defMsg = "&aПогода в мире &7%world% &aустановлена на &9Гроза&a.";
            }
            sendFromConfig(sender, msgKey, defMsg, "%world%", target.getName());

            plugin.getLogger().info(sender.getName() + " принудительно установил погоду "
                    + sub + " в мире " + target.getName() + ".");
            return true;
        }

        // ---------- неизвестная подкоманда ----------
        sender.sendMessage(color("&cНеизвестная подкоманда. Доступные варианты: /weatherage [reload|sun|rain|storm] [мир]"));
        return true;
    }

    /**
     * Определяет целевой мир: явное имя из аргументов,
     * иначе мир игрока, иначе первый мир сервера (для консоли).
     */
    private World resolveWorld(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            World named = Bukkit.getWorld(args[1]);
            if (named == null) {
                sender.sendMessage(color("&cМир '" + args[1] + "' не найден."));
                return null;
            }
            return named;
        }

        if (sender instanceof Player) {
            return ((Player) sender).getWorld();
        }

        // Консоль: берём первый мир
        if (!Bukkit.getWorlds().isEmpty()) {
            return Bukkit.getWorlds().get(0);
        }
        return null;
    }

    /** Читает строку из конфига, подставляет %world% и красит. */
    private void sendFromConfig(CommandSender sender, String key, String def, String... placeholders) {
        String msg = plugin.getConfig().getString("messages." + key, def);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            msg = msg.replace(placeholders[i], placeholders[i + 1]);
        }
        sender.sendMessage(color(msg));
    }

    private String color(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    // ---------- автодополнение по Tab ----------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("weatherage")) {
            return null;
        }

        if (args.length == 1) {
            String typed = args[0].toLowerCase();
            List<String> result = new ArrayList<>();
            for (String s : SUB_COMMANDS) {
                if (s.startsWith(typed)) {
                    result.add(s);
                }
            }
            return result;
        }

        if (args.length == 2) {
            String typed = args[1].toLowerCase();
            List<String> result = new ArrayList<>();
            for (World w : Bukkit.getWorlds()) {
                if (w.getName().toLowerCase().startsWith(typed)) {
                    result.add(w.getName());
                }
            }
            return result;
        }

        return null;
    }
}
