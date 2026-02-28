package me.sedattr.deluxebazaar.menus;

import me.sedattr.deluxebazaar.DeluxeBazaar;
import me.sedattr.bazaarapi.BazaarItemHook;
import me.sedattr.deluxebazaar.inventoryapi.HInventory;
import me.sedattr.deluxebazaar.inventoryapi.item.ClickableItem;
import me.sedattr.deluxebazaar.managers.BazaarItem;
import me.sedattr.deluxebazaar.managers.PlayerBazaar;
import me.sedattr.deluxebazaar.managers.PlayerOrder;
import me.sedattr.deluxebazaar.others.PlaceholderUtil;
import me.sedattr.deluxebazaar.others.Utils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MainMenu implements MenuManager {
    private final ConfigurationSection section;
    private final ItemStack sell;
    private final ItemStack manage;
    private final ItemStack next;
    private final ItemStack previous;
    private final Player player;
    private HInventory gui;
    private String category;
    private int page;
    private PlayerBazaar playerBazaar;
    private String searched = "";
    private List<String> searchedItems = new ArrayList<>();

    public MainMenu(Player player) {
        this.section = DeluxeBazaar.getInstance().menusFile.getConfigurationSection("menus.main");
        this.sell = Utils.createItemFromSection(section.getConfigurationSection("sell"), null);
        this.manage = Utils.createItemFromSection(section.getConfigurationSection("manage"), null);
        this.next = Utils.createItemFromSection(section.getConfigurationSection("next"), null);
        this.previous = Utils.createItemFromSection(section.getConfigurationSection("previous"), null);
        this.player = player;
    }

    public void openMenu(String category, int page) {
        if (section == null)
            return;

        this.category = category;
        this.page = page;
        this.playerBazaar = DeluxeBazaar.getInstance().players.getOrDefault(player.getUniqueId(), new PlayerBazaar(player));
        if (!category.equalsIgnoreCase("search"))
             playerBazaar.setCategory(category);

        ConfigurationSection categorySection = DeluxeBazaar.getInstance().categoriesFile.getConfigurationSection(category);
        if (categorySection == null)
            return;

        int maxPage = Math.max(categorySection.getInt("page"), 1);
        this.gui = DeluxeBazaar.getInstance().menuHandler.createInventory(this.player, this.section, category,
                new PlaceholderUtil()
                        .addPlaceholder("%search%", searched)
                        .addPlaceholder("%category%", Utils.strip(DeluxeBazaar.getInstance().categoriesFile.getString(category + ".name")))
                        .addPlaceholder("%category_name%", Utils.strip(DeluxeBazaar.getInstance().categoriesFile.getString(category + ".name")))
                        .addPlaceholder("%category_name_colored%", Utils.colorize(DeluxeBazaar.getInstance().categoriesFile.getString(category + ".name")))
                        .addPlaceholder("%current_page%", String.valueOf(page))
                        .addPlaceholder("%currentpage%", String.valueOf(page))
                        .addPlaceholder("%total_page%", String.valueOf(maxPage))
                        .addPlaceholder("%totalpage%", String.valueOf(maxPage)));

        loadCategories();
        loadSellItem();
        loadManageItem();
        modeItem();

        if (page < maxPage && this.next != null)
            this.gui.setItem(this.section.getInt("next.slot") - 1, ClickableItem.of(this.next, (event -> openMenu(category, page + 1))));

        if (page > 1 && this.previous != null)
            this.gui.setItem(this.section.getInt("previous.slot") - 1, ClickableItem.of(this.previous, (event -> openMenu(category, page - 1))));

        ConfigurationSection searchSection = DeluxeBazaar.getInstance().categoriesFile.getConfigurationSection("search");
        if (searchSection != null && searchSection.getBoolean("enabled")) {
            ItemStack search = Utils.createItemFromSection(searchSection, null);
            if (search != null) {
                boolean permission = Utils.checkPermission(player, "main_menu", "search");
                if (!permission)
                    Utils.changeLore(search, searchSection.getStringList("lore.no_permission"), null);
                else if (category.equalsIgnoreCase("search")) {
                    search.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 10);

                    Utils.changeLore(search, searchSection.getStringList("lore.selected"), new PlaceholderUtil()
                            .addPlaceholder("%searched%", searched)
                            .addPlaceholder("%found_items%", String.valueOf(searchedItems.size())));
                } else
                    Utils.changeLore(search, searchSection.getStringList("lore.not_selected"), null);

                int slot = searchSection.getInt("slot");
                if (slot <= this.gui.getInventory().getSize())
                    if (!permission)
                        gui.setItem(slot - 1,
                                ClickableItem.empty(search));
                    else
                        gui.setItem(slot - 1,
                                ClickableItem.of(search, (event) -> {
                                    ClickType clickType = event.getClick();
                                    if ((clickType == ClickType.NUMBER_KEY || clickType == ClickType.LEFT) || (clickType == ClickType.RIGHT && !category.equalsIgnoreCase("search")))
                                        DeluxeBazaar.getInstance().inputMenu.open(player, this);
                                    else
                                        openMenu(playerBazaar.getCategory(), 1);
                                }));
            }
        }

        gui.open(player);
    }

    String playerUUID = "%%__USER__%%";
    public void loadManageItem() {
        List<String> lore = new ArrayList<>();
        ConfigurationSection manageSection = this.section.getConfigurationSection("manage");

        List<PlayerOrder> buyOrders = playerBazaar.getBuyOrders();
        List<PlayerOrder> sellOffers = playerBazaar.getSellOffers();

        ItemStack newManageItem = this.manage.clone();
        if (buyOrders.isEmpty() && sellOffers.isEmpty()) {
            Utils.changeLore(newManageItem, manageSection.getStringList("lore.none"), null);
            this.gui.setItem(this.section.getInt("manage.slot") - 1, ClickableItem.empty(newManageItem));
        } else {
            int totalCount = 0;
            double totalPrice = 0.0;

            int collectableItems = 0;
            double collectableMoney = 0.0;

            if (!buyOrders.isEmpty())
                for (PlayerOrder order : buyOrders) {
                    totalCount += order.getAmount();
                    totalPrice += order.getPrice() * order.getAmount();

                    collectableItems += order.getFilled() - order.getCollected();
                }

            if (!sellOffers.isEmpty())
                for (PlayerOrder order : sellOffers) {
                    totalCount += order.getAmount();
                    totalPrice += order.getPrice() * order.getAmount();

                    collectableMoney += order.getPrice() * (order.getFilled() - order.getCollected());
                }

            if (collectableItems > 0 || collectableMoney > 0) {
                int finalCollectableItems = collectableItems;
                double finalCollectableMoney = collectableMoney;
                manageSection.getStringList("lore.collectable").forEach((string) -> lore.add(Utils.colorize(string
                        .replace("%order%", String.valueOf(buyOrders.size() + sellOffers.size()))
                        .replace("%price%", DeluxeBazaar.getInstance().numberFormat.format(finalCollectableMoney))
                        .replace("%count%", String.valueOf(finalCollectableItems))
                        .replace("%order_amount%", String.valueOf(buyOrders.size() + sellOffers.size()))
                        .replace("%collectable_coins%", DeluxeBazaar.getInstance().numberFormat.format(finalCollectableMoney))
                        .replace("%collectable_items%", String.valueOf(finalCollectableItems)))));
            } else if (collectableMoney < 1) {
                double finalTotalPrice = totalPrice;
                int finalTotalCount = totalCount;
                manageSection.getStringList("lore.order").forEach((string) -> lore.add(Utils.colorize(string
                        .replace("%order%", String.valueOf(buyOrders.size() + sellOffers.size()))
                        .replace("%price%", DeluxeBazaar.getInstance().numberFormat.format(finalTotalPrice))
                        .replace("%amount%", String.valueOf(finalTotalCount))
                        .replace("%order_amount%", String.valueOf(buyOrders.size() + sellOffers.size()))
                        .replace("%total_price%", DeluxeBazaar.getInstance().numberFormat.format(finalTotalPrice))
                        .replace("%item_amount%", String.valueOf(finalTotalCount)))));
            }

            Utils.changeLore(newManageItem, lore, null);
            this.gui.setItem(this.section.getInt("manage.slot") - 1, ClickableItem.of(newManageItem, (event -> {
                new OrdersMenu(player).openMenu(1);
            })));
        }
    }

    public void loadSellItem() {
        ItemStack newSellItem = this.sell.clone();
        HashMap<String, Integer> sellableItems = this.playerBazaar.getSellableItems();

        ConfigurationSection sellSection = this.section.getConfigurationSection("sell");
        if (sellableItems.isEmpty()) {
            Utils.changeLore(newSellItem, sellSection.getStringList("lore.nothing"), null);
            this.gui.setItem(this.section.getInt("sell.slot") - 1, ClickableItem.of(newSellItem, (event) -> Utils.sendMessage(player, "no_sellable_item")));
        } else {
            List<String> lore = new ArrayList<>();
            double price = 0;
            int number = 0;

            sellSection.getStringList("lore.header")
                    .forEach((string) -> lore.add(Utils.colorize(string)));

            List<String> allItems = new ArrayList<>();
            if (!searchedItems.isEmpty())
                searchedItems.forEach(i -> allItems.add(i.split("[:]", 2)[0]));

            HashMap<String, Integer> newSellableItems = new HashMap<>();
            for (Map.Entry<String, Integer> entry : sellableItems.entrySet()) {
                String key = entry.getKey();
                int itemCount = entry.getValue();

                if (category.equalsIgnoreCase("search")) {
                    if (!allItems.contains(key))
                        continue;
                }

                if (itemCount <= 0)
                    continue;

                double itemPrice = BazaarItemHook.getSellPrice(player, key, itemCount);
                price += itemPrice;

                if (number < sellSection.getInt("maximum")) {
                    number++;
                    String items = Utils.colorize(sellSection.getString("items.sellable")
                            .replace("%item_amount%", String.valueOf(itemCount))
                            .replace("%item_ranking%", String.valueOf(number))
                            .replace("%number%", String.valueOf(number))
                            .replace("%item_name_colored%", Utils.colorize(DeluxeBazaar.getInstance().itemsFile.getString("items." + key + ".name")))
                            .replace("%item_name%", Utils.strip(DeluxeBazaar.getInstance().itemsFile.getString("items." + key + ".name")))
                            .replace("%item%", Utils.strip(DeluxeBazaar.getInstance().itemsFile.getString("items." + key + ".name")))
                            .replace("%item_sell_price%", DeluxeBazaar.getInstance().numberFormat.format(itemPrice)));

                    lore.add(items);
                }

                newSellableItems.put(key, itemCount);
            }

            if (number <= 0) {
                Utils.changeLore(newSellItem, sellSection.getStringList("lore.nothing"), null);
                this.gui.setItem(this.section.getInt("sell.slot") - 1, ClickableItem.of(newSellItem, (event) -> Utils.sendMessage(player, "no_sellable_item")));

                return;
            }

            if (number < newSellableItems.size())
                lore.add(Utils.colorize(sellSection.getString("items.more")
                        .replace("%other_items%", String.valueOf(newSellableItems.size() - number))));

            double finalPrice = price;
            sellSection.getStringList("lore.footer").forEach((string) -> {
                lore.add(Utils.colorize(string
                        .replace("%price%", DeluxeBazaar.getInstance().numberFormat.format(finalPrice))
                        .replace("%total_price%", DeluxeBazaar.getInstance().numberFormat.format(finalPrice))));
            });

            Utils.changeLore(newSellItem, lore, null);
            this.gui.setItem(this.section.getInt("sell.slot") - 1, ClickableItem.of(newSellItem, (event -> new SellAllMenu(player).openMenu(newSellableItems))));
        }
    }

    public void loadSubcategories() {
        List<String> subcategories = category.equalsIgnoreCase("search") ? searchedItems : DeluxeBazaar.getInstance().categoriesFile.getStringList(category + ".items");
        if (subcategories.isEmpty())
            return;

        List<Integer> slots = DeluxeBazaar.getInstance().categoriesFile.getIntegerList(category + ".slots");

        int i = 0;
        for (String subcategory : subcategories) {
            if (i >= slots.size())
                return;
            String[] args = subcategory.split("[:]", 3);

            int itemPage = args.length > 1 ? Integer.parseInt(args[1]) : 1;
            if (itemPage != this.page)
                continue;

            ConfigurationSection section = DeluxeBazaar.getInstance().itemsFile.getConfigurationSection("groups." + args[0]);

            int slot = slots.get(i);
            List<String> lore = new ArrayList<>();
            boolean disableLore = DeluxeBazaar.getInstance().configFile.getBoolean("settings.disable_price_lore", true);
            ItemStack itemStack;
            boolean isSubcategory;

            // if its normal item instead of subcategory
            if (section == null) {
                isSubcategory = false;
                section = DeluxeBazaar.getInstance().itemsFile.getConfigurationSection("items." + args[0]);
                if (section == null)
                    continue;
                if (!Utils.checkItemPermission(player, args[0]))
                    continue;

                BazaarItem bazaarItem = DeluxeBazaar.getInstance().bazaarItems.get(args[0]);
                if (bazaarItem == null)
                    continue;

                itemStack = bazaarItem.getItemStack().clone();
                if (itemStack == null)
                    continue;

                PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                        .addPlaceholder("%item_name%", Utils.strip(DeluxeBazaar.getInstance().itemsFile.getString("items." + args[0] + ".name")))
                        .addPlaceholder("%item_name_colored%", Utils.colorize(DeluxeBazaar.getInstance().itemsFile.getString("items." + args[0] + ".name")))
                        .addPlaceholder("%item_buy_price%", DeluxeBazaar.getInstance().numberFormat.format(BazaarItemHook.getBuyPrice(player, args[0], 1)))
                        .addPlaceholder("%item_sell_price%", DeluxeBazaar.getInstance().numberFormat.format(BazaarItemHook.getSellPrice(player, args[0], 1)))
                        .addPlaceholder("%item_buy_amount%", String.valueOf(BazaarItemHook.getBuyCount(args[0])))
                        .addPlaceholder("%item_sell_amount%", String.valueOf(BazaarItemHook.getSellCount(args[0])));

                List<String> details = section.getStringList("details");
                for (String string : DeluxeBazaar.getInstance().messagesFile.getStringList("lores.item." + playerBazaar.getMode())) {
                    if (string.contains("%item_details%")) {
                        if (!details.isEmpty())
                            for (String detail : details)
                                lore.add(Utils.colorize(Utils.replacePlaceholders(detail, placeholderUtil)));

                        continue;
                    }

                    if (string.contains("%buy_lore%")) {
                        if (disableLore)
                            if (BazaarItemHook.getDefaultBuyPrice(args[0]) <= 0.0)
                                continue;

                        List<String> buyLore = DeluxeBazaar.getInstance().messagesFile.getStringList("lores.buy_lore." + playerBazaar.getMode());
                        if (!buyLore.isEmpty())
                            for (String line : buyLore)
                                lore.add(Utils.colorize(Utils.replacePlaceholders(line, placeholderUtil)));

                        continue;
                    }

                    if (string.contains("%sell_lore%")) {
                        if (disableLore)
                            if (BazaarItemHook.getDefaultSellPrice(args[0]) <= 0.0)
                                continue;

                        List<String> sellLore = DeluxeBazaar.getInstance().messagesFile.getStringList("lores.sell_lore." + playerBazaar.getMode());
                        if (!sellLore.isEmpty())
                            for (String line : sellLore)
                                lore.add(Utils.colorize(Utils.replacePlaceholders(line, placeholderUtil)));

                        continue;
                    }

                    lore.add(Utils.colorize(Utils.replacePlaceholders(string, placeholderUtil)));
                }

                // if subcateogry
            } else {
                isSubcategory=true;
                itemStack = Utils.createItemFromSection(section.getConfigurationSection("item"), null);
                if (itemStack == null) {
                    BazaarItem bazaarItem = DeluxeBazaar.getInstance().bazaarItems.get(section.getString("main_item"));
                    if (bazaarItem == null)
                        continue;

                    itemStack = bazaarItem.getItemStack().clone();
                    if (itemStack == null)
                        continue;
                }

                List<String> items = section.getStringList("item_list");

                for (String string : DeluxeBazaar.getInstance().messagesFile.getStringList("lores.subcategory." + playerBazaar.getMode())) {
                    if (string.contains("%subcategory_description%")) {
                        List<String> description = section.getStringList("item.description");
                        if (description.isEmpty())
                            continue;

                        for (String line : description)
                            lore.add(Utils.colorize(line));

                        continue;
                    }

                    if (string.contains("%item_informations%")) {
                        for (String entry : items) {
                            String item = entry.split("[:]", 2)[0];

                            ConfigurationSection subcategorySection = DeluxeBazaar.getInstance().itemsFile.getConfigurationSection("groups." + item);
                            if (subcategorySection != null) {
                                String subcategoryInformation = DeluxeBazaar.getInstance().messagesFile.getString("lores.subcategory.subcategory_information");

                                PlaceholderUtil placeholderUtil = new PlaceholderUtil();

                                String subcategoryName = subcategorySection.getString("item.name");
                                if (subcategoryName != null && !subcategoryName.equals("")) {
                                    placeholderUtil.addPlaceholder("%subcategory_name%", Utils.strip(subcategoryName))
                                    .addPlaceholder("%subcategory_name_colored%", Utils.colorize(subcategoryName));
                                }
                                else {
                                    placeholderUtil.addPlaceholder("%subcategory_name%", Utils.strip(DeluxeBazaar.getInstance().itemsFile.getString("items." + subcategorySection.getString("main_item") + ".name")))
                                            .addPlaceholder("%subcategory_name_colored%", Utils.colorize(DeluxeBazaar.getInstance().itemsFile.getString("items." + subcategorySection.getString("main_item") + ".name")));
                                }

                                lore.add(Utils.colorize(Utils.replacePlaceholders(subcategoryInformation, placeholderUtil)));
                            } else {
                                String itemInformation = DeluxeBazaar.getInstance().messagesFile.getString("lores.subcategory.item_information");

                                PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                                        .addPlaceholder("%item_name%", Utils.strip(DeluxeBazaar.getInstance().itemsFile.getString("items." + item + ".name")))
                                        .addPlaceholder("%item_name_colored%", Utils.colorize(DeluxeBazaar.getInstance().itemsFile.getString("items." + item + ".name")))
                                        .addPlaceholder("%item_buy_price%", DeluxeBazaar.getInstance().numberFormat.format(BazaarItemHook.getBuyPrice(player, item, 1)))
                                        .addPlaceholder("%item_sell_price%", DeluxeBazaar.getInstance().numberFormat.format(BazaarItemHook.getSellPrice(player, item, 1)));

                                lore.add(Utils.colorize(Utils.replacePlaceholders(itemInformation, placeholderUtil)));
                            }
                        }

                        continue;
                    }

                    PlaceholderUtil placeholderUtil = new PlaceholderUtil()
                            .addPlaceholder("%product_amount%", String.valueOf(items.size()));
                    lore.add(Utils.colorize(Utils.replacePlaceholders(string, placeholderUtil)));
                }
            }

            ItemMeta meta = itemStack.getItemMeta();
            meta.setLore(lore);

            List<String> flags = DeluxeBazaar.getInstance().configFile.getStringList("settings.menu_item_flags");
            if (!flags.isEmpty()) {
                if (flags.contains("ALL"))
                    meta.addItemFlags(ItemFlag.values());
                else
                    for (String flag : flags)
                        meta.addItemFlags(ItemFlag.valueOf(flag));
            }

            if (isSubcategory) {
                String subcategoryName = section.getString("item.name");
                if (subcategoryName != null && !subcategoryName.isEmpty())
                    meta.setDisplayName(Utils.colorize(DeluxeBazaar.getInstance().categoriesFile.getString(this.category + ".color", "") + subcategoryName));
                else
                    meta.setDisplayName(Utils.colorize(DeluxeBazaar.getInstance().categoriesFile.getString(this.category + ".color", "") + Utils.strip(DeluxeBazaar.getInstance().itemsFile.getString("items." + section.getString("main_item") + ".name"))));
            }

            itemStack.setItemMeta(meta);
            Utils.addMenuItemFlags(itemStack);

            if (args.length > 2)
                slot = Integer.parseInt(args[2]);
            else
                i++;

            gui.setItem(slot - 1, ClickableItem.of(itemStack, (event) -> {
                if (category.equalsIgnoreCase("search") || !isSubcategory)
                    new ItemMenu(player).openMenu(args[0]);
                else
                    new SubcategoryMenu(player).openMenu(args[0]);
                Utils.playSound(player, "bazaar_item_click");
            }));
        }
    }

    public void loadCategories() {
        YamlConfiguration categoriesSection = DeluxeBazaar.getInstance().categoriesFile;
        if (categoriesSection != null)
            for (String entry : categoriesSection.getKeys(false)) {
                int slot = categoriesSection.getInt(entry + ".slot");
                if (slot > this.gui.getInventory().getSize())
                    continue;
                if (entry.equalsIgnoreCase("lore"))
                    continue;
                if (entry.equalsIgnoreCase("search") && !categoriesSection.getBoolean("search.enabled"))
                    continue;

                ItemStack item = DeluxeBazaar.getInstance().normalItems.get(entry);
                if (item == null)
                    continue;

                boolean permission = entry.equalsIgnoreCase("search") ? Utils.checkSearchPermission(player) : Utils.checkCategoryPermission(player, entry);
                String loreType = "not_selected";
                if (!permission)
                    loreType = "no_permission";
                else if (category.equalsIgnoreCase(entry))
                    loreType = "selected";

                item = item.clone();
                ItemMeta meta = item.getItemMeta();
                List<String> lore = new ArrayList<>();
                DeluxeBazaar.getInstance().messagesFile.getStringList("lores.category." + loreType)
                        .forEach(string -> lore.add(Utils.colorize(string
                                .replace("%item_name%", Utils.strip(categoriesSection.getString(entry + ".name")))
                                .replace("%category%", categoriesSection.getString(entry + ".name")))));
                meta.setLore(lore);
                item.setItemMeta(meta);

                Utils.addMenuItemFlags(item);

                if (!permission || category.equalsIgnoreCase(entry)) {
                    if (category.equalsIgnoreCase(entry))
                        item.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 10);

                    this.gui.setItem(slot - 1, ClickableItem.empty(item));
                } else
                    this.gui.setItem(slot - 1, ClickableItem.of(item, (event) -> {
                        openMenu(entry, 1);
                        Utils.playSound(player, "category_item_click");
                    }));
            }
    }

    public void modeItem() {
        String type = playerBazaar.getMode();

        ConfigurationSection modeSection = this.section.getConfigurationSection("modes." + type);
        if (modeSection == null)
            return;

        ItemStack mode = Utils.createItemFromSection(modeSection, null);
        if (mode == null)
            return;

        this.gui.setItem(modeSection.getInt("slot") - 1, ClickableItem.of(mode, (event) -> {
            if (type.equalsIgnoreCase("direct"))
                playerBazaar.setMode("advanced");
            else
                playerBazaar.setMode("direct");

            Utils.playSound(player, "mode_item_click");
            modeItem();
        }));

        loadSubcategories();
    }

    @Override
    public void inputResult(String input) {
        searchedItems = new ArrayList<>();
        searched = input;
        if (searched == null || searched.equals("")) {
            openMenu(playerBazaar.getCategory(), 1);
            return;
        }

        ConcurrentHashMap<String, BazaarItem> items = DeluxeBazaar.getInstance().bazaarItems;
        if (items != null && !items.isEmpty()) {
            List<Integer> slots = DeluxeBazaar.getInstance().categoriesFile.getConfigurationSection("search").getIntegerList("slots");
            int i = 0;
            int count = slots.size();

            for (Map.Entry<String, BazaarItem> item : items.entrySet()) {
                if (i >= count)
                    break;
                if (item == null)
                    continue;
                if (!Utils.checkItemPermission(player, item.getKey()))
                    continue;

                ItemMeta meta = item.getValue().getItemStack().getItemMeta();
                if (meta == null)
                    continue;

                String itemName = meta.getDisplayName();
                if (itemName == null || itemName.equals("")) {
                    itemName = Utils.strip(DeluxeBazaar.getInstance().itemsFile.getString("items." + item.getKey() + ".name"));

                    if (itemName == null || itemName.equals(""))
                        continue;
                }

                if (!Utils.strip(itemName.toLowerCase()).contains(searched.toLowerCase()))
                    continue;

                searchedItems.add(item.getKey());
                i++;
            }
        }

        openMenu("search", 1);
    }
}