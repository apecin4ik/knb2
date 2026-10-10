package me.knb;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class Knb extends JavaPlugin {

    private final Map<Integer, Game> games = new LinkedHashMap<>();
    private final Map<UUID, Pending> awaitingBet = new ConcurrentHashMap<>();
    private int nextId = 1;

    private Economy econ;
    private PointsHook points;
    private Menus menus;

    private String prefix;
    private String vaultName;
    private String pointsName;
    private long vaultMin;
    private long vaultMax;
    private long pointsMin;
    private long pointsMax;
    private double commission;
    private int maxGames;
    private boolean broadcast;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupEconomy();
        if (econ == null && points == null) {
            getLogger().severe("Не найден ни Vault (с плагином экономики), ни PlayerPoints! Отключаюсь.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Валюты: Vault=" + (econ != null) + ", PlayerPoints=" + (points != null));
        loadSettings();
        menus = new Menus(this);
        getServer().getPluginManager().registerEvents(new KnbListener(this), this);
        if (getCommand("knb") != null) {
            getCommand("knb").setExecutor(this);
        }
    }

    @Override
    public void onDisable() {
        // Возвращаем замороженные ставки
        for (Game g : games.values()) {
            deposit(g.getCurrency(), g.getCreator(), g.getBet());
        }
        games.clear();
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                econ = rsp.getProvider();
            }
        }
        points = PointsHook.create();
    }

    private void loadSettings() {
        reloadConfig();
        prefix = color(getConfig().getString("prefix", "&6&lКНБ &8» &7"));
        vaultName = getConfig().getString("currency", "дублонов");
        pointsName = getConfig().getString("points-currency", "Лей✞");
        vaultMin = Math.max(1, getConfig().getLong("min-bet", 100));
        vaultMax = Math.max(vaultMin, getConfig().getLong("max-bet", 1000000));
        pointsMin = Math.max(1, getConfig().getLong("points-min-bet", 10));
        pointsMax = Math.max(pointsMin, Math.min(getConfig().getLong("points-max-bet", 100000), Integer.MAX_VALUE));
        commission = Math.max(0, Math.min(100, getConfig().getDouble("commission-percent", 0)));
        maxGames = Math.max(1, getConfig().getInt("max-games-per-player", 3));
        broadcast = getConfig().getBoolean("broadcast-new-game", true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("knb.admin")) {
                sender.sendMessage(prefix + "§cНет прав.");
                return true;
            }
            loadSettings();
            sender.sendMessage(prefix + "§aКонфиг перезагружен.");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        Player p = (Player) sender;
        if (!p.hasPermission("knb.use")) {
            msg(p, "§cНет прав.");
            return true;
        }
        menus.openMain(p);
        return true;
    }

    // ===================== Валюты =====================

    public boolean isAvailable(CurrencyType c) {
        return c == CurrencyType.VAULT ? econ != null : points != null;
    }

    public String currencyName(CurrencyType c) {
        return c == CurrencyType.VAULT ? vaultName : pointsName;
    }

    public long getMinBet(CurrencyType c) {
        return c == CurrencyType.VAULT ? vaultMin : pointsMin;
    }

    public long getMaxBet(CurrencyType c) {
        return c == CurrencyType.VAULT ? vaultMax : pointsMax;
    }

    public String fmt(CurrencyType c, long amount) {
        return String.format(Locale.ROOT, "%,d", amount).replace(',', ' ') + " " + currencyName(c);
    }

    public long balance(CurrencyType c, UUID id) {
        if (c == CurrencyType.VAULT && econ != null) {
            return (long) Math.floor(econ.getBalance(Bukkit.getOfflinePlayer(id)));
        }
        if (c == CurrencyType.POINTS && points != null) {
            return points.look(id);
        }
        return 0L;
    }

    public boolean has(CurrencyType c, UUID id, long amount) {
        return balance(c, id) >= amount;
    }

    public boolean withdraw(CurrencyType c, UUID id, long amount) {
        if (c == CurrencyType.VAULT && econ != null) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            return econ.withdrawPlayer(op, (double) amount).transactionSuccess();
        }
        if (c == CurrencyType.POINTS && points != null) {
            return points.take(id, amount);
        }
        return false;
    }

    public void deposit(CurrencyType c, UUID id, long amount) {
        if (c == CurrencyType.VAULT && econ != null) {
            econ.depositPlayer(Bukkit.getOfflinePlayer(id), (double) amount);
        } else if (c == CurrencyType.POINTS && points != null) {
            points.give(id, amount);
        }
    }

    /** Сколько получает победитель: своя ставка + ставка соперника за вычетом комиссии. */
    public long payoutFor(long bet) {
        return bet + (long) Math.floor(bet * (1.0 - commission / 100.0));
    }

    // ===================== Игра =====================

    public void createGame(Player p, Choice choice, CurrencyType cur, long bet) {
        if (!isAvailable(cur)) {
            msg(p, "§cЭта валюта сейчас недоступна.");
            return;
        }
        if (bet < getMinBet(cur)) {
            msg(p, "§cМинимальная ставка: §e" + fmt(cur, getMinBet(cur)));
            return;
        }
        if (bet > getMaxBet(cur)) {
            msg(p, "§cМаксимальная ставка: §e" + fmt(cur, getMaxBet(cur)));
            return;
        }
        long mine = games.values().stream().filter(g -> g.getCreator().equals(p.getUniqueId())).count();
        if (mine >= maxGames) {
            msg(p, "§cУ тебя уже максимум открытых игр (§e" + maxGames + "§c). Дождись соперника или отмени одну.");
            return;
        }
        if (!has(cur, p.getUniqueId(), bet)) {
            msg(p, "§cНе хватает денег. Баланс: §e" + fmt(cur, balance(cur, p.getUniqueId())));
            return;
        }
        if (!withdraw(cur, p.getUniqueId(), bet)) {
            msg(p, "§cНе удалось списать ставку.");
            return;
        }
        Game g = new Game(nextId++, p.getUniqueId(), p.getName(), choice, bet, cur);
        games.put(g.getId(), g);
        msg(p, "§aИгра создана! Твой ход: " + choice.getColored() + "§a, ставка: §e" + fmt(cur, bet)
                + "§a. Ставка заморожена до конца игры.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        if (broadcast) {
            Bukkit.broadcastMessage(prefix + "§e" + p.getName() + " §7создал игру на §a" + fmt(cur, bet)
                    + "§7! Играть: §e/knb");
        }
    }

    public void cancelGame(Player p, int gameId) {
        Game g = games.get(gameId);
        if (g == null || !g.getCreator().equals(p.getUniqueId())) {
            msg(p, "§cЭта игра уже недоступна.");
            return;
        }
        games.remove(gameId);
        deposit(g.getCurrency(), g.getCreator(), g.getBet());
        msg(p, "§eИгра отменена, ставка §a" + fmt(g.getCurrency(), g.getBet()) + " §eвозвращена.");
    }

    public void play(Player joiner, int gameId, Choice pick) {
        Game g = games.get(gameId);
        if (g == null) {
            msg(joiner, "§cЭта игра уже недоступна.");
            joiner.closeInventory();
            return;
        }
        if (g.getCreator().equals(joiner.getUniqueId())) {
            msg(joiner, "§cНельзя играть с самим собой.");
            return;
        }
        CurrencyType cur = g.getCurrency();
        long bet = g.getBet();
        if (!isAvailable(cur)) {
            msg(joiner, "§cЭта валюта сейчас недоступна.");
            return;
        }
        if (!has(cur, joiner.getUniqueId(), bet)) {
            msg(joiner, "§cНе хватает денег для ставки §e" + fmt(cur, bet));
            joiner.closeInventory();
            return;
        }
        if (!withdraw(cur, joiner.getUniqueId(), bet)) {
            msg(joiner, "§cНе удалось списать ставку.");
            return;
        }
        games.remove(gameId);

        Player host = Bukkit.getPlayer(g.getCreator());
        Choice theirs = g.getChoice();
        long payout = payoutFor(bet);
        long net = payout - bet;

        if (pick == theirs) {
            deposit(cur, joiner.getUniqueId(), bet);
            deposit(cur, g.getCreator(), bet);
            String text = "§e§lНИЧЬЯ! §7Оба выбрали " + pick.getColored() + "§7. Ставки возвращены.";
            result(joiner, text, pick, theirs, g.getCreatorName(), Sound.BLOCK_NOTE_BLOCK_PLING);
            result(host, text, theirs, pick, joiner.getName(), Sound.BLOCK_NOTE_BLOCK_PLING);
        } else if (pick.beats(theirs)) {
            deposit(cur, joiner.getUniqueId(), payout);
            result(joiner, "§a§lПОБЕДА! §7Ты получил §6" + fmt(cur, payout) + " §7(чистыми +" + fmt(cur, net) + ")",
                    pick, theirs, g.getCreatorName(), Sound.ENTITY_PLAYER_LEVELUP);
            result(host, "§c§lПОРАЖЕНИЕ. §7Ты потерял §c" + fmt(cur, bet),
                    theirs, pick, joiner.getName(), Sound.ENTITY_VILLAGER_NO);
        } else {
            deposit(cur, g.getCreator(), payout);
            result(host, "§a§lПОБЕДА! §7Ты получил §6" + fmt(cur, payout) + " §7(чистыми +" + fmt(cur, net) + ")",
                    theirs, pick, joiner.getName(), Sound.ENTITY_PLAYER_LEVELUP);
            result(joiner, "§c§lПОРАЖЕНИЕ. §7Ты потерял §c" + fmt(cur, bet),
                    pick, theirs, g.getCreatorName(), Sound.ENTITY_VILLAGER_NO);
        }
    }

    private void result(Player p, String headline, Choice mine, Choice theirs, String opponent, Sound sound) {
        if (p == null) {
            return;
        }
        p.closeInventory();
        String sep = "§8§m                    §r";
        p.sendMessage(sep);
        p.sendMessage("§7Игра против §e" + opponent);
        p.sendMessage("§7Твой ход: " + mine.getColored() + " §8| §7Ход соперника: " + theirs.getColored());
        p.sendMessage(headline);
        p.sendMessage(sep);
        int i = headline.indexOf(' ');
        String title = i > 0 ? headline.substring(0, i) : headline;
        String sub = i > 0 ? headline.substring(i + 1) : "";
        p.sendTitle(title, sub, 5, 50, 10);
        p.playSound(p.getLocation(), sound, 1f, 1f);
    }

    // ===================== Утилиты =====================

    public void msg(Player p, String text) {
        p.sendMessage(prefix + text);
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public Collection<Game> getGames() {
        return games.values();
    }

    public Game getGame(int id) {
        return games.get(id);
    }

    public Menus getMenus() {
        return menus;
    }

    public Map<UUID, Pending> getAwaitingBet() {
        return awaitingBet;
    }
}
