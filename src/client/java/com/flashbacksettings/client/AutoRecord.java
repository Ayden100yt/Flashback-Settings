package com.flashbacksettings.client;

import com.flashbacksettings.FlashbackSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AutoRecord {
    private AutoRecord() {
    }

    public static boolean shouldAutoStart(boolean flashbackSetting) {
        FlashbackSettings mod = FlashbackSettings.getInstance();
        if (mod == null || mod.getConfig() == null || !mod.getConfig().autoRecordPerServer) {
            return flashbackSetting;
        }
        FlashbackSettings.ModConfig config = mod.getConfig();
        String host = currentHost();
        if (host == null) {
            return config.autoRecordSingleplayer;
        }
        return matchesAny(host, config.autoRecordServers);
    }

    public static String currentHost() {
        Minecraft client = Minecraft.getInstance();
        ServerData server = client == null ? null : client.getCurrentServer();
        if (server == null || server.ip == null) {
            return null;
        }
        String host = normalize(server.ip);
        return host.isEmpty() ? null : host;
    }

    public static boolean matchesAny(String host, List<String> rules) {
        if (host == null || rules == null) {
            return false;
        }
        for (String rule : rules) {
            if (matches(host, rule)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matches(String host, String rule) {
        String h = normalize(host);
        String r = normalize(rule);
        if (h.isEmpty() || r.isEmpty()) {
            return false;
        }
        return h.equals(r) || h.endsWith("." + r);
    }

    public static String normalize(String address) {
        if (address == null) {
            return "";
        }
        String s = address.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("[")) {
            int end = s.indexOf(']');
            s = end > 0 ? s.substring(1, end) : s.substring(1);
        } else if (s.indexOf(':') >= 0 && s.indexOf(':') == s.lastIndexOf(':')) {
            s = s.substring(0, s.indexOf(':'));
        }
        while (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.trim();
    }

    public static List<String> parseList(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text != null) {
            for (String part : text.split("[,;\\s]+")) {
                String n = normalize(part);
                if (!n.isEmpty()) {
                    out.add(n);
                }
            }
        }
        return new ArrayList<>(out);
    }

    public static String formatList(List<String> servers) {
        return servers == null ? "" : String.join(", ", servers);
    }
}
