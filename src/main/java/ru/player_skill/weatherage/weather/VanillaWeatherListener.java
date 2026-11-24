package ru.player_skill.weatherage.weather;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.scheduler.BukkitRunnable;
import ru.player_skill.weatherage.WeatherAgePlugin;
import ru.player_skill.weatherage.config.VanillaWeatherConfig;
import ru.player_skill.weatherage.util.CommandRunner;

import java.util.HashMap;
import java.util.Map;

public class VanillaWeatherListener implements Listener {

    private final WeatherAgePlugin plugin;
    // Для каждого мира храним, сколько тиков осталось ждать после начала дождя, чтобы не дублировать грозу
    private final Map<String, Integer> stormCooldown = new HashMap<>();

    public VanillaWeatherListener(WeatherAgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        String worldName = event.getWorld().getName();
        VanillaWeatherConfig cfg = plugin.getVanillaConfig(worldName);

        // Если это не мир с vanilla-weather — не делаем ничего
        if (cfg == null) return;

        boolean toRain = event.toRain();
        boolean fromRain = event.fromRain();
        boolean toThunder = event.toThundering();
        boolean fromThunder = event.fromThundering();

        int cooldownTicks = cfg.stormWaitingTicks;

        // Логика: дождь начался
        if (toRain && !fromRain) {
            // Сбрасываем кулдаун грозы и запускаем сообщения/команды для дождя
            stormCooldown.put(worldName, 0);
            handleRainStart(worldName, cfg);
            return;
        }

        // Логика: гроза началась
        if (toThunder && !fromThunder) {
            // Проверяем кулдаун: если после начала дождя прошло меньше cooldownTicks — игнорируем
            Integer remaining = stormCooldown.getOrDefault(worldName, 0);
            if (remaining > 0) {
                // Гроза началась слишком рано после дождя — считаем это частью дождя, не дублируем сообщения
                return;
            }
            handleThunderStart(worldName, cfg);
            // После обработки грозы ставим кулдаун, чтобы не было повторных срабатываний в короткий промежуток
            stormCooldown.put(worldName, cooldownTicks);
            return;
        }

        // Опционально: можно обрабатывать конец дождя/грозы, если нужны сообщения «дождь закончился»
    }

    private void handleRainStart(String worldName, VanillaWeatherConfig cfg) {
        if (cfg.rainStartMessage != null && !cfg.rainStartMessage.isEmpty() && !cfg.rainStartMessage.equals("none")) {
            String msg = ChatColor.translateAlternateColorCodes('&', cfg.rainStartMessage);
            plugin.getServer().broadcastMessage(msg);
        }
        CommandRunner.runCommands(cfg.rainStartCommands, plugin);
    }

    private void handleThunderStart(String worldName, VanillaWeatherConfig cfg) {
        if (cfg.thunderStartMessage != null && !cfg.thunderStartMessage.isEmpty() && !cfg.thunderStartMessage.equals("none")) {
            String msg = ChatColor.translateAlternateColorCodes('&', cfg.thunderStartMessage);
            plugin.getServer().broadcastMessage(msg);
        }
        CommandRunner.runCommands(cfg.thunderStartCommands, plugin);
    }

    // Каждую тик уменьшаем кулдаун (чтобы он не висел вечно)
    // Лучше всего запускать один общий BukkitRunnable в WeatherAgePlugin.onEnable
    public void tick() {
        for (Map.Entry<String, Integer> e : stormCooldown.entrySet()) {
            int remaining = e.getValue() - 1;
            if (remaining <= 0) {
                stormCooldown.remove(e.getKey());
            } else {
                stormCooldown.put(e.getKey(), remaining);
            }
        }
    }
}
