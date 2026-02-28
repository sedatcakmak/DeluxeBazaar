package me.sedattr.deluxebazaar.economy;

import me.sedattr.deluxebazaar.DeluxeBazaar;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class YamlEconomy implements EconomyManager {
    private final Object lock = new Object();

    public String replace(OfflinePlayer player, String text) {
        String message = DeluxeBazaar.getInstance().configFile.getString(text);
        if (message == null || message.isEmpty())
            return "";

        return message
                .replace("%player_uuid%", player.getUniqueId().toString())
                .replace("%player_name%", player.getName());
    }

    public boolean addBalance(OfflinePlayer player, Double count) {
        synchronized (lock) {
            File file = new File(replace(player, "economy.yaml_settings.folder_name"), replace(player, "default.economy.yaml.file"));
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            Double oldCount = config.getDouble(replace(player, "economy.yaml_settings.node_text"));

            config.set(replace(player, "economy.yaml_settings.node_text"), oldCount + count);
            try {
                config.save(file);
                return true;
            } catch (IOException e) {
                return false;
            }
        }
    }

    @Override
    public boolean removeBalance(OfflinePlayer player, Double count) {
        synchronized (lock) {
            File file = new File(replace(player, "economy.yaml_settings.folder_name"), replace(player, "default.economy.yaml.file"));
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            Double oldCount = config.getDouble(replace(player, "economy.yaml_settings.node_text"));

            if (oldCount < count) return false;

            config.set(replace(player, "economy.yaml_settings.node_text"), oldCount - count);
            try {
                config.save(file);
                return true;
            } catch (IOException e) {
                return false;
            }
        }
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        synchronized (lock) {
            File file = new File(replace(player, "economy.yaml_settings.folder_name"), replace(player, "default.economy.yaml.file"));
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

            return config.getDouble(replace(player, "economy.yaml_settings.node_text"));
        }
    }
}
