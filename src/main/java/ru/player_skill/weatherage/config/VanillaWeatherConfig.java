package ru.player_skill.weatherage.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VanillaWeatherConfig {
    public final String sunStartMessage;
    public final String rainStartMessage;
    public final String thunderStartMessage;
    public final List<String> sunStartCommands;
    public final List<String> rainStartCommands;
    public final List<String> thunderStartCommands;
    public final int stormWaitingTicks;

    public VanillaWeatherConfig(Map<String, Object> data) {
        sunStartMessage = getString(data, "sun-start-message", "");
        rainStartMessage = getString(data, "rain-start-message", "");
        thunderStartMessage = getString(data, "thunder-start-message", "");

        sunStartCommands = getStringList(data, "sun-start-commands");
        rainStartCommands = getStringList(data, "rain-start-commands");
        thunderStartCommands = getStringList(data, "thunder-start-commands");

        stormWaitingTicks = getInt(data, "storm-waiting-ticks", 20);
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
}
