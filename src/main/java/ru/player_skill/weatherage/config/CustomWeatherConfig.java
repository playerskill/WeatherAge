package ru.player_skill.weatherage.config;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import java.util.List;
import java.util.Map;

public class CustomWeatherConfig {
    public final boolean enableBossbarSun;
    public final BarStyle bossbarSunStyle;
    public final BarColor bossbarSunColor;
    public final long bossbarSunDisplaySeconds;
    public final float bossbarSunFixedProgress;
    public final String bossbarSunText;
    public final String chatSunText;
    public final int sunMinSeconds;
    public final int sunMaxSeconds;
    public final double chanceSun;
    public final List<String> sunStartCommands;

    public final boolean enableBossbarRain;
    public final BarStyle bossbarRainStyle;
    public final BarColor bossbarRainColor;
    public final long bossbarRainDisplaySeconds;
    public final float bossbarRainFixedProgress;
    public final String bossbarRainText;
    public final String chatRainText;
    public final int rainMinSeconds;
    public final int rainMaxSeconds;
    public final double chanceRain;
    public final List<String> rainStartCommands;

    public final boolean enableBossbarThunder;
    public final BarStyle bossbarThunderStyle;
    public final BarColor bossbarThunderColor;
    public final long bossbarThunderDisplaySeconds;
    public final float bossbarThunderFixedProgress;
    public final String bossbarThunderText;
    public final String chatThunderText;
    public final int thunderMinSeconds;
    public final int thunderMaxSeconds;
    public final double chanceThunder;
    public final List<String> thunderStartCommands;

    public CustomWeatherConfig(Map<String, Object> data) {
        enableBossbarSun = getBoolean(data, "enable-bossbar-sun", true);
        bossbarSunStyle = parseBarStyle(getString(data, "bossbar-sun-style", "SOLID"));
        bossbarSunColor = parseBarColor(getString(data, "bossbar-sun-color", "YELLOW"));
        bossbarSunDisplaySeconds = getLong(data, "bossbar-sun-display-seconds", 30);
        bossbarSunFixedProgress = getFloat(data, "bossbar-sun-fixed-progress", 1.0f);
        bossbarSunText = getString(data, "bossbar-sun-text", "&6✷ &eСолнечно &6✷");
        chatSunText = getString(data, "chat-sun-text", "");
        sunMinSeconds = getInt(data, "sun-min-seconds", 1800);
        sunMaxSeconds = getInt(data, "sun-max-seconds", 3000);
        chanceSun = getDouble(data, "chance-sun", 0.626);
        sunStartCommands = getStringList(data, "sun-start-commands");

        enableBossbarRain = getBoolean(data, "enable-bossbar-rain", true);
        bossbarRainStyle = parseBarStyle(getString(data, "bossbar-rain-style", "SEGMENTED_6"));
        bossbarRainColor = parseBarColor(getString(data, "bossbar-rain-color", "BLUE"));
        bossbarRainDisplaySeconds = getLong(data, "bossbar-rain-display-seconds", 30);
        bossbarRainFixedProgress = getFloat(data, "bossbar-rain-fixed-progress", 0.05f);
        bossbarRainText = getString(data, "bossbar-rain-text", "&9☄ &bДождливо &9☄");
        chatRainText = getString(data, "chat-rain-text", "");
        rainMinSeconds = getInt(data, "rain-min-seconds", 480);
        rainMaxSeconds = getInt(data, "rain-max-seconds", 900);
        chanceRain = getDouble(data, "chance-rain", 0.256);
        rainStartCommands = getStringList(data, "rain-start-commands");

        enableBossbarThunder = getBoolean(data, "enable-bossbar-thunder", true);
        bossbarThunderStyle = parseBarStyle(getString(data, "bossbar-thunder-style", "SEGMENTED_6"));
        bossbarThunderColor = parseBarColor(getString(data, "bossbar-thunder-color", "PURPLE"));
        bossbarThunderDisplaySeconds = getLong(data, "bossbar-thunder-display-seconds", 30);
        bossbarThunderFixedProgress = getFloat(data, "bossbar-thunder-fixed-progress", 0.01f);
        bossbarThunderText = getString(data, "bossbar-thunder-text", "&b⚡ &9Гроза &b⚡");
        chatThunderText = getString(data, "chat-thunder-text", "");
        thunderMinSeconds = getInt(data, "thunder-min-seconds", 300);
        thunderMaxSeconds = getInt(data, "thunder-max-seconds", 1200);
        chanceThunder = getDouble(data, "chance-thunder", 0.118);
        thunderStartCommands = getStringList(data, "thunder-start-commands");
    }

    private static String getString(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : v.toString();
    }

    private static int getInt(Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return def; }
    }

    private static long getLong(Map<String, Object> map, String key, long def) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return def; }
    }

    private static double getDouble(Map<String, Object> map, String key, double def) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return def; }
    }

    private static float getFloat(Map<String, Object> map, String key, float def) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).floatValue();
        try { return Float.parseFloat(v.toString()); } catch (Exception e) { return def; }
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean def) {
        Object v = map.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        if (v == null) return def;
        return Boolean.parseBoolean(v.toString());
    }

    private static List<String> getStringList(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof List<?>) {
            List<?> list = (List<?>) v;
            List<String> res = new ArrayList<>();
            for (Object o : list) {
                if (o != null) res.add(o.toString());
            }
            return res;
        }
        return new ArrayList<>();
    }

    private static org.bukkit.boss.BarStyle parseBarStyle(String s) {
        switch (s.toUpperCase()) {
            case "SOLID": return org.bukkit.boss.BarStyle.SOLID;
            case "SEGMENTED_6": return org.bukkit.boss.BarStyle.SEGMENTED_6;
            case "SEGMENTED_10": return org.bukkit.boss.BarStyle.SEGMENTED_10;
            case "SEGMENTED_12": return org.bukkit.boss.BarStyle.SEGMENTED_12;
            case "SEGMENTED_20": return org.bukkit.boss.BarStyle.SEGMENTED_20;
            default: return org.bukkit.boss.BarStyle.SOLID;
        }
    }

    private static org.bukkit.boss.BarColor parseBarColor(String s) {
        switch (s.toUpperCase()) {
            case "WHITE": return org.bukkit.boss.BarColor.WHITE;
            case "YELLOW": return org.bukkit.boss.BarColor.YELLOW;
            case "BLUE": return org.bukkit.boss.BarColor.BLUE;
            case "GREEN": return org.bukkit.boss.BarColor.GREEN;
            case "PINK": return org.bukkit.boss.BarColor.PINK;
            case "PURPLE": return org.bukkit.boss.BarColor.PURPLE;
            case "RED": return org.bukkit.boss.BarColor.RED;
            default: return org.bukkit.boss.BarColor.YELLOW;
        }
    }
}
