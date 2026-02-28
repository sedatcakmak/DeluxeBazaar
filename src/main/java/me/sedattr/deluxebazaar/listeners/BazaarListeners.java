package me.sedattr.deluxebazaar.listeners;

import me.sedattr.deluxebazaar.DeluxeBazaar;
import me.sedattr.bazaarapi.events.BazaarItemBuyEvent;
import me.sedattr.bazaarapi.events.BazaarItemSellEvent;
import me.sedattr.deluxebazaar.managers.*;
import me.sedattr.deluxebazaar.others.PlaceholderUtil;
import me.sedattr.deluxebazaar.others.Utils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.*;

public class BazaarListeners implements Listener {
    @EventHandler
    public synchronized void onItemBuy(BazaarItemBuyEvent e) {
        OfflinePlayer player = e.getPlayer();
        Double price = e.getUnitPrice();
        BazaarItem item = e.getItem();
        int count = e.getCount();

        List<OrderPrice> toRemove = new ArrayList<>();
        List<OrderPrice> orderPrices = new ArrayList<>(item.getSellPrices());
        if (!orderPrices.isEmpty())
            for (OrderPrice orderPrice : orderPrices) {
                if (count <= 0)
                    break;
                if (orderPrice.getPrice() > price)
                    continue;

                int itemCount = DeluxeBazaar.getInstance().orderHandler.getItemCount(player, "buy", item.getName(), orderPrice.getPrice());
                int newCount = orderPrice.getItemAmount() - itemCount;
                if (newCount <= 0)
                    continue;

                if (newCount > count) {
                    orderPrice.setItemAmount(orderPrice.getItemAmount() - count);
                    changePlayerOrders(player.getUniqueId(), count, orderPrice);
                    break;
                }
                else {
                    count-=newCount;
                    changePlayerOrders(player.getUniqueId(), newCount, orderPrice);

                    toRemove.add(orderPrice);
                }
            }

        // Remove after iteration to avoid concurrent modification
        item.getSellPrices().removeAll(toRemove);
    }

    @EventHandler
    public synchronized void onItemSell(BazaarItemSellEvent e) {
        OfflinePlayer player = e.getPlayer();
        Double price = e.getUnitPrice();
        BazaarItem item = e.getItem();
        int count = e.getCount();

        List<OrderPrice> toRemove = new ArrayList<>();
        List<OrderPrice> orderPrices = new ArrayList<>(item.getBuyPrices());
        if (!orderPrices.isEmpty())
            for (OrderPrice orderPrice : orderPrices) {
                if (count <= 0)
                    break;
                if (orderPrice.getPrice() < price)
                    continue;

                int itemCount = DeluxeBazaar.getInstance().orderHandler.getItemCount(player, "sell", item.getName(), orderPrice.getPrice());
                int newCount = orderPrice.getItemAmount() - itemCount;
                if (newCount <= 0)
                    continue;

                if (newCount > count) {
                    orderPrice.setItemAmount(orderPrice.getItemAmount() - count);

                    changePlayerOrders(player.getUniqueId(), count, orderPrice);
                    break;
                }
                else {
                    count-=newCount;
                    changePlayerOrders(player.getUniqueId(), newCount, orderPrice);

                    toRemove.add(orderPrice);
                }
            }

        // Remove after iteration to avoid concurrent modification
        item.getBuyPrices().removeAll(toRemove);
    }

    public void changePlayerOrders(UUID uuid, Integer count, OrderPrice orderPrice) {
        for (Map.Entry<UUID, List<PlayerOrder>> entry : orderPrice.getPlayers().entrySet()) {
            if (count <= 0)
                return;
            if (uuid.equals(entry.getKey()))
                continue;

            PlayerBazaar playerBazaar = DeluxeBazaar.getInstance().players.get(entry.getKey());
            if (playerBazaar == null)
                continue;

            List<PlayerOrder> orders = entry.getValue();
            if (orders.isEmpty())
                return;

            for (PlayerOrder order : orders) {
                int totalCount = order.getAmount();
                if (order.getFilled() >= totalCount)
                    continue;

                int fillCount = order.getFilled() + count;
                if (totalCount > fillCount) {
                    order.setFilled(fillCount);
                    return;
                }
                else {
                    count-=(totalCount-order.getFilled());
                    order.setFilled(totalCount);

                    int newOrderAmount = orderPrice.getOrderAmount()-1;
                    if (newOrderAmount <= 0) {
                        if (orderPrice.getType().equals(OrderType.BUY))
                            order.getItem().getBuyPrices().remove(orderPrice);
                        else
                            order.getItem().getSellPrices().remove(orderPrice);
                    } else
                        orderPrice.setOrderAmount(orderPrice.getOrderAmount()-1);

                    String itemName = DeluxeBazaar.getInstance().itemsFile.getString("items." + order.getItem().getName() + ".name");
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null && player.isOnline())
                        Utils.sendMessage(player, order.getType().equals(OrderType.BUY) ? "filled_buy_order" : "filled_sell_offerr", new PlaceholderUtil()
                                .addPlaceholder("%total_amount%", String.valueOf(totalCount))
                                .addPlaceholder("%item_name%", Utils.strip(itemName))
                                .addPlaceholder("%item_name_colored%", Utils.colorize(itemName)));
                }
            }
        }
    }


}