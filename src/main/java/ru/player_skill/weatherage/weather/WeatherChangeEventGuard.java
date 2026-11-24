package ru.player_skill.weatherage.weather;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.WeatherChangeEvent;
import ru.player_skill.weatherage.WeatherAgePlugin;
import ru.player_skill.weatherage.config.WorldMode;

public class WeatherChangeEventGuard implements Listener {

    private final WeatherAgePlugin plugin;

    public WeatherChangeEventGuard(WeatherAgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        String worldName = event.getWorld().getName();
        WorldMode mode = plugin.getWorldMode(worldName);

        if (mode == WorldMode.ALWAYS_SUN) {
            event.setCancelled(true);
            // Можно раскомментировать для отладки:
            // plugin.getLogger().fine("Погода отменена в мире '" + worldName + "': режим ALWAYS_SUN");
        }
    }
}
