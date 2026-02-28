package me.sedattr.deluxebazaar.handlers;

import me.sedattr.deluxebazaar.DeluxeBazaar;
import me.sedattr.bazaarapi.BazaarItemHook;
import me.sedattr.bazaarapi.events.BazaarItemBuyEvent;
import me.sedattr.bazaarapi.events.BazaarItemSellEvent;
import me.sedattr.deluxebazaar.managers.BazaarItem;
import me.sedattr.deluxebazaar.others.PlaceholderUtil;
import me.sedattr.deluxebazaar.others.Utils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class EconomyHandler {
    public Boolean event(Player player, String type, BazaarItem item, int count) {
        if (!type.equalsIgnoreCase("buy") && !type.equalsIgnoreCase("sell"))
            return false;

        if (!Utils.checkItemPermission(player, item.getName())) {
            Utils.sendMessage(player, "no_permission_for_item");
            return false;
        }

        if (type.equalsIgnoreCase("buy") ? BazaarItemHook.getDefaultBuyPrice(item.getName()) <= 0.0 : BazaarItemHook.getDefaultSellPrice(item.getName()) <= 0.0) {
            String itemName = DeluxeBazaar.getInstance().itemsFile.getString("items." + item.getName() + ".name");
            Utils.sendMessage(player, type.equalsIgnoreCase("buy") ? "buying_disabled_for_item" : "selling_disabled_for_item", new PlaceholderUtil()
                    .addPlaceholder("%item_name%", Utils.strip(itemName))
                    .addPlaceholder("%item_name_colored%", Utils.colorize(itemName)));
            return false;
        }

        int maximumAmount = DeluxeBazaar.getInstance().configFile.getInt("settings.maximum_item_amount");
        if (maximumAmount > 0 && count > maximumAmount) {
            Utils.sendMessage(player, "maximum_amount", new PlaceholderUtil().addPlaceholder("%maximum_amount%", String.valueOf(maximumAmount)));
            return false;
        }

        if (DeluxeBazaar.getInstance().orderHandler.isStockEnabled(item.getName(), type)) {
            int totalCount = DeluxeBazaar.getInstance().orderHandler.getStockCount(player, item.getName(), type);

            if (count > totalCount) {
                Utils.sendMessage(player, "not_enough_stock", new PlaceholderUtil()
                        .addPlaceholder("%item_amount%", String.valueOf(count))
                        .addPlaceholder("%total_amount%", String.valueOf(totalCount))
                        .addPlaceholder("%required_amount%", String.valueOf(count-totalCount)));
                return false;
            }
        }

        return type.equalsIgnoreCase("buy") ? buyEvent(player, item, count, true) : sellEvent(player, item, count, true);
    }

    public Boolean buyEvent(Player player, BazaarItem item, Integer amount, Boolean sendMessages) {
        if (item == null)
            return false;

        if (amount < 1)
            return false;

        double price = BazaarItemHook.getBuyPrice(player, item.getName(), amount);
        double balance = DeluxeBazaar.getInstance().economyManager.getBalance(player);

        ConfigurationSection itemSection = DeluxeBazaar.getInstance().itemsFile.getConfigurationSection("items."+item.getName());
        PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                .addPlaceholder("%item_name_colored%", Utils.colorize(itemSection.getString("name")))
                .addPlaceholder("%item_name%", Utils.strip(itemSection.getString("name")))
                .addPlaceholder("%total_price%", DeluxeBazaar.getInstance().numberFormat.format(price))
                .addPlaceholder("%required_money%", DeluxeBazaar.getInstance().numberFormat.format(price - balance))
                .addPlaceholder("%total_amount%", String.valueOf(amount));

        if (balance < price) {
            if (sendMessages)
                Utils.sendMessage(player, "not_enough_money", placeholderUtil);
            return false;
        }

        ItemStack itemStack = item.getItemStack();
        if (itemStack == null)
            return false;
        itemStack = itemStack.clone();

        boolean isNormal = itemSection.getBoolean("normal");

        int empty = DeluxeBazaar.getInstance().itemHandler.getEmptySlots(player, itemStack);
        if (empty < amount) {
            if (sendMessages)
                Utils.sendMessage(player, "no_empty_slot");
            return false;
        }

        // Fire event BEFORE removing balance to prevent money loss on cancel
        BazaarItemBuyEvent event = new BazaarItemBuyEvent(player, item, price/amount, amount);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled())
            return false;

        if (!DeluxeBazaar.getInstance().economyManager.removeBalance(player, price)) {
            if (sendMessages)
                Utils.sendMessage(player, "remove_error");
            return false;
        }

        if (isNormal)
            itemStack = new ItemStack(itemStack.getType(), 1, (short) itemSection.getInt("data"));

        DeluxeBazaar.getInstance().itemHandler.giveBazaarItems(player, itemStack, amount);

        item.addBuyCount(amount);
        Utils.executeCommands(player, item.getName(), "itemCommands.buy");
        Utils.playSound(player, "bought_item");

        DeluxeBazaar.getInstance().dataHandler.writeToLog("[PLAYER BOUGHT ITEM] " + player.getName() + " (" + player.getUniqueId() + ") bought " + (amount) + "x " + item.getName() + " for total " + (price) + " coins.");
        if (sendMessages) {
            Utils.sendMessage(player, "bought", placeholderUtil);
            if (DeluxeBazaar.getInstance().discordWebhook != null)
                DeluxeBazaar.getInstance().discordWebhook.sendMessage("bought_item", placeholderUtil
                .addPlaceholder("%player_name%", player.getName())
                .addPlaceholder("%player_displayname%", player.getDisplayName()));
        }

        DeluxeBazaar.getInstance().itemHandler.updateBazaarItem(player, item.getName());
        return true;
    }

    public Boolean sellEvent(Player player, BazaarItem item, int count, Boolean sendMessages) {
        if (count < 1) {
            if (sendMessages)
                Utils.sendMessage(player, "no_item_found");
            return false;
        }

        ConfigurationSection itemSection = DeluxeBazaar.getInstance().itemsFile.getConfigurationSection("items."+item.getName());
        PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                .addPlaceholder("%item_name_colored%", Utils.colorize(itemSection.getString("name")))
                .addPlaceholder("%item_name%", Utils.strip(itemSection.getString("name")));

        double price = BazaarItemHook.getSellPrice(player, item.getName(), count);
        if (price <= 0) {
            if (sendMessages)
                Utils.sendMessage(player, "wrong_item", placeholderUtil);
            return false;
        }

        List<ItemStack> itemList = DeluxeBazaar.getInstance().itemHandler.getSellableBazaarItems(player, item.getName());
        int itemCount1 = itemList == null || itemList.isEmpty() ? 0 : itemList.stream().mapToInt(ItemStack::getAmount).sum();

        if (itemCount1 < count) {
            if (sendMessages)
                Utils.sendMessage(player, "not_enough_item", placeholderUtil
                        .addPlaceholder("%required_amount%", String.valueOf(count - itemCount1)));
            return false;
        }

        // Fire event BEFORE any state changes to prevent free money on cancel
        BazaarItemSellEvent itemSellEvent = new BazaarItemSellEvent(player, item, price/count, count);
        Bukkit.getPluginManager().callEvent(itemSellEvent);
        if (itemSellEvent.isCancelled())
            return false;

        int itemCount = 0;
        boolean enabled = false;
        for (ItemStack is : itemList) {
            if (enabled)
                break;
            int amount = is.getAmount();

            if (itemCount + amount <= count) {
                player.getInventory().removeItem(is);
                itemCount += amount;

                if (itemCount == count)
                    enabled = true;
            } else if (itemCount + amount > count) {
                int newAmount = amount - (count - itemCount);

                is.setAmount(newAmount);
                player.updateInventory();

                enabled = true;
            }
        }

        // Add balance AFTER items are removed from inventory
        if (!DeluxeBazaar.getInstance().economyManager.addBalance(player, price)) {
            // Rollback: give items back
            ItemStack rollbackItem = item.getItemStack();
            if (rollbackItem != null)
                DeluxeBazaar.getInstance().itemHandler.giveBazaarItems(player, rollbackItem.clone(), count);
            if (sendMessages)
                Utils.sendMessage(player, "add_error");
            return false;
        }

        item.addSellCount(count);
        Utils.executeCommands(player, item.getName(), "itemCommands.sell");
        Utils.playSound(player, "sold_item");

        if (sendMessages) {
            DeluxeBazaar.getInstance().dataHandler.writeToLog("[PLAYER SOLD ITEM] " + player.getName() + " (" + player.getUniqueId() + ") sold " + (count) + "x " + item.getName() + " for total " + (price) + " coins.");

            placeholderUtil
                    .addPlaceholder("%total_price%", DeluxeBazaar.getInstance().numberFormat.format(price))
                    .addPlaceholder("%total_amount%", String.valueOf(count));

            Utils.sendMessage(player, "sold", placeholderUtil);

            if (DeluxeBazaar.getInstance().discordWebhook != null)
                DeluxeBazaar.getInstance().discordWebhook.sendMessage("sold_item", placeholderUtil
                        .addPlaceholder("%player_name%", player.getName())
                        .addPlaceholder("%player_displayname%", player.getDisplayName()));
        }

        DeluxeBazaar.getInstance().itemHandler.updateBazaarItem(player, item.getName());
        return true;
    }
}
