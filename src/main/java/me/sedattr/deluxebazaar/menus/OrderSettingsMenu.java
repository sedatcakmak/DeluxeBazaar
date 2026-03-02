package me.sedattr.deluxebazaar.menus;

import me.sedattr.deluxebazaar.DeluxeBazaar;
import me.sedattr.bazaarapi.BazaarItemHook;
import me.sedattr.bazaarapi.events.PlayerDeletedOrderEvent;
import me.sedattr.deluxebazaar.inventoryapi.HInventory;
import me.sedattr.deluxebazaar.inventoryapi.item.ClickableItem;
import me.sedattr.deluxebazaar.managers.BazaarItem;
import me.sedattr.deluxebazaar.managers.OrderType;
import me.sedattr.deluxebazaar.managers.PlayerOrder;
import me.sedattr.deluxebazaar.others.PlaceholderUtil;
import me.sedattr.deluxebazaar.others.Utils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class OrderSettingsMenu implements MenuManager {
    private final ConfigurationSection section;
    private final Player player;
    private PlayerOrder order;

    public OrderSettingsMenu(Player player) {
        this.section = DeluxeBazaar.getInstance().menusFile.getConfigurationSection("menus.orderSettings");
        this.player = player;
    }

    public void openMenu(PlayerOrder order) {
        if (section == null)
            return;
        this.order = order;

        String name = order.getItem().getName();

        String itemName = DeluxeBazaar.getInstance().itemsFile.getString("items." + name + ".name");
        int total = order.getAmount() - order.getFilled();

        PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                .addPlaceholder("%item_name_colored%", Utils.colorize(itemName))
                .addPlaceholder("%item_name%", Utils.strip(itemName))
                .addPlaceholder("%total_price%", DeluxeBazaar.getInstance().numberFormat.format(total * order.getPrice()))
                .addPlaceholder("%price%", DeluxeBazaar.getInstance().numberFormat.format(total * order.getPrice()))
                .addPlaceholder("%total_amount%", String.valueOf(total))
                .addPlaceholder("%amount%", String.valueOf(total));
        HInventory gui = DeluxeBazaar.getInstance().menuHandler.createInventory(this.player, this.section, "order_settings", placeholderUtil);

        BazaarItem bazaarItem = DeluxeBazaar.getInstance().bazaarItems.get(name);
        if (bazaarItem == null)
            return;
        ItemStack exampleItem = bazaarItem.getItemStack().clone();

        ItemStack cancel = Utils.createItemFromSection(section.getConfigurationSection("cancel"), placeholderUtil);
        if (cancel != null) {
            gui.setItem(section.getInt("cancel.slot") - 1, ClickableItem.of(cancel, (event -> {
                PlayerDeletedOrderEvent orderEvent = new PlayerDeletedOrderEvent(player, order);
                Bukkit.getPluginManager().callEvent(orderEvent);
                if (orderEvent.isCancelled())
                    return;

                if (order.getType().equals(OrderType.BUY)) {
                    if (!DeluxeBazaar.getInstance().economyManager.addBalance(player, total * order.getPrice())) {
                        Utils.sendMessage(player, "add_error");
                        return;
                    }
                    DeluxeBazaar.getInstance().orderHandler.deleteOrder(player, order);
                    DeluxeBazaar.getInstance().dataHandler.writeToLog("[PLAYER CANCELLED BUY ORDER] " + player.getName() + " (" + player.getUniqueId() + ") cancelled " + (order.getAmount()) + "x " + name + " buy order. (" + (total * order.getPrice()) + " coins)");
                    Utils.sendMessage(player, "cancelled_buy_order", placeholderUtil);
                } else {
                    DeluxeBazaar.getInstance().orderHandler.deleteOrder(player, order);
                    DeluxeBazaar.getInstance().dataHandler.writeToLog("[PLAYER CANCELLED SELL OFFER] " + player.getName() + " (" + player.getUniqueId() + ") cancelled " + (order.getAmount()) + "x " + name + " sell offer. (" + (total * order.getPrice()) + " coins)");
                    DeluxeBazaar.getInstance().itemHandler.giveBazaarItems(player, exampleItem, total);
                    Utils.sendMessage(player, "cancelled_sell_offer", placeholderUtil);
                }

                //Variables.itemHandler.reloadPrices(orderItemArgs[1], orderItemArgs[0], orderItemArgs);
                player.closeInventory();
            })));
        }

        Double unitPrice = order.getType().equals(OrderType.BUY) ?
                BazaarItemHook.getSellPrice(player, name, 1) :
                BazaarItemHook.getBuyPrice(player, name, 1);

        placeholderUtil
                .addPlaceholder("%unit_price%", DeluxeBazaar.getInstance().numberFormat.format(unitPrice))
                .addPlaceholder("%unitprice%", DeluxeBazaar.getInstance().numberFormat.format(unitPrice))
                .addPlaceholder("%order_price%", DeluxeBazaar.getInstance().numberFormat.format(order.getPrice()))
                .addPlaceholder("%orderprice%", DeluxeBazaar.getInstance().numberFormat.format(order.getPrice()))
                .addPlaceholder("%total_amount%", String.valueOf(total))
                .addPlaceholder("%amount%", String.valueOf(total));

        ItemStack change = Utils.createItemFromSection(section.getConfigurationSection("change"), placeholderUtil);
        if (change != null) {
            Utils.changeLore(change, section.getStringList("change.lore." + order.getType().name().toLowerCase()), placeholderUtil);
            gui.setItem(section.getInt("change.slot") - 1, ClickableItem.of(change, (event ->
                    DeluxeBazaar.getInstance().inputMenu.open(player, this))));
        }

        int goBackSlot = section.getInt("back");
        ItemStack goBackItem = DeluxeBazaar.getInstance().normalItems.get("goBack");
        if (goBackSlot > 0 && goBackItem != null)
            gui.setItem(goBackSlot - 1, ClickableItem.of(goBackItem, (event) -> new OrdersMenu(player).openMenu(1)));

        gui.open(player);
    }

    @Override
    public void inputResult(String input) {
        double number;
        try {
            number = Double.parseDouble(input);
        } catch (NumberFormatException e) {
            try {
                number = DeluxeBazaar.getInstance().numberFormat.reverseFormat(input);
            } catch (NumberFormatException a) {
                number = 0;
            }
        }

        if (number <= 0) {
            openMenu(this.order);
            return;
        }

        if (this.order.getType().equals(OrderType.BUY)) {
            if (BazaarItemHook.getDefaultSellPrice(this.order.getItem().getName()) <= 0.0) {
                String itemName = DeluxeBazaar.getInstance().itemsFile.getString("items." + this.order.getItem().getName() + ".name");
                Utils.sendMessage(player, "selling_disabled_for_item", new PlaceholderUtil()
                        .addPlaceholder("%item_name%", Utils.strip(itemName))
                        .addPlaceholder("%item_name_colored%", Utils.colorize(itemName)));
                return;
            }

            if (this.order.getItem().getMaximumSellPrice() > 0.0 && number > this.order.getItem().getMaximumSellPrice()) {
                Utils.sendMessage(player, "exceeds_maximum_sell_price", new PlaceholderUtil()
                        .addPlaceholder("%maximum_sell_price%", DeluxeBazaar.getInstance().numberFormat.format(this.order.getItem().getMaximumSellPrice())));
                return;
            }

            double price = BazaarItemHook.getDefaultBuyPrice(this.order.getItem().getName());
            if (price > 0.0 && number > price) {
                Utils.sendMessage(player, "wrong_price");
                return;
            }

            int left = this.order.getAmount() - this.order.getFilled();
            double requiredMoney = (left * number) - (left * this.order.getPrice());
            if (DeluxeBazaar.getInstance().economyManager.getBalance(player) < requiredMoney) {
                Utils.sendMessage(player, "not_enough_money", new PlaceholderUtil()
                        .addPlaceholder("%required_money%", DeluxeBazaar.getInstance().numberFormat.format(requiredMoney - DeluxeBazaar.getInstance().economyManager.getBalance(player))));
                return;
            }

            ConfigurationSection buyOrderSection = DeluxeBazaar.getInstance().configFile.getConfigurationSection("buy_order");
            if (buyOrderSection != null && buyOrderSection.getBoolean("limit_price_change")) {
                double currentBuyPrice = BazaarItemHook.getSellPrice(player, this.order.getItem().getName(), 1);
                double maximumChange = buyOrderSection.getDouble("maximum_price_change");

                if ((number - currentBuyPrice) > maximumChange) {
                    Utils.sendMessage(player, "overbidding");
                    return;
                } else if ((currentBuyPrice - number) > maximumChange) {
                    Utils.sendMessage(player, "underbidding");
                    return;
                }
            }

            if (requiredMoney > 0) {
                if (!DeluxeBazaar.getInstance().economyManager.removeBalance(this.player, requiredMoney))
                    return;
            } else if (requiredMoney < 0) {
                if (!DeluxeBazaar.getInstance().economyManager.addBalance(this.player, Math.abs(requiredMoney))) {
                    Utils.sendMessage(player, "add_error");
                    return;
                }
            }
        } else {
            if (BazaarItemHook.getDefaultBuyPrice(this.order.getItem().getName()) <= 0.0) {
                String itemName = DeluxeBazaar.getInstance().itemsFile.getString("items." + this.order.getItem().getName() + ".name");
                Utils.sendMessage(player, "buying_disabled_for_item", new PlaceholderUtil()
                        .addPlaceholder("%item_name%", Utils.strip(itemName))
                        .addPlaceholder("%item_name_colored%", Utils.colorize(itemName)));
                return;
            }

            if (this.order.getItem().getMaximumBuyPrice() > 0.0 && number > this.order.getItem().getMaximumBuyPrice()) {
                Utils.sendMessage(player, "exceeds_maximum_buy_price", new PlaceholderUtil()
                        .addPlaceholder("%maximum_buy_price%", DeluxeBazaar.getInstance().numberFormat.format(this.order.getItem().getMaximumBuyPrice())));
                return;
            }

            double price = BazaarItemHook.getDefaultSellPrice(this.order.getItem().getName());
            if (price > 0.0 && number < price) {
                Utils.sendMessage(player, "wrong_price");
                return;
            }

            ConfigurationSection buyOrderSection = DeluxeBazaar.getInstance().configFile.getConfigurationSection("sell_offer");
            if (buyOrderSection != null && buyOrderSection.getBoolean("limit_price_change")) {
                double currentBuyPrice = BazaarItemHook.getBuyPrice(player, this.order.getItem().getName(), 1);
                double maximumChange = buyOrderSection.getDouble("maximum_price_change");

                if ((number - currentBuyPrice) > maximumChange) {
                    Utils.sendMessage(player, "underbidding");
                    return;
                } else if ((currentBuyPrice - number) > maximumChange) {
                    Utils.sendMessage(player, "overbidding");
                    return;
                }
            }
        }

        String itemName = DeluxeBazaar.getInstance().itemsFile.getString("items." + order.getItem().getName() + ".name");
        Utils.sendMessage(player, "changed_price", new PlaceholderUtil()
                .addPlaceholder("%total_amount%", String.valueOf(order.getAmount()))
                .addPlaceholder("%item_name%", Utils.strip(itemName))
                .addPlaceholder("%item_name_colored%", Utils.colorize(itemName))
                .addPlaceholder("%new_price%", DeluxeBazaar.getInstance().numberFormat.format(number))
                .addPlaceholder("%old_price%", DeluxeBazaar.getInstance().numberFormat.format(order.getPrice()))
        );

        double oldPrice = order.getPrice();
        DeluxeBazaar.getInstance().orderHandler.updateOrderPrice(player, order, oldPrice, number);
        order.setPrice(number);
        new OrdersMenu(player).openMenu(1);
    }
}