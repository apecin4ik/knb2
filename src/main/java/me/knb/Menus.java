package me.knb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public class Menus {

    public static final int[] CHOICE_SLOTS = {11, 13, 15};
    private static final int[] LIST_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};
    public static final int PAGE_SIZE = LIST_SLOTS.length;

    private final Knb plugin;

    public Menus(Knb plugin) {
        this.plugin = plugin;
    }

    // ===================== Предметы =====================

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            meta.addItemFlags(ItemFlag.values());
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack glow(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
            if (e != null) {
                meta.addEnchant(e, 1, true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            it.setItemMeta(meta);
        }
        return it;
    }

    private void fill(Inventory inv) {
        ItemStack bg = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        ItemStack edge = item(Material.ORANGE_STAINED_GLASS_PANE, " ");
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            boolean border = i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8;
            inv.setItem(i, border ? edge : bg);
        }
    }

    private Inventory create(KnbHolder holder, int size, String title) {
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);
        fill(inv);
        return inv;
    }

    private void show(Player p, Inventory inv) {
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    // ===================== Меню =====================

    public void openMain(Player p) {
        KnbHolder h = new KnbHolder(KnbHolder.Type.MAIN, 0, 0, null);
        Inventory inv = create(h, 27, "§6§l✂ Камень · Ножницы · Бумага");

        inv.setItem(11, glow(item(Material.EMERALD, "§a§lИграть",
                "§7Список игр, которые создали",
                "§7другие игроки. Выбери соперника",
                "§7и попробуй его обыграть!",
                " ",
                "§7Открытых игр: §e" + plugin.getGames().size(),
                " ",
                "§e▶ Нажми, чтобы открыть")));

        List<String> lore = new ArrayList<>();
        lore.add("§7Выбери валюту, ход и поставь сумму.");
        lore.add("§7Твоя игра появится в списке,");
        lore.add("§7и соперник сможет её принять.");
        lore.add(" ");
        for (CurrencyType c : CurrencyType.values()) {
            if (plugin.isAvailable(c)) {
                lore.add("§7Мин. ставка (" + plugin.currencyName(c) + "): §e" + plugin.fmt(c, plugin.getMinBet(c)));
            }
        }
        lore.add(" ");
        lore.add("§e▶ Нажми, чтобы создать");
        inv.setItem(15, glow(item(Material.GOLD_INGOT, "§6§lСоздать игру", lore.toArray(new String[0]))));

        inv.setItem(22, item(Material.BARRIER, "§c§lЗакрыть"));
        show(p, inv);
    }

    public void openList(Player p, int page) {
        List<Game> all = new ArrayList<>(plugin.getGames());
        int maxPage = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, maxPage - 1));

        KnbHolder h = new KnbHolder(KnbHolder.Type.LIST, page, 0, null);
        Inventory inv = create(h, 54, "§6§lОткрытые игры §8(" + (page + 1) + "/" + maxPage + ")");

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < all.size(); i++) {
            Game g = all.get(start + i);
            boolean mine = g.getCreator().equals(p.getUniqueId());
            CurrencyType cur = g.getCurrency();

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta im = head.getItemMeta();
            if (im instanceof SkullMeta) {
                SkullMeta sm = (SkullMeta) im;
                sm.setOwningPlayer(Bukkit.getOfflinePlayer(g.getCreator()));
                sm.setDisplayName((mine ? "§e" : "§b") + g.getCreatorName());
                List<String> lore = new ArrayList<>();
                lore.add("§7Ставка: §a" + plugin.fmt(cur, g.getBet()));
                lore.add("§7Выигрыш: §6" + plugin.fmt(cur, plugin.payoutFor(g.getBet())));
                lore.add(" ");
                if (mine) {
                    lore.add("§7Твой ход: " + g.getChoice().getColored());
                    lore.add("§c▶ Это твоя игра. Нажми, чтобы отменить");
                } else {
                    lore.add("§7Ход соперника: §8§k???");
                    lore.add("§a▶ Нажми, чтобы играть");
                }
                sm.setLore(lore);
                sm.addItemFlags(ItemFlag.values());
                head.setItemMeta(sm);
            }
            int slot = LIST_SLOTS[i];
            inv.setItem(slot, head);
            h.getSlotToGame().put(slot, g.getId());
        }

        if (all.isEmpty()) {
            inv.setItem(22, item(Material.COBWEB, "§7Пока нет открытых игр",
                    "§7Создай свою — вернись в меню",
                    "§7и нажми «Создать игру»."));
        }
        if (page > 0) {
            inv.setItem(48, item(Material.ARROW, "§e« Назад"));
        }
        inv.setItem(49, item(Material.BARRIER, "§c§lВ меню"));
        if (page < maxPage - 1) {
            inv.setItem(50, item(Material.ARROW, "§eВперёд »"));
        }
        show(p, inv);
    }

    /** Шаг 1 создания игры: выбор валюты. */
    public void openCurrency(Player p) {
        KnbHolder h = new KnbHolder(KnbHolder.Type.CURRENCY, 0, 0, null);
        Inventory inv = create(h, 27, "§6§lСоздать игру §8» §7валюта");

        inv.setItem(11, currencyItem(p, CurrencyType.VAULT, Material.GOLD_INGOT, "§e§l"));
        inv.setItem(15, currencyItem(p, CurrencyType.POINTS, Material.NETHER_STAR, "§b§l"));
        inv.setItem(22, item(Material.ARROW, "§e« Назад"));
        show(p, inv);
    }

    private ItemStack currencyItem(Player p, CurrencyType c, Material mat, String colorPrefix) {
        String name = plugin.currencyName(c);
        if (!plugin.isAvailable(c)) {
            return item(Material.BARRIER, "§c§l" + name, "§7Эта валюта сейчас недоступна.");
        }
        return glow(item(mat, colorPrefix + name,
                "§7Играть на " + name + ".",
                " ",
                "§7Минимум: §e" + plugin.fmt(c, plugin.getMinBet(c)),
                "§7Максимум: §e" + plugin.fmt(c, plugin.getMaxBet(c)),
                "§7Твой баланс: §a" + plugin.fmt(c, plugin.balance(c, p.getUniqueId())),
                " ",
                "§e▶ Нажми, чтобы выбрать"));
    }

    /** Шаг 2 создания игры: выбор хода. */
    public void openCreate(Player p, CurrencyType currency) {
        KnbHolder h = new KnbHolder(KnbHolder.Type.CREATE, 0, 0, currency);
        Inventory inv = create(h, 27, "§6§lСоздать игру §8» §7твой ход");

        Choice[] choices = Choice.values();
        for (int i = 0; i < choices.length; i++) {
            Choice c = choices[i];
            inv.setItem(CHOICE_SLOTS[i], item(c.getIcon(), c.getColored(),
                    "§7Поставить на этот ход.",
                    "§7Бьёт: " + c.victim().getColored(),
                    "§7Валюта: §e" + plugin.currencyName(currency),
                    " ",
                    "§e▶ Нажми, затем введи ставку в чат"));
        }
        inv.setItem(22, item(Material.ARROW, "§e« Назад"));
        show(p, inv);
    }

    public void openPick(Player p, Game g) {
        KnbHolder h = new KnbHolder(KnbHolder.Type.PICK, 0, g.getId(), g.getCurrency());
        Inventory inv = create(h, 27,
                "§6§lИгра с " + g.getCreatorName() + " §8» §7" + plugin.fmt(g.getCurrency(), g.getBet()));

        inv.setItem(4, item(Material.PAPER, "§e§lВыбери свой ход",
                "§7Ход соперника скрыт.",
                "§7Ставка спишется после выбора.",
                "§7Победа: §6+" + plugin.fmt(g.getCurrency(), plugin.payoutFor(g.getBet()) - g.getBet())));

        Choice[] choices = Choice.values();
        for (int i = 0; i < choices.length; i++) {
            Choice c = choices[i];
            inv.setItem(CHOICE_SLOTS[i], item(c.getIcon(), c.getColored(),
                    "§7Бьёт: " + c.victim().getColored(),
                    " ",
                    "§e▶ Нажми, чтобы сыграть этим ходом"));
        }
        inv.setItem(22, item(Material.ARROW, "§e« К списку игр"));
        show(p, inv);
    }
}
