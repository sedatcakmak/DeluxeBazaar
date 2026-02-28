package me.sedattr.deluxebazaar.menus;

import me.sedattr.deluxebazaar.DeluxeBazaar;
import me.sedattr.bazaarapi.BazaarItemHook;
import me.sedattr.bazaarapi.events.PlayerCreatedOrderEvent;
import me.sedattr.deluxebazaar.inventoryapi.HInventory;
import me.sedattr.deluxebazaar.inventoryapi.item.ClickableItem;
import me.sedattr.deluxebazaar.managers.BazaarItem;
import me.sedattr.deluxebazaar.managers.OrderType;
import me.sedattr.deluxebazaar.managers.PlayerBazaar;
import me.sedattr.deluxebazaar.managers.PlayerOrder;
import me.sedattr.deluxebazaar.others.PlaceholderUtil;
import me.sedattr.deluxebazaar.others.Utils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public class ConfirmMenu {
    private final ConfigurationSection section;
    private final Player player;

    public ConfirmMenu(Player player) {
        this.section = DeluxeBazaar.getInstance().menusFile.getConfigurationSection("menus.confirm");
        this.player = player;
    }

    public void openMenu(String name, String type, Integer amount, double unitPrice) {
        if (section == null)
            return;

        ConfigurationSection menuSection = this.section.getConfigurationSection(type);
        if (menuSection == null)
            return;

        String itemName = DeluxeBazaar.getInstance().itemsFile.getString("items." + name + ".name");
        PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                .addPlaceholder("%total_amount%", String.valueOf(amount))
                .addPlaceholder("%amount%", String.valueOf(amount))
                .addPlaceholder("%unit_price%", DeluxeBazaar.getInstance().numberFormat.format(unitPrice))
                .addPlaceholder("%unitprice%", DeluxeBazaar.getInstance().numberFormat.format(unitPrice))
                .addPlaceholder("%item_name_colored%", Utils.colorize(itemName))
                .addPlaceholder("%item_name%", Utils.strip(itemName))
                .addPlaceholder("%item%", Utils.strip(itemName))
                .addPlaceholder("%price%", DeluxeBazaar.getInstance().numberFormat.format(unitPrice * amount))
                .addPlaceholder("%total_price%", DeluxeBazaar.getInstance().numberFormat.format(unitPrice * amount));

        HInventory gui = DeluxeBazaar.getInstance().menuHandler.createInventory(player, menuSection, "confirm", placeholderUtil);
        BazaarItem bazaarItem = DeluxeBazaar.getInstance().bazaarItems.get(name);
        if (bazaarItem == null)
            return;

        ItemStack exampleItem = bazaarItem.getItemStack().clone();
        ItemStack newItem = exampleItem.clone();
        String displayname = menuSection.getString("item.name");
        if (displayname != null && !displayname.equals(""))
            Utils.changeName(newItem, Utils.colorize(displayname), placeholderUtil);

        List<String> lore = menuSection.getStringList("item.lore");
        if (!lore.isEmpty())
            Utils.changeLore(newItem, lore, placeholderUtil);
        Utils.addMenuItemFlags(newItem);

        gui.setItem(menuSection.getInt("item.slot") - 1, ClickableItem.of(newItem, (event) -> {
            if (type.equalsIgnoreCase("buy")) {
                int emptySlots = DeluxeBazaar.getInstance().itemHandler.getEmptySlots(player, exampleItem);
                if (emptySlots < 1) {
                    Utils.sendMessage(player, "no_empty_slot");
                    return;
                }

                DeluxeBazaar.getInstance().economyHandler.event(player, "buy", bazaarItem, amount);
                return;
            }

            if (type.equalsIgnoreCase("sell")) {
                int itemCount = DeluxeBazaar.getInstance().itemHandler.getUpdatedItemCount(player, name);
                if (itemCount < 1) {
                    Utils.sendMessage(player, "no_item_found");
                    return;
                }

                DeluxeBazaar.getInstance().economyHandler.event(player, "sell", bazaarItem, amount);
            }

            PlayerBazaar playerBazaar = DeluxeBazaar.getInstance().players.getOrDefault(player.getUniqueId(), new PlayerBazaar(player.getUniqueId()));
            if (type.equalsIgnoreCase("buyOrder")) {
                int maximumOrderAmount = DeluxeBazaar.getInstance().configFile.getInt("settings.maximum_buy_order_amount");
                if (maximumOrderAmount > 0 && playerBazaar.getBuyOrders().size() >= maximumOrderAmount) {
                    Utils.sendMessage(player, "reached_maximum_buy_order");
                    player.closeInventory();
                    return;
                }

                if (BazaarItemHook.getDefaultSellPrice(name) <= 0.0 || unitPrice <= 0.0) {
                    Utils.sendMessage(player, "selling_disabled_for_item", new PlaceholderUtil()
                            .addPlaceholder("%item_name%", Utils.strip(itemName))
                            .addPlaceholder("%item_name_colored%", Utils.colorize(itemName)));
                    return;
                }

                if (bazaarItem.getMaximumSellPrice() > 0.0 && unitPrice > bazaarItem.getMaximumSellPrice()) {
                    Utils.sendMessage(player, "exceeds_maximum_sell_price", new PlaceholderUtil()
                            .addPlaceholder("%maximum_sell_price%", DeluxeBazaar.getInstance().numberFormat.format(bazaarItem.getMaximumSellPrice())));
                    return;
                }

                double price = BazaarItemHook.getDefaultBuyPrice(name);
                if (price > 0.0 && unitPrice > price) {
                    Utils.sendMessage(player, "wrong_price");
                    return;
                }

                if (DeluxeBazaar.getInstance().economyManager.getBalance(player) < unitPrice * amount) {
                    Utils.sendMessage(player, "not_enough_money", new PlaceholderUtil()
                            .addPlaceholder("%required_money%", DeluxeBazaar.getInstance().numberFormat.format(unitPrice * amount - DeluxeBazaar.getInstance().economyManager.getBalance(player))));
                    return;
                }

                ConfigurationSection buyOrderSection = DeluxeBazaar.getInstance().configFile.getConfigurationSection("buy_order");
                if (buyOrderSection.getBoolean("limit_price_change")) {
                    double currentBuyPrice = BazaarItemHook.getSellPrice(player, name, 1);
                    double maximumChange = buyOrderSection.getDouble("maximum_price_change");

                    if ((unitPrice - currentBuyPrice) > maximumChange) {
                        Utils.sendMessage(player, "overbidding");
                        return;
                    } else if ((currentBuyPrice - unitPrice) > maximumChange) {
                        Utils.sendMessage(player, "underbidding");
                        return;
                    }
                }

                PlayerCreatedOrderEvent orderEvent = new PlayerCreatedOrderEvent(player, bazaarItem, OrderType.BUY, price/amount, amount);
                Bukkit.getPluginManager().callEvent(orderEvent);
                if (orderEvent.isCancelled())
                    return;

                boolean result = DeluxeBazaar.getInstance().economyManager.removeBalance(player, unitPrice * amount);
                if (!result)
                    return;

                player.closeInventory();
                PlayerOrder playerOrder = new PlayerOrder(player, UUID.randomUUID(), bazaarItem, OrderType.BUY, unitPrice, amount);
                DeluxeBazaar.getInstance().orderHandler.createOrder(player, playerOrder);

                Utils.sendMessage(player, "setup_buy_order", placeholderUtil);

                DeluxeBazaar.getInstance().dataHandler.writeToLog("[PLAYER CREATED BUY ORDER] " + player.getName() + " (" + player.getUniqueId() + ") setup " + (amount) + "x " + name + " buy order for " + (unitPrice * amount) + " coins.");
                if (DeluxeBazaar.getInstance().discordWebhook != null)
                    DeluxeBazaar.getInstance().discordWebhook.sendMessage("setup_buy_order", placeholderUtil
                            .addPlaceholder("%player_name%", player.getName())
                            .addPlaceholder("%player_displayname%", player.getDisplayName()));
            }

            if (type.equalsIgnoreCase("sellOffer")) {
                int maximumOrderAmount = DeluxeBazaar.getInstance().configFile.getInt("settings.maximum_sell_offer_amount");
                if (maximumOrderAmount > 0 && playerBazaar.getSellOffers().size() >= maximumOrderAmount) {
                    Utils.sendMessage(player, "reached_maximum_sell_offer");
                    player.closeInventory();
                    return;
                }

                if (BazaarItemHook.getDefaultBuyPrice(name) <= 0.0 || unitPrice <= 0.0) {
                    Utils.sendMessage(player, "buying_disabled_for_item", new PlaceholderUtil()
                            .addPlaceholder("%item_name%", Utils.strip(itemName))
                            .addPlaceholder("%item_name_colored%", Utils.colorize(itemName)));
                    return;
                }

                if (bazaarItem.getMaximumBuyPrice() > 0.0 && unitPrice > bazaarItem.getMaximumBuyPrice()) {
                    Utils.sendMessage(player, "exceeds_maximum_buy_price", new PlaceholderUtil()
                            .addPlaceholder("%maximum_buy_price%", DeluxeBazaar.getInstance().numberFormat.format(bazaarItem.getMaximumBuyPrice())));
                    return;
                }

                double price = BazaarItemHook.getDefaultSellPrice(name);
                if (price > 0.0 && unitPrice < price) {
                    Utils.sendMessage(player, "wrong_price");
                    return;
                }

                ConfigurationSection buyOrderSection = DeluxeBazaar.getInstance().configFile.getConfigurationSection("sell_offer");
                if (buyOrderSection.getBoolean("limit_price_change")) {
                    double currentBuyPrice = BazaarItemHook.getBuyPrice(player, name, 1);
                    double maximumChange = buyOrderSection.getDouble("maximum_price_change");

                    if ((unitPrice - currentBuyPrice) > maximumChange) {
                        Utils.sendMessage(player, "underbidding");
                        return;
                    } else if ((currentBuyPrice - unitPrice) > maximumChange) {
                        Utils.sendMessage(player, "overbidding");
                        return;
                    }
                }

                List<ItemStack> sellableItems = DeluxeBazaar.getInstance().itemHandler.getSellableBazaarItems(player, name);
                int itemCount = sellableItems != null && !sellableItems.isEmpty() ? sellableItems.stream().mapToInt(ItemStack::getAmount).sum() : 0;

                if (itemCount <= 0 || amount > itemCount) {
                    Utils.sendMessage(player, "not_enough_item", new PlaceholderUtil()
                            .addPlaceholder("%item_name%", Utils.strip(itemName))
                            .addPlaceholder("%required_amount%", String.valueOf(amount - itemCount))
                            .addPlaceholder("%item_name_colored%", Utils.colorize(itemName))
                            .addPlaceholder("%count%", String.valueOf(amount - itemCount)));
                    return;
                }

                PlayerCreatedOrderEvent orderEvent = new PlayerCreatedOrderEvent(player, bazaarItem, OrderType.SELL, price/amount, amount);
                Bukkit.getPluginManager().callEvent(orderEvent);
                if (orderEvent.isCancelled())
                    return;

                player.closeInventory();

                // Remove items FIRST to prevent dupe on crash
                int count = 0;
                boolean enabled = false;
                for (ItemStack is : sellableItems) {
                    if (enabled)
                        break;
                    int itemAmount = is.getAmount();

                    if (count + itemAmount <= amount) {
                        player.getInventory().removeItem(is);
                        count += itemAmount;

                        if (count == amount)
                            enabled = true;

                        player.updateInventory();
                    } else if (count + itemAmount > amount) {
                        int newAmount = itemAmount - (amount - count);

                        is.setAmount(newAmount);
                        player.updateInventory();

                        enabled = true;
                    }
                }

                // Create order AFTER items are removed
                if (count >= amount) {
                    PlayerOrder playerOrder = new PlayerOrder(player, UUID.randomUUID(), bazaarItem, OrderType.SELL, unitPrice, amount);
                    DeluxeBazaar.getInstance().orderHandler.createOrder(player, playerOrder);
                } else {
                    // Rollback: give back removed items
                    if (count > 0)
                        DeluxeBazaar.getInstance().itemHandler.giveBazaarItems(player, bazaarItem.getItemStack().clone(), count);
                    return;
                }

                Utils.sendMessage(player, "setup_sell_offer", placeholderUtil);

                DeluxeBazaar.getInstance().dataHandler.writeToLog("[PLAYER CREATED SELL OFFER] " + player.getName() + " (" + player.getUniqueId() + ") setup " + (amount) + "x " + name + " sell offer for " + (unitPrice * amount) + " coins.");
                if (DeluxeBazaar.getInstance().discordWebhook != null)
                    DeluxeBazaar.getInstance().discordWebhook.sendMessage("setup_sell_offer", placeholderUtil
                            .addPlaceholder("%player_name%", player.getName())
                            .addPlaceholder("%player_displayname%", player.getDisplayName()));
            }
        }));


        int goBackSlot = menuSection.getInt("back");
        ItemStack goBackItem = DeluxeBazaar.getInstance().normalItems.get("goBack");
        if (goBackSlot > 0 && goBackItem != null)
            switch (type) {
                case "buy":
                    gui.setItem(goBackSlot - 1, ClickableItem.of(goBackItem, (event) -> new BuyMenu(player).openMenu(name, "default")));
                    break;
                case "sell":
                    gui.setItem(goBackSlot - 1, ClickableItem.of(goBackItem, (event) -> new SellMenu(player).openMenu(name, "default")));
                    break;
                case "buyOrder":
                    gui.setItem(goBackSlot - 1, ClickableItem.of(goBackItem, (event) -> new SetBuyOrderMenu(player).openMenu(amount, name, "default")));
                    break;
                case "sellOffer":
                    gui.setItem(goBackSlot - 1, ClickableItem.of(goBackItem, (event) -> new SetSellOfferMenu(player).openMenu(amount, name, "default")));
                    break;
            }

        gui.open(player);
    }
}
