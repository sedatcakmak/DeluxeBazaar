package me.sedattr.bazaarapi;

import de.tr7zw.changeme.nbtapi.NBT;
import me.sedattr.deluxebazaar.DeluxeBazaar;
import me.sedattr.deluxebazaar.managers.BazaarItem;
import me.sedattr.deluxebazaar.managers.OrderPrice;
import me.sedattr.deluxebazaar.others.PersistentDataContainerUtils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BazaarItemHook {
    public static BazaarItem getBazaarItem(String name) {
        return DeluxeBazaar.getInstance().bazaarItems.getOrDefault(name, null);
    }

    public static boolean isSimilar(ItemStack item1, ItemStack item2) {
        if (item1.getType() != item2.getType())
            return false;
        if (item1.getMaxStackSize() != item2.getMaxStackSize())
            return false;

        ItemMeta meta1 = item1.getItemMeta();
        ItemMeta meta2 = item2.getItemMeta();
        if (meta1 == null && meta2 != null)
            return false;
        if (meta2 == null && meta1 != null)
            return false;

        if (DeluxeBazaar.getInstance().version < 13) {
            if (item1.getDurability() != item2.getDurability())
                return false;
        } else {
            if (meta1.hasCustomModelData() && meta2.hasCustomModelData() && meta1.getCustomModelData() > 0 && meta2.getCustomModelData() > 0 && meta1.getCustomModelData() != meta2.getCustomModelData())
                return false;
        }

        if (meta1 != null && meta2 != null) {
            String displayName1 = meta1.getDisplayName();
            String displayName2 = meta2.getDisplayName();
            if (displayName1 == null && displayName2 == null)
                return true;
            if (displayName1 == null && displayName2 != null)
                return false;
            if (displayName2 == null && displayName1 != null)
                return false;
            if (displayName1 != null && displayName2 != null && !displayName1.equals(displayName2))
                return false;

            List<String> lore1 = meta1.getLore();
            List<String> lore2 = meta2.getLore();
            if (lore1 == null && lore2 == null)
                return true;
            if (lore1 == null)
                return false;
            if (lore2 == null)
                return false;
            if (lore1.isEmpty() && lore2.isEmpty())
                return true;
            if (lore1.isEmpty())
                return false;
            if (lore2.isEmpty())
                return false;

            return lore1.equals(lore2);
        }

        return true;
    }

    public static String getItemName(ItemStack item) {
        if (item == null || item.getType().equals(Material.AIR))
            return null;

        String nbt = NBT.itemStackToNBT(item).getString("BazaarITEM");
        if (nbt != null && !nbt.isEmpty()) {
            ConfigurationSection itemSection = DeluxeBazaar.getInstance().itemsFile.getConfigurationSection("items." + nbt);
            if (itemSection != null)
                return nbt;
        }

        ConcurrentHashMap<String, BazaarItem> bazaarItems = DeluxeBazaar.getInstance().bazaarItems;
        if (bazaarItems.isEmpty())
            return null;

        for (Map.Entry<String, BazaarItem> entry : bazaarItems.entrySet()) {
            BazaarItem bazaarItem = entry.getValue();
            if (bazaarItem == null)
                continue;

            ItemStack itemStack = bazaarItem.getItemStack();
            if (itemStack==null)
                continue;

            if (DeluxeBazaar.getInstance().version > 14) {
                ConfigurationSection section = DeluxeBazaar.getInstance().itemsFile.getConfigurationSection("items." + entry.getKey() + ".persistent");
                if (section != null && PersistentDataContainerUtils.isCustomItem(itemStack, section) && PersistentDataContainerUtils.isCustomItem(item, section))
                    return entry.getKey();
            }

            if (isSimilar(item, itemStack))
                return entry.getKey();
        }

        return null;
    }

    public static double getBuyPrice(Player player, String name, int amount) {
        double totalPrice = 0.0;
        int totalAmount = amount;
        double defaultPrice = getDefaultBuyPrice(name);

        BazaarItem bazaarItem = BazaarItemHook.getBazaarItem(name);
        if (bazaarItem == null)
            return 0.0;

        List<OrderPrice> orderPrices = bazaarItem.getSellPrices();
        if (!orderPrices.isEmpty()) {
            orderPrices.sort(Comparator.comparing(OrderPrice::getPrice));
            for (OrderPrice order : orderPrices) {
                if (order == null)
                    continue;
                if (totalAmount <= 0)
                    break;

                double price = order.getPrice();
                int itemAmount = order.getItemAmount() - DeluxeBazaar.getInstance().orderHandler.getItemCount(player, "buy", name, price);
                if (itemAmount <= 0)
                    continue;

                if (totalAmount >= itemAmount) {
                    totalPrice += price * itemAmount;
                    totalAmount -= itemAmount;
                } else {
                    totalPrice += price * totalAmount;
                    totalAmount = 0;
                }
            }
        }

        if (totalAmount > 0)
            totalPrice += totalAmount * defaultPrice;

        return totalPrice;
    }

    public static double getSellPrice(Player player, String name, int amount) {
        double totalPrice = 0.0;
        int totalAmount = amount;
        double defaultPrice = getDefaultSellPrice(name);

        BazaarItem bazaarItem = BazaarItemHook.getBazaarItem(name);
        if (bazaarItem == null)
            return 0.0;

        List<OrderPrice> orderPrices = bazaarItem.getBuyPrices();
        if (!orderPrices.isEmpty()) {
            orderPrices.sort(Comparator.comparing(OrderPrice::getPrice));
            Collections.reverse(orderPrices);
            for (OrderPrice order : orderPrices) {
                if (order == null)
                    continue;
                if (totalAmount <= 0)
                    break;

                double price = order.getPrice();
                int itemAmount = order.getItemAmount() - DeluxeBazaar.getInstance().orderHandler.getItemCount(player, "sell", name, price);
                if (itemAmount <= 0)
                    continue;

                if (totalAmount >= itemAmount) {
                    totalPrice += price * itemAmount;
                    totalAmount -= itemAmount;
                } else {
                    totalPrice += price * totalAmount;
                    totalAmount = 0;
                }
            }
        }

        if (totalAmount > 0)
            totalPrice += totalAmount * defaultPrice;

        return totalPrice;
    }

    public static Double getDefaultBuyPrice(String item) {
        BazaarItem bazaarItem = DeluxeBazaar.getInstance().bazaarItems.get(item);
        if (bazaarItem == null)
            return 0.0;

        double price = bazaarItem.getBuyPrice();
        if (price <= 0.0)
            return DeluxeBazaar.getInstance().itemsFile.getDouble("items." + item + ".prices.buy", 0.0);
        else
            return price;
    }

    public static Double getDefaultSellPrice(String item) {
        BazaarItem bazaarItem = DeluxeBazaar.getInstance().bazaarItems.get(item);
        if (bazaarItem == null)
            return 0.0;

        double price = bazaarItem.getSellPrice();
        if (price <= 0.0)
            return DeluxeBazaar.getInstance().itemsFile.getDouble("items." + item + ".prices.sell", 0.0);
        else
            return price;
    }

    public static void setBuyPrice(String item, Double amount) {
        BazaarItem bazaarItem = DeluxeBazaar.getInstance().bazaarItems.get(item);
        if (bazaarItem == null)
            return;

        bazaarItem.setBuyPrice(amount);
    }

    public static void setSellPrice(String item, Double amount) {
        BazaarItem bazaarItem = DeluxeBazaar.getInstance().bazaarItems.get(item);
        if (bazaarItem == null)
            return;

        bazaarItem.setSellPrice(amount);
    }

    public static Integer getBuyCount(String item) {
        BazaarItem bazaarItem = DeluxeBazaar.getInstance().bazaarItems.get(item);
        if (bazaarItem == null)
            return 0;

        return bazaarItem.getTotalBuyCount();
    }

    public static Integer getSellCount(String item) {
        BazaarItem bazaarItem = DeluxeBazaar.getInstance().bazaarItems.get(item);
        if (bazaarItem == null)
            return 0;

        return bazaarItem.getTotalSellCount();
    }
}