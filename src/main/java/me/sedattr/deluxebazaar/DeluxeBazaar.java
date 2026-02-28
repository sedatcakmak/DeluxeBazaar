package me.sedattr.deluxebazaar;

import lombok.Getter;
import me.sedattr.deluxebazaar.inventoryapi.inventory.InventoryAPI;
import me.sedattr.deluxebazaar.addons.*;
import me.sedattr.deluxebazaar.commands.BazaarAdminCommand;
import me.sedattr.deluxebazaar.commands.BazaarCommand;
import me.sedattr.deluxebazaar.configupdater.ConfigUpdater;
import me.sedattr.deluxebazaar.database.DatabaseManager;
import me.sedattr.deluxebazaar.economy.EconomyManager;
import me.sedattr.deluxebazaar.handlers.*;
import me.sedattr.deluxebazaar.listeners.BazaarListeners;
import me.sedattr.deluxebazaar.listeners.InventoryListeners;
import me.sedattr.deluxebazaar.listeners.ItemListeners;
import me.sedattr.deluxebazaar.managers.BazaarItem;
import me.sedattr.deluxebazaar.managers.PlayerBazaar;
import me.sedattr.deluxebazaar.menus.InputMenu;
import me.sedattr.deluxebazaar.others.Logger;
import me.sedattr.deluxebazaar.others.Metrics;
import me.sedattr.deluxebazaar.others.NumberFormat;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;

@SuppressWarnings("ConstantConditions")
public class DeluxeBazaar extends JavaPlugin {
    @Getter private static DeluxeBazaar instance;

    public Economy economy;
    public EconomyManager economyManager;
    public DatabaseManager databaseManager;

    public MenuHandler menuHandler;
    public EconomyHandler economyHandler;
    public ItemHandler itemHandler;
    public OrderHandler orderHandler;
    public DataHandler dataHandler;

    public java.util.concurrent.ConcurrentHashMap<UUID, PlayerBazaar> players = new java.util.concurrent.ConcurrentHashMap<>();
    public java.util.concurrent.ConcurrentHashMap<String, BazaarItem> bazaarItems = new java.util.concurrent.ConcurrentHashMap<>();
    public java.util.concurrent.ConcurrentHashMap<String, ItemStack> normalItems = new java.util.concurrent.ConcurrentHashMap<>();

    public NumberFormat numberFormat;
    public Integer version;

    public InputMenu inputMenu;
    public HeadDatabase headDatabase;
    public boolean placeholderApi = false;
    public DiscordWebhook discordWebhook = null;
    public EcoItemsAddon ecoItemsAddon = null;
    public MMOItemsAddon mmoItemsAddon = null;
    public ExecutableItemsAddon executableItemsAddon = null;
    public boolean started = false;
    public double fileVersions = 4.7;

    public YamlConfiguration itemsFile;
    public YamlConfiguration menusFile;
    public FileConfiguration configFile;
    public YamlConfiguration messagesFile;
    public YamlConfiguration categoriesFile;

    @Override
    public void onEnable() {
        instance = this;
        this.version = Integer.valueOf(Bukkit.getBukkitVersion().substring(2, 4).replace(".", ""));

        saveDefaultConfig();

        this.dataHandler = new DataHandler();
        if (!this.dataHandler.load())
            return;

        registerCommandsListeners();
        InventoryAPI.setup(DeluxeBazaar.this);

        String version = "1." + this.version;
        Logger.sendConsoleMessage("Your server is running on &f" + version + "%level_color%.", Logger.LogLevel.INFO);

        long time = System.currentTimeMillis();
        dataHandler.setupDatabase();

        Logger.sendConsoleMessage("Database successfully loaded! Took &f" + (System.currentTimeMillis() - time) + "ms %level_color%to complete!", Logger.LogLevel.INFO);

        double currentVersions = DeluxeBazaar.getInstance().getConfig().getDouble("file_versions", 1.0);
        if (this.fileVersions > currentVersions) {
            long start = System.currentTimeMillis();

            File messagesFile = new File(getDataFolder(), "messages.yml");
            if (messagesFile.exists()) {
                try {
                    File oldBackupFile = new File(getDataFolder() + File.separator + "backups", "messages_backup.yml");
                    if (!oldBackupFile.exists()) {
                        new File(getDataFolder() + File.separator + "backups").mkdirs();

                        Files.copy(Paths.get(messagesFile.getPath()), Paths.get(getDataFolder() + File.separator + "backups" + File.separator + "messages_backup.yml"));
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            File configFile = new File(getDataFolder(), "config.yml");
            if (configFile.exists()) {
                try {
                    File oldBackupFile = new File(getDataFolder() + File.separator + "backups", "config_backup.yml");
                    if (!oldBackupFile.exists()) {
                        new File(getDataFolder() + File.separator + "backups").mkdirs();

                        Files.copy(Paths.get(configFile.getPath()), Paths.get(getDataFolder() + File.separator + "backups" + File.separator + "config_backup.yml"));
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            try {
                ConfigUpdater.update(DeluxeBazaar.getInstance(), "messages.yml", messagesFile);
                ConfigUpdater.update(DeluxeBazaar.getInstance(), "config.yml", configFile, Collections.singletonList("item_name_detection.normal_items"));
            } catch (IOException e) {
                e.printStackTrace();
            }

            this.dataHandler.createDefaultFiles();

            Logger.sendConsoleMessage("It looks like you are using old versions' files! &7(%level_color%Current/New Version: &f" + currentVersions + "/" + this.fileVersions + "&7)", Logger.LogLevel.WARN);
            Logger.sendConsoleMessage("Config and messages file is successfully updated! (Old files backed up)", Logger.LogLevel.INFO);
            Logger.sendConsoleMessage("Took &f" + (System.currentTimeMillis() - start) + "ms %level_color%to complete.", Logger.LogLevel.INFO);
        }

        new Metrics(this, 19196);
        Logger.sendConsoleMessage("Plugin is enabled! Plugin Version: &fv" + getDescription().getVersion(), Logger.LogLevel.INFO);
        started = true;

        this.dataHandler.startDataTask();
    }

    @Override
    public void onDisable() {
        if (started) {
            Logger.sendConsoleMessage("Saving all datas to database...", Logger.LogLevel.WARN);
            long time = System.currentTimeMillis();
            databaseManager.saveDatabase();

            Logger.sendConsoleMessage("Database successfully saved! Took &f" + (System.currentTimeMillis() - time) + "ms %level_color%to complete!", Logger.LogLevel.WARN);
        }

        Logger.sendConsoleMessage("Plugin is disabled! &8(&7sedattr was here...&8)", Logger.LogLevel.WARN);
    }

    public void registerCommandsListeners() {
        PluginCommand bazaar = DeluxeBazaar.getInstance().getCommand("bazaar");
        if (bazaar != null)
            bazaar.setExecutor(new BazaarCommand());
        PluginCommand admin = DeluxeBazaar.getInstance().getCommand("bazaaradmin");
        if (admin != null)
            admin.setExecutor(new BazaarAdminCommand());

        Bukkit.getPluginManager().registerEvents(new ItemListeners(), DeluxeBazaar.getInstance());
        Bukkit.getPluginManager().registerEvents(new BazaarListeners(), DeluxeBazaar.getInstance());
        Bukkit.getPluginManager().registerEvents(new InventoryListeners(), DeluxeBazaar.getInstance());
    }


}