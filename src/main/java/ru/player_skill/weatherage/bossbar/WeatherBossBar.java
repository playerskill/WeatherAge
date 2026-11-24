package ru.player_skill.weatherage.bossbar;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import ru.player_skill.weatherage.WeatherAgePlugin;

public class WeatherBossBar {

    private final WeatherAgePlugin plugin;

    public WeatherBossBar(WeatherAgePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Показывает боссбар всем игрокам в мире.
     *
     * @param world        Мир, где показывать
     * @param enabled      Включен ли боссбар
     * @param color        Цвет полосы (BarColor)
     * @param style        Стиль полосы (BarStyle)
     * @param text         Текст (с цветовыми кодами &)
     * @param fixedProgress Прогресс от 0.0 до 1.0 (1.0 = полная)
     * @param seconds      Длительность показа в секундах
     */
    public void show(World world, boolean enabled, BarColor color, BarStyle style, String text, float fixedProgress, int seconds) {
        if (!enabled || text == null || text.isEmpty() || world == null) {
            return;
        }

        // Нормализуем прогресс, чтобы не было ошибок, если передали 1.5 или -0.1
        float progress = Math.max(0.0f, Math.min(1.0f, fixedProgress));
        String coloredText = ChatColor.translateAlternateColorCodes('&', text);

        // Создаем ОДИН боссбар на мир (так как у него нет привязки к конкретному игроку в конструкторе)
        org.bukkit.boss.BossBar bossBar = Bukkit.createBossBar(coloredText, color, style, progress);

        // Добавляем всех игроков мира
        for (Player p : world.getPlayers()) {
            bossBar.addPlayer(p);
        }

        // Если длительность > 0, ставим таймер на удаление
        if (seconds > 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    bossBar.removeAll();
                }
            }.runTaskLater(plugin, seconds * 20L); // ticks = seconds * 20
        } else {
            // Если seconds == 0, бар останется навсегда, пока плагин не перезагрузится или мир не изменится
            // Можно добавить логику авто-удаления позже, если нужно
        }
    }
}
