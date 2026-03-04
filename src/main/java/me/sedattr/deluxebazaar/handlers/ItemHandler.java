package me.sedattr.deluxebazaar.handlers;

import com.google.common.collect.Maps;
import me.sedattr.deluxebazaar.DeluxeBazaar;
import me.sedattr.bazaarapi.BazaarItemHook;
import me.sedattr.deluxebazaar.managers.BazaarItem;
import me.sedattr.deluxebazaar.managers.PlayerBazaar;
import me.sedattr.deluxebazaar.others.PlaceholderUtil;
import me.sedattr.deluxebazaar.others.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ItemHandler {
    public int getSellableItemCount(Player player, String item) {
        PlayerBazaar playerBazaar = DeluxeBazaar.getInstance().players.getOrDefault(player.getUniqueId(), new PlayerBazaar(player));

        HashMap<String, Integer> items = playerBazaar.getSellableItems();
        if (items.isEmpty())
            return 0;

        if (item == null || item.isEmpty()) {
            /*
            List<Integer> allItems = new ArrayList<>();
            items.forEach((key, value) -> allItems.add(value));
             */

            AtomicInteger i = new AtomicInteger();
            items.forEach((a,b) -> i.addAndGet(b));
            return i.get();
        }

        return items.getOrDefault(item, 0);
    }

    public int getUpdatedItemCount(Player player, String name) {
        List<ItemStack> items = getSellableBazaarItems(player, name);
        if (items == null || items.isEmpty())
            return 0;

        return items.stream().mapToInt(ItemStack::getAmount).sum();
    }

    public List<ItemStack> getSellableBazaarItems(Player player, String itemName) {
        if (BazaarItemHook.getDefaultSellPrice(itemName) <= 0.0)
            return null;
        if (!Utils.checkItemPermission(player, itemName))
            return null;

        List<ItemStack> contents = getInventoryItems(player, true);
        List<ItemStack> sellable = new ArrayList<>();
        for (ItemStack item : contents) {
            if (item == null || item.getType().equals(Material.AIR))
                continue;

            String name = BazaarItemHook.getItemName(item);
            if (name == null)
                continue;
            if (!itemName.equals(name))
                continue;

            sellable.add(item);
        }

        return sellable;
    }

    public List<ItemStack> getInventoryItems(Player player, Boolean removeAirs) {
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> newItems = new ArrayList<>();

        // Sadece main inventory slotlarını iterate et (0-35)
        // Armor (36-39) ve offhand (40) otomatik hariç
        for (int i = 0; i < 36; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().equals(Material.AIR)) {
                if (removeAirs) continue;
                newItems.add(item);
                continue;
            }
            newItems.add(item);
        }
        return newItems;
    }

    public void sellBazaarItems(Player player, HashMap<String, Integer> itemList) {
        if (itemList == null || itemList.isEmpty()) {
            Utils.sendMessage(player, "no_sellable_item");
            return;
        }

        double totalPrice = 0.0;
        int itemCount = 0;

        String soldItem = DeluxeBazaar.getInstance().configFile.getString("addons.discord.sold_all_items.sold_item");
        StringJoiner text = new StringJoiner(", ");
        StringJoiner logText = new StringJoiner(", ");

        HashMap<String, Integer> newItemList = Maps.newHashMap();
        newItemList.putAll(itemList);

        for (Map.Entry<String, Integer> entry : newItemList.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();

            BazaarItem bazaarItem = DeluxeBazaar.getInstance().bazaarItems.get(key);
            Boolean result = DeluxeBazaar.getInstance().economyHandler.sellEvent(player, bazaarItem, value, false);
            if (!result)
                continue;

            double price = BazaarItemHook.getSellPrice(player, key, value);
            totalPrice += price;
            itemCount += value;

            if (DeluxeBazaar.getInstance().discordWebhook != null && soldItem != null && !soldItem.equals("")) {
                PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                        .addPlaceholder("%item_amount%", String.valueOf(value))
                        .addPlaceholder("%item_price%", DeluxeBazaar.getInstance().numberFormat.format(price));

                ConfigurationSection section = DeluxeBazaar.getInstance().itemsFile.getConfigurationSection("items." + key);
                if (section != null)
                    placeholderUtil
                            .addPlaceholder("%item_name_colored%", Utils.colorize(section.getString("name")))
                            .addPlaceholder("%item_name%", Utils.strip(section.getString("name")));

                text.add(Utils.replacePlaceholders(soldItem, placeholderUtil));
                logText.add(value + "x " + key + " (" + price + " coins)");
            }
        }

        PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                .addPlaceholder("%items%", text.toString())
                .addPlaceholder("%total_amount%", String.valueOf(itemCount))
                .addPlaceholder("%total_price%", DeluxeBazaar.getInstance().numberFormat.format(totalPrice));

        Utils.sendMessage(player, "sold_all", placeholderUtil);
        if (DeluxeBazaar.getInstance().discordWebhook != null)
            DeluxeBazaar.getInstance().discordWebhook.sendMessage("sold_all_items", placeholderUtil
                    .addPlaceholder("%player_name%", player.getName())
                    .addPlaceholder("%player_displayname%", player.getDisplayName()));

        DeluxeBazaar.getInstance().dataHandler.writeToLog("[PLAYER SOLD ALL] " + player.getName() + " (" + player.getUniqueId() + ") sold " + logText + " for total " + (totalPrice) + " coins.");
    }

    public void giveBazaarItems(Player player, ItemStack item, int amount) {
        PlayerInventory playerInventory = player.getInventory();
        List<Integer> emptySlots = emptySlotIds(player, item);
        item.setAmount(item.getMaxStackSize());
        int count = amount;

        for (Integer id : emptySlots) {
            ItemStack itemStack = playerInventory.getItem(id);
            if (itemStack == null || itemStack.getType() == Material.AIR) {
                if (count >= item.getMaxStackSize()) {
                    playerInventory.setItem(id, item);
                    count-=item.getMaxStackSize();
                } else {
                    item.setAmount(count);
                    if (item.getAmount() > 0)
                        playerInventory.setItem(id, item);
                    return;
                }
            } else {
                int leftAmount = itemStack.getMaxStackSize() - itemStack.getAmount();

                if (count >= leftAmount) {
                    itemStack.setAmount(item.getMaxStackSize());
                    if (itemStack.getAmount() > 0)
                        count-=leftAmount;
                } else {
                    itemStack.setAmount(Math.min(itemStack.getAmount()+count, itemStack.getMaxStackSize()));
                    return;
                }
            }
        }

       /*
        while (count > 0) {
            if (count >= item.getMaxStackSize()) {
                count-=item.getMaxStackSize();
                if (emptySlots.size() > i) {
                    playerInventory.setItem(emptySlots.get(i), item);
                    i++;
                } else
                    player.getWorld().dropItem(player.getLocation(), item);
            } else {
                item.setAmount(count);
                if (emptySlots.size() > i)
                    playerInventory.setItem(emptySlots.get(i), item);
                else
                    player.getWorld().dropItem(player.getLocation(), item);
                count=0;
            }
        }
        */

        /*
        while (count > 0) {
            for (Integer id : emptySlots) {
                ItemStack is = playerInventory.getItem(id);
                if (is == null || is.getType() == Material.AIR) {
                    playerInventory.setItem(id, item);
                }
            }
        }
         */
    }

    public void updateBazaarItem(Player player, String item) {
        PlayerBazaar playerBazaar = DeluxeBazaar.getInstance().players.getOrDefault(player.getUniqueId(), new PlayerBazaar(player));

        List<ItemStack> items = getSellableBazaarItems(player, item);
        int count = 0;
        if (items != null && !items.isEmpty())
            count = items.stream().mapToInt(ItemStack::getAmount).sum();

        HashMap<String, Integer> sellableItems = playerBazaar.getSellableItems();
        if (DeluxeBazaar.getInstance().orderHandler.isStockEnabled(item, "sell")) {
            int totalCount = DeluxeBazaar.getInstance().orderHandler.getStockCount(player, item, "sell");
            if (count > totalCount)
                count = totalCount;
        }

        if (count > 0)
            sellableItems.put(item, count);
        else
            sellableItems.remove(item);

        playerBazaar.setSellableItems(sellableItems);
    }
    public void updateSellableItems(Player player) {
        List<ItemStack> contents = getInventoryItems(player, true);
        HashMap<String, Integer> sellable = Maps.newHashMap();

        for (ItemStack item : contents) {
            if (item == null || item.getType().equals(Material.AIR))
                continue;

            String name = BazaarItemHook.getItemName(item);
            if (name == null)
                continue;
            if (BazaarItemHook.getDefaultSellPrice(name) <= 0.0)
                continue;
            if (!Utils.checkItemPermission(player, name))
                continue;

            int items = sellable.getOrDefault(name, 0);
            items += item.getAmount();

            sellable.put(name, items);
        }

        HashMap<String, Integer> newSellable = Maps.newHashMap();
        for (Map.Entry<String, Integer> entry : sellable.entrySet()) {
            if (DeluxeBazaar.getInstance().orderHandler.isStockEnabled(entry.getKey(), "sell")) {
                int totalCount = DeluxeBazaar.getInstance().orderHandler.getStockCount(player, entry.getKey(), "sell");
                if (totalCount <= 0)
                    continue;

                if (entry.getValue() > totalCount)
                    newSellable.put(entry.getKey(), totalCount);
                else
                    newSellable.put(entry.getKey(), entry.getValue());
            } else
                newSellable.put(entry.getKey(), entry.getValue());
        }

        PlayerBazaar playerBazaar = DeluxeBazaar.getInstance().players.getOrDefault(player.getUniqueId(), new PlayerBazaar(player.getUniqueId()));
        playerBazaar.setSellableItems(newSellable);
    }


    public List<Integer> emptySlotIds(Player player, ItemStack item) {
        int i;

        List<Integer> emptySlots = new ArrayList<>();
        PlayerInventory playerInventory = player.getInventory();
        for (i = 0; i < 36; i++) {
            ItemStack is = playerInventory.getItem(i);
            if (is == null || is.getType().equals(Material.AIR)) {
                emptySlots.add(i);
                continue;
            }
            if (!item.getType().equals(is.getType()))
                continue;

            if (item == is) {
                emptySlots.add(i);
                continue;
            }

            if (!BazaarItemHook.isSimilar(item, is))
                continue;

            emptySlots.add(i);
        }

        return emptySlots;
    }


    public int getEmptySlots(Player player, ItemStack item) {
        List<ItemStack> itemList = getInventoryItems(player, false);

        int i = 0;
        for (ItemStack is : itemList) {
            if (is == null || is.getType().equals(Material.AIR)) {
                i += item.getMaxStackSize();
                continue;
            }
            if (!item.getType().equals(is.getType()))
                continue;

            if (item == is) {
                i += is.getMaxStackSize() - is.getAmount();
                continue;
            }

            if (!BazaarItemHook.isSimilar(item, is))
                continue;

            i += (is.getMaxStackSize() - is.getAmount());
        }

        return Math.min(i, 2304);
    }
}
