package me.sedattr.deluxebazaar.others;

import com.google.common.collect.ImmutableMultimap;
import me.sedattr.deluxebazaar.DeluxeBazaar;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    String username = "%%__USERNAME__%%";

    private static String sanitizeForCommand(String input) {
        if (input == null) return "";
        return input.replaceAll("[;&|`$(){}\\[\\]!#]", "")
                    .replaceAll("\\s+", " ")
                    .trim();
    }

    public static boolean checkPermission(CommandSender player, String type, String text) {
        if (player.isOp())
            return true;

        String permission = DeluxeBazaar.getInstance().configFile.getString("permissions." + type + "." + text);
        if (permission == null || permission.isEmpty())
            return true;

        return player.hasPermission(permission);
    }


    public static boolean checkSearchPermission(Player player) {
        if (player.isOp())
            return true;

        ConfigurationSection searchSection = DeluxeBazaar.getInstance().configFile.getConfigurationSection("permissions.search");
        if (searchSection == null)
            return true;
        if (!searchSection.getBoolean("enabled", false))
            return true;

        String permission = searchSection.getString("permission");
        if (permission == null || permission.isEmpty())
            return true;

        return player.hasPermission(permission);
    }

    public static boolean checkCategoryPermission(Player player, String category) {
        if (player.isOp())
            return true;

        ConfigurationSection categorySection = DeluxeBazaar.getInstance().configFile.getConfigurationSection("permissions.category");
        if (categorySection == null)
            return true;
        if (!categorySection.getBoolean("enabled", false))
            return true;

        String permission = categorySection.getString("permission");
        if (permission == null || permission.isEmpty())
            return true;

        List<String> categories = categorySection.getStringList("category_list");
        if (categories.isEmpty())
            return true;

        if (!categories.contains(category))
            return true;

        return player.hasPermission(permission
                .replace("%category_name%", category));
    }

    public static boolean checkItemPermission(Player player, String item) {
        if (player.isOp())
            return true;

        ConfigurationSection itemSection = DeluxeBazaar.getInstance().configFile.getConfigurationSection("permissions.item");
        if (itemSection == null)
            return true;
        if (!itemSection.getBoolean("enabled", false))
            return true;

        String permission = itemSection.getString("permission");
        if (permission == null || permission.equals(""))
            return true;

        List<String> items = itemSection.getStringList("item_list");
        if (items.isEmpty())
            return true;

        if (!items.contains(item))
            return true;

        return player.hasPermission(permission
                .replace("%item_name%", item));
    }

    public static String strip(String text) {
        if (text == null || text.isEmpty())
            return "";

        text = Utils.colorize(text);
        return ChatColor.stripColor(text);
    }

    public static void addMenuItemFlags(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;

        List<String> flags = DeluxeBazaar.getInstance().configFile.getStringList("settings.menu_item_flags");
        if (!flags.isEmpty()) {
            if (flags.contains("ALL")) {
                if (DeluxeBazaar.getInstance().version > 13)
                    meta.setAttributeModifiers(ImmutableMultimap.of());

                meta.addItemFlags(ItemFlag.values());
            }
            else
                for (String flag : flags)
                    meta.addItemFlags(ItemFlag.valueOf(flag));
        }

        item.setItemMeta(meta);
    }

    public static void changeName(ItemStack item, String name, PlaceholderUtil placeholderUtil) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;

        meta.setDisplayName(Utils.colorize(replacePlaceholders(name, placeholderUtil)));

        item.setItemMeta(meta);
    }

    public static void changeLore(ItemStack item, List<String> lore, PlaceholderUtil placeholderUtil) {
        if (item == null)
            return;
        if (lore.isEmpty())
            return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;

        List<String> newLore = new ArrayList<>();

        for (String line : lore)
            newLore.add(Utils.colorize(replacePlaceholders(line, placeholderUtil)));

        meta.setLore(newLore);
        item.setItemMeta(meta);
    }

    public static void executeCommands(Player player, String item, String type) {
        List<String> commands = DeluxeBazaar.getInstance().itemsFile.getStringList("items." + item + "." + type);
        if (commands.isEmpty())
            return;

        for (String command : commands) {
            command = placeholderApi(player, command
                    .replace("%player_displayname%", sanitizeForCommand(player.getDisplayName()))
                    .replace("%player_name%", sanitizeForCommand(player.getName()))
                    .replace("%player_uuid%", String.valueOf(player.getUniqueId())));

            if (command.startsWith("[player]"))
                player.performCommand(command
                        .replace("[player] ", "")
                        .replace("[player]", ""));
            else
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                        .replace("[console] ", "")
                        .replace("[console]", ""));
        }
    }

    public static String colorize(String s) {
        if (s == null || s.isEmpty())
            return "";

        if (!Bukkit.getVersion().contains("1.16") && !Bukkit.getVersion().contains("1.17") && !Bukkit.getVersion().contains("1.18") && !Bukkit.getVersion().contains("1.19") && !Bukkit.getVersion().contains("1.20") && !Bukkit.getVersion().contains("1.21") && !Bukkit.getVersion().contains("1.22"))
            return ChatColor.translateAlternateColorCodes('&', s);

        Pattern pattern = Pattern.compile("#[a-fA-F0-9]{6}");
        Matcher match = pattern.matcher(s);
        while (match.find()) {
            String hexColor = s.substring(match.start(), match.end());
            s = s.replace(hexColor, ChatColor.of(hexColor).toString());
            match = pattern.matcher(s);
        }

        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static String replacePlaceholders(String message, PlaceholderUtil placeholderUtil) {
        if (placeholderUtil == null)
            return message;

        HashMap<String, String> placeholders = placeholderUtil.getPlaceholders();
        if (placeholders != null && !placeholders.isEmpty())
            for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
                String key = placeholder.getKey();
                String value = placeholder.getValue();

                message = message
                        .replace(key, value);
            }

        return message;
    }

    public static void playSound(Player player, String type) {
        if (type == null || type.isEmpty())
            return;

        ConfigurationSection section = DeluxeBazaar.getInstance().configFile.getConfigurationSection("sounds." + type);
        if (section == null)
            return;
        if (!section.getBoolean("enabled"))
            return;

        String name = section.getString("name");
        if (name == null || name.isEmpty())
            return;

        Sound sound = Sound.valueOf(name);
        if (sound == null)
            return;

        player.playSound(player.getLocation(), sound, (float) section.getDouble("volume", 1.0), (float) section.getDouble("pitch", 1.0));
    }

    public static boolean sendMessage(CommandSender player, String text, PlaceholderUtil placeholderUtil) {
        if (player == null)
            return false;

        List<String> messageList = DeluxeBazaar.getInstance().messagesFile.getStringList(text);
        if (messageList.isEmpty()) {
            String message = DeluxeBazaar.getInstance().messagesFile.getString(text);
            if (message == null) {
                Logger.sendConsoleMessage("I can't find message &f" + text + " &ein config!", Logger.LogLevel.WARN);
                return false;
            }
            if (message.equals(""))
                return false;

            player.sendMessage(placeholderApi(player, colorize(replacePlaceholders(message, placeholderUtil))));
        } else
            for (String message : messageList)
                player.sendMessage(placeholderApi(player, colorize(replacePlaceholders(message, placeholderUtil))));

        return true;
    }

    public static String getDisplayName(ItemStack item) {
        String itemName;
        if (item.getItemMeta().getDisplayName() != null)
            itemName = item.getItemMeta().getDisplayName();
        else
            itemName = org.bukkit.ChatColor.WHITE + capitalize(item.getType().name().replace("_ITEM", "").replace("_", " "));

        return itemName;
    }

    public static String placeholderApi(CommandSender player, String message) {
        if (!(player instanceof Player))
            return message;
        if (!DeluxeBazaar.getInstance().placeholderApi)
            return message;

        return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders((Player) player, message);
    }

    public static String capitalize(String text) {
        String c = (text != null) ? text.trim() : "";
        String[] words = c.split(" ");

        StringBuilder result = new StringBuilder();
        for (String w : words)
            result.append(w.length() > 1 ? w.substring(0, 1).toUpperCase(Locale.US) + w.substring(1).toLowerCase(Locale.US) : w).append(" ");

        return result.toString().trim();
    }

    public static boolean sendMessage(CommandSender player, String text) {
        if (player == null)
            return false;

        List<String> messageList = DeluxeBazaar.getInstance().messagesFile.getStringList(text);
        if (messageList.isEmpty()) {
            String message = DeluxeBazaar.getInstance().messagesFile.getString(text);

            if (message == null) {
                Logger.sendConsoleMessage("I can't find message &f" + text + " &ein config!", Logger.LogLevel.WARN);
                return false;
            }
            if (message.equals(""))
                return false;

            player.sendMessage(placeholderApi(player, colorize(message)));
        } else
            for (String message : messageList)
                player.sendMessage(placeholderApi(player, colorize(message)));

        return true;
    }

    public static ItemStack createItemFromSection(ConfigurationSection section, PlaceholderUtil placeholderUtil) {
        if (section == null)
            return null;

        Object object = section.get("item");
        if (object instanceof ItemStack)
            return (ItemStack) object;

        String executableItems = section.getString("executable_items");
        if (executableItems != null && !executableItems.isEmpty() && DeluxeBazaar.getInstance().executableItemsAddon != null) {
            ItemStack itemStack = DeluxeBazaar.getInstance().executableItemsAddon.getExecutableItem(executableItems);
            if (itemStack != null)
                return itemStack;
        }

        ConfigurationSection mmoItems = section.getConfigurationSection("mmo_items");
        if (mmoItems != null && DeluxeBazaar.getInstance().mmoItemsAddon != null) {
            String type = mmoItems.getString("type");
            String name = mmoItems.getString("name");
            ItemStack itemStack = DeluxeBazaar.getInstance().mmoItemsAddon.getMMOItem(type, name);
            if (itemStack != null)
                return itemStack;
        }

        String ecoItems = section.getString("eco_items");
        if (ecoItems != null && !ecoItems.isEmpty() && DeluxeBazaar.getInstance().ecoItemsAddon != null) {
            ItemStack itemStack = DeluxeBazaar.getInstance().ecoItemsAddon.getEcoItem(ecoItems);
            if (itemStack != null)
                return itemStack;
        }

        String materialName = section.getString("material");
        if (materialName == null)
            return null;

        Material material = MaterialHelper.getMaterial(materialName);
        if (material == null)
            return null;

        ItemStack item = null;
        if (material.name().toUpperCase().contains("SKULL_ITEM") || material.name().toUpperCase().contains("PLAYER_HEAD")) {
            String skin = section.getString("skin");
            if (skin != null && !skin.isEmpty())
                item = new SkullTexture().getSkull(material, section.getString("skin"));
        }

        if (item == null) {
            int data = section.getInt("data", 0);
            if (data != 0 && DeluxeBazaar.getInstance().version < 13)
                item = new ItemStack(material, Math.max(section.getInt("amount"), 1), (short) data);
            else
                item = new ItemStack(material, Math.max(section.getInt("amount"), 1));
        }

        ConfigurationSection persistent = section.getConfigurationSection("persistent");
        if (persistent != null && DeluxeBazaar.getInstance().version > 13)
            PersistentDataContainerUtils.setItem(item, persistent);

        ItemMeta meta = item.getItemMeta();
        String name = section.getString("name");
        boolean disableName = section.getBoolean("disable_name");
        if (name != null && !disableName)
            meta.setDisplayName(Utils.colorize(replacePlaceholders(name, placeholderUtil)));

        List<String> lore = section.getStringList("lore");
        if (!lore.isEmpty()) {
            List<String> newLore = new ArrayList<>();

            for (String line : lore)
                newLore.add(Utils.colorize(replacePlaceholders(line, placeholderUtil)));

            meta.setLore(newLore);
        }

        List<String> flags = section.getStringList("flags");
        if (!flags.isEmpty()) {
            if (flags.contains("ALL"))
                meta.addItemFlags(ItemFlag.values());
            else
                for (String flag : flags)
                    meta.addItemFlags(ItemFlag.valueOf(flag));
        }

        if (DeluxeBazaar.getInstance().version > 13 && section.getInt("model", 0) != 0)
            meta.setCustomModelData(section.getInt("model"));

        item.setItemMeta(meta);

        List<String> enchants = section.getStringList("enchants");
        if (!enchants.isEmpty())
            for (String enchant : enchants) {
                String[] args = enchant.split("[:]", 2);
                if (args.length < 2)
                    continue;

                Enchantment enchantment = Enchantment.getByName(args[0]);
                if (enchantment == null)
                    continue;

                item.addUnsafeEnchantment(enchantment, Integer.parseInt(args[1]));
            }

        return item;
    }
}
