package ru.player_skill.weatherage.weather;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.WeatherChangeEvent;
import ru.player_skill.weatherage.WeatherAgePlugin;
import ru.player_skill.weatherage.config.VanillaWeatherConfig;
import ru.player_skill.weatherage.util.CommandRunner;

import java.util.HashMap;
import java.util.Map;

public class VanillaWeatherListener implements Listener {

    private final WeatherAgePlugin plugin;
    
    // Храним состояние: был ли дождь/гроза в мире ДО этого события
    private final Map<String, Boolean> wasRaining = new HashMap<>();
    private final Map<String, Boolean> wasThundering = new HashMap<>();

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

        // ВАЖНО: В Bukkit нет event.isRain(). 
        // Мы определяем изменение погоды, сравнивая текущее состояние с сохранённым ранее.
        // Но так как событие приходит только при ИЗМЕНЕНИИ, мы можем считать, что погода сменилась.
        // Однако нам нужно знать, НА ЧТО она сменилась. 
        // В стандартном Bukkit API нет прямого способа узнать тип погоды из события.
        // Поэтому мы используем хитрость: смотрим на мир и проверяем его погоду напрямую.
        
        boolean nowRaining = event.getWorld().hasStorm();
        boolean nowThundering = event.getWorld().isThundering();

        Boolean prevRaining = wasRaining.getOrDefault(worldName, false);
        Boolean prevThundering = wasThundering.getOrDefault(worldName, false);

        int cooldownTicks = cfg.stormWaitingTicks;

        // Логика: дождь начался (был не дождь, стал дождь)
        // Примечание: hasStorm() возвращает true, если идет дождь или гроза.
        if (nowRaining && !prevRaining) {
            // Сбрасываем кулдаун грозы и запускаем сообщения/команды для дождя
            stormCooldown.put(worldName, 0);
            handleRainStart(worldName, cfg);
        }

        // Логика: гроза началась (была не гроза, стала гроза)
        if (nowThundering && !prevThundering) {
            // Проверяем кулдаун: если после начала дождя прошло меньше cooldownTicks — игнорируем
            Integer remaining = stormCooldown.getOrDefault(worldName, 0);
            if (remaining > 0) {
                // Гроза началась слишком рано после дождя — считаем это частью дождя, не дублируем сообщения
                // Можно добавить лог для отладки: plugin.getLogger().info("Storm ignored due to cooldown in " + worldName);
            } else {
                handleThunderStart(worldName, cfg);
                // После обработки грозы ставим кулдаун, чтобы не было повторных срабатываний в короткий промежуток
                stormCooldown.put(worldName, cooldownTicks);
            }
        }

        // Обновляем состояние для следующего события
        wasRaining.put(worldName, nowRaining);
        wasThundering.put(worldName, nowThundering);
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

    /**
     * Каждую тик уменьшаем кулдаун.
     * Этот метод нужно вызывать из BukkitRunnable в WeatherAgePlugin.onEnable()
     */
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
