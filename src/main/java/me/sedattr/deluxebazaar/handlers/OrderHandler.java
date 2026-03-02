package me.sedattr.deluxebazaar.handlers;

import me.sedattr.deluxebazaar.DeluxeBazaar;
import me.sedattr.bazaarapi.BazaarItemHook;
import me.sedattr.deluxebazaar.managers.*;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public class OrderHandler {
    public void deleteOrder(OfflinePlayer player, PlayerOrder order) {
        BazaarItem bazaarItem = order.getItem();
        OrderType type = order.getType();

        List<OrderPrice> prices = type.equals(OrderType.BUY) ? bazaarItem.getBuyPrices() : bazaarItem.getSellPrices();

        // finding order price with unit price
        OrderPrice orderPrice = getOrderPrice(prices, order.getPrice());
        if (orderPrice != null) {
            int leftAmount = order.getAmount() - order.getFilled();

            int newAmount = orderPrice.getItemAmount()-leftAmount;
            if (newAmount > 0)
                orderPrice.setItemAmount(newAmount);
            else
                prices.remove(orderPrice);

            orderPrice.setOrderAmount(orderPrice.getOrderAmount()-1);
        }

        // removing order from player's orders
        PlayerBazaar playerBazaar = DeluxeBazaar.getInstance().players.getOrDefault(player.getUniqueId(), new PlayerBazaar(player));
        if (type.equals(OrderType.BUY))
            playerBazaar.getBuyOrders().remove(order);
        else
            playerBazaar.getSellOffers().remove(order);
    }

    public void updateOrderPrice(OfflinePlayer player, PlayerOrder order, double oldPrice, double newPrice) {
        BazaarItem bazaarItem = order.getItem();
        List<OrderPrice> prices = order.getType().equals(OrderType.BUY) ? bazaarItem.getBuyPrices() : bazaarItem.getSellPrices();
        int leftAmount = order.getAmount() - order.getFilled();

        // Remove from old OrderPrice entry
        OrderPrice oldOrderPrice = getOrderPrice(prices, oldPrice);
        if (oldOrderPrice != null) {
            int newAmount = oldOrderPrice.getItemAmount() - leftAmount;
            if (newAmount > 0)
                oldOrderPrice.setItemAmount(newAmount);
            else
                prices.remove(oldOrderPrice);

            oldOrderPrice.setOrderAmount(oldOrderPrice.getOrderAmount() - 1);

            List<PlayerOrder> playerOrders = oldOrderPrice.getPlayers().getOrDefault(player.getUniqueId(), new ArrayList<>());
            playerOrders.remove(order);
            if (playerOrders.isEmpty())
                oldOrderPrice.getPlayers().remove(player.getUniqueId());
            else
                oldOrderPrice.getPlayers().put(player.getUniqueId(), playerOrders);
        }

        // Add to new OrderPrice entry
        OrderPrice newOrderPrice = getOrderPrice(prices, newPrice);
        if (newOrderPrice == null) {
            newOrderPrice = new OrderPrice(order.getType(), newPrice, leftAmount);
            prices.add(newOrderPrice);
        } else {
            newOrderPrice.setItemAmount(newOrderPrice.getItemAmount() + leftAmount);
            newOrderPrice.setOrderAmount(newOrderPrice.getOrderAmount() + 1);
        }

        List<PlayerOrder> playerOrders = newOrderPrice.getPlayers().getOrDefault(player.getUniqueId(), new ArrayList<>());
        playerOrders.add(order);
        newOrderPrice.getPlayers().put(player.getUniqueId(), playerOrders);
    }

    public void createOrder(OfflinePlayer player, PlayerOrder playerOrder) {
        List<OrderPrice> prices = playerOrder.getType().equals(OrderType.BUY) ? playerOrder.getItem().getBuyPrices() : playerOrder.getItem().getSellPrices();
        OrderPrice orderPrice = getOrderPrice(prices, playerOrder.getPrice());

        if (orderPrice == null) {
            orderPrice = new OrderPrice(playerOrder.getType(), playerOrder.getPrice(), playerOrder.getAmount());
            prices.add(orderPrice);
        } else {
            orderPrice.setItemAmount(orderPrice.getItemAmount()+playerOrder.getAmount());
            orderPrice.setOrderAmount(orderPrice.getOrderAmount()+1);

            prices.set(prices.indexOf(orderPrice), orderPrice);
        }

        List<PlayerOrder> playerOrders = orderPrice.getPlayers().getOrDefault(player.getUniqueId(), new ArrayList<>());
        playerOrders.add(playerOrder);
        orderPrice.getPlayers().put(player.getUniqueId(), playerOrders);

        // adding order to player's orders
        PlayerBazaar playerBazaar = DeluxeBazaar.getInstance().players.getOrDefault(player.getUniqueId(), new PlayerBazaar(player));
        if (playerOrder.getType().equals(OrderType.BUY))
            playerBazaar.getBuyOrders().add(playerOrder);
        else
            playerBazaar.getSellOffers().add(playerOrder);
    }

    /*
    public PlayerOrder createOrder(OfflinePlayer player, BazaarItem bazaarItem, OrderType type, double unitPrice, int amount) {
        // creating new order
        PlayerOrder playerOrder = new PlayerOrder(player, bazaarItem, type, unitPrice, amount);

        List<OrderPrice> prices = type == OrderType.BUY ? bazaarItem.getBuyPrices() : bazaarItem.getSellPrices();
        OrderPrice orderPrice = getOrderPrice(prices, unitPrice);

        if (orderPrice == null) {
            orderPrice = new OrderPrice(type, unitPrice, amount);
            prices.add(orderPrice);
        } else {
            orderPrice.setItemAmount(orderPrice.getItemAmount()+amount);
            orderPrice.setOrderAmount(orderPrice.getOrderAmount()+1);

            prices.set(prices.indexOf(orderPrice), orderPrice);
        }

        List<PlayerOrder> playerOrders = orderPrice.getPlayers().getOrDefault(player.getUniqueId(), new ArrayList<>());
        playerOrders.add(playerOrder);
        orderPrice.getPlayers().put(player.getUniqueId(), playerOrders);

        // adding order to player's orders
        PlayerBazaar playerBazaar = DeluxeBazaar.getInstance().players.getOrDefault(player.getUniqueId(), new PlayerBazaar(player));
        if (type == OrderType.BUY)
            playerBazaar.getBuyOrders().add(playerOrder);
        else
            playerBazaar.getSellOffers().add(playerOrder);

        return playerOrder;
    }

     */

    public OrderPrice getOrderPrice(List<OrderPrice> orderPrices, Double price) {
        if (orderPrices.isEmpty())
            return null;

        for (OrderPrice orderPrice : orderPrices) {
            if (orderPrice == null)
                continue;
            if (price > 0.0 && orderPrice.getPrice() != price)
                continue;

            return orderPrice;
        }

        return null;
    }

    public boolean isStockEnabled(String item, String type) {
        ConfigurationSection section = getStockConfiguration(item);
        if (section == null)
            return false;

        if (type.equalsIgnoreCase("sell"))
            return section.getBoolean("only_orders", false);

        return true;
    }

    public int getItemCount(OfflinePlayer player, String type, String item, Double price) {
        if (player == null)
            return 0;

        PlayerBazaar playerBazaar = DeluxeBazaar.getInstance().players.getOrDefault(player.getUniqueId(), new PlayerBazaar(player.getUniqueId()));
        List<PlayerOrder> orders = type.contains("sell") ? playerBazaar.getBuyOrders() : playerBazaar.getSellOffers();
        if (orders.isEmpty())
            return 0;

        BazaarItem bazaarItem = DeluxeBazaar.getInstance().bazaarItems.get(item);
        List<OrderPrice> itemOrders = type.contains("sell") ? bazaarItem.getBuyPrices() : bazaarItem.getSellPrices();
        if (itemOrders.isEmpty())
            return 0;

        int itemCount = 0;
        for (PlayerOrder playerOrder : orders) {
            if (playerOrder == null)
                continue;
            if (!playerOrder.getItem().getName().equalsIgnoreCase(item))
                continue;
            if (price > 0.0 && playerOrder.getPrice() != price)
                continue;

            itemCount += playerOrder.getAmount() - playerOrder.getFilled();
        }

        return itemCount;
    }

    public ConfigurationSection getStockConfiguration(String item) {
        ConfigurationSection stockSection = DeluxeBazaar.getInstance().configFile.getConfigurationSection("item_stock_system");
        if (stockSection == null)
            return null;

        if (!stockSection.getBoolean("enabled", false))
            return null;

        ConfigurationSection section = stockSection.getConfigurationSection("item_list." + item);
        if (section != null)
            return section;

        BazaarItem bazaarItem = BazaarItemHook.getBazaarItem(item);
        if (bazaarItem == null)
            return null;

        String categoryName = bazaarItem.getCategory();
        if (categoryName == null || categoryName.isEmpty())
            return null;

        section = stockSection.getConfigurationSection("item_list." + categoryName);
        return section;
    }

    private final String time = "%%__TIMESTAMP__%%";
    public int getStockCountWithOrders(OfflinePlayer player, String item, String type) {
        BazaarItem bazaarItem = DeluxeBazaar.getInstance().bazaarItems.get(item);
        if (bazaarItem == null)
            return 0;

        List<OrderPrice> itemOrders = type.equalsIgnoreCase("buy") ? bazaarItem.getSellPrices() : bazaarItem.getBuyPrices();
        if (itemOrders == null || itemOrders.size() < 1)
            return 0;

        int itemCount = 0;
        if (player != null) {
            PlayerBazaar playerBazaar = DeluxeBazaar.getInstance().players.get(player.getUniqueId());
            List<PlayerOrder> orders = type.equalsIgnoreCase("buy") ? playerBazaar.getSellOffers() : playerBazaar.getBuyOrders();

            if (!orders.isEmpty())
                for (PlayerOrder playerOrder : orders) {
                    if (playerOrder == null)
                        continue;
                    if (!playerOrder.getItem().getName().equalsIgnoreCase(item))
                        continue;

                    itemCount += (playerOrder.getAmount() - playerOrder.getFilled());
                }
        }

        int totalCount = 0;
        for (OrderPrice order : itemOrders) {
            if (order == null)
                continue;
            if (order.getItemAmount() < 1 || order.getOrderAmount() < 1)
                continue;

            totalCount += order.getItemAmount();
        }

        return Math.max(totalCount - itemCount, 0);
    }

    public int getStockCount(OfflinePlayer player, String item, String type) {
        BazaarItem bazaarItem = DeluxeBazaar.getInstance().bazaarItems.get(item);
        if (bazaarItem == null)
            return 0;

        ConfigurationSection stockSection = getStockConfiguration(item);
        if (stockSection == null)
            return 0;

        if (stockSection.getBoolean("only_orders", false))
            return getStockCountWithOrders(player, item, type);

        int maximumStock = stockSection.getInt("max_stock", 0);
        int stock = Math.max(BazaarItemHook.getSellCount(item) - BazaarItemHook.getBuyCount(item), 0);
        if (maximumStock > 0 && stock > maximumStock)
            stock = maximumStock;

        return stock;
    }
}