package me.knb;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

public class KnbListener implements Listener {

    private final Knb plugin;

    public KnbListener(Knb plugin) {
        this.plugin = plugin;
    }

    private Choice choiceBySlot(int slot) {
        Choice[] choices = Choice.values();
        for (int i = 0; i < choices.length; i++) {
            if (Menus.CHOICE_SLOTS[i] == slot) {
                return choices[i];
            }
        }
        return null;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof KnbHolder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof KnbHolder)) {
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() != top) {
            return;
        }
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getWhoClicked();
        KnbHolder h = (KnbHolder) top.getHolder();
        Menus menus = plugin.getMenus();
        int slot = e.getRawSlot();

        switch (h.getType()) {
            case MAIN:
                if (slot == 11) {
                    menus.openList(p, 0);
                } else if (slot == 15) {
                    menus.openCurrency(p);
                } else if (slot == 22) {
                    p.closeInventory();
                }
                break;

            case LIST:
                if (slot == 49) {
                    menus.openMain(p);
                } else if (slot == 48 && h.getPage() > 0) {
                    menus.openList(p, h.getPage() - 1);
                } else if (slot == 50) {
                    menus.openList(p, h.getPage() + 1);
                } else {
                    Integer id = h.getSlotToGame().get(slot);
                    if (id == null) {
                        break;
                    }
                    Game g = plugin.getGame(id);
                    if (g == null) {
                        plugin.msg(p, "§cЭта игра уже недоступна.");
                        menus.openList(p, h.getPage());
                    } else if (g.getCreator().equals(p.getUniqueId())) {
                        plugin.cancelGame(p, id);
                        menus.openList(p, h.getPage());
                    } else {
                        menus.openPick(p, g);
                    }
                }
                break;

            case CURRENCY:
                if (slot == 11 || slot == 15) {
                    CurrencyType c = slot == 11 ? CurrencyType.VAULT : CurrencyType.POINTS;
                    if (!plugin.isAvailable(c)) {
                        plugin.msg(p, "§cЭта валюта сейчас недоступна.");
                    } else {
                        menus.openCreate(p, c);
                    }
                } else if (slot == 22) {
                    menus.openMain(p);
                }
                break;

            case CREATE:
                if (slot == 22) {
                    menus.openCurrency(p);
                    break;
                }
                Choice c = choiceBySlot(slot);
                if (c != null) {
                    CurrencyType cur = h.getCurrency();
                    plugin.getAwaitingBet().put(p.getUniqueId(), new Pending(c, cur));
                    p.closeInventory();
                    p.sendMessage("");
                    plugin.msg(p, "Твой ход: " + c.getColored() + "§7. Напиши в чат §aсумму ставки §7(например §e2000§7 или §e5k§7).");
                    plugin.msg(p, "Валюта: §e" + plugin.currencyName(cur) + "§7. Минимум: §e"
                            + plugin.fmt(cur, plugin.getMinBet(cur)) + "§7, максимум: §e"
                            + plugin.fmt(cur, plugin.getMaxBet(cur)));
                    plugin.msg(p, "Для отмены напиши §ccancel §7или §cотмена§7.");
                }
                break;

            case PICK:
                if (slot == 22) {
                    menus.openList(p, 0);
                    break;
                }
                Choice pick = choiceBySlot(slot);
                if (pick != null) {
                    plugin.play(p, h.getGameId(), pick);
                }
                break;

            default:
                break;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        Pending pending = plugin.getAwaitingBet().get(p.getUniqueId());
        if (pending == null) {
            return;
        }
        e.setCancelled(true);
        String text = e.getMessage().trim().toLowerCase();
        plugin.getServer().getScheduler().runTask(plugin, () -> handleBet(text, p, pending));
    }

    private void handleBet(String text, Player p, Pending pending) {
        if (text.equals("cancel") || text.equals("отмена")) {
            plugin.getAwaitingBet().remove(p.getUniqueId());
            plugin.msg(p, "§eСоздание игры отменено.");
            return;
        }
        Long amount = parseAmount(text);
        if (amount == null) {
            plugin.msg(p, "§cЭто не похоже на сумму. Напиши число (например §e2000§c) или §ecancel§c.");
            return;
        }
        CurrencyType cur = pending.getCurrency();
        if (amount < plugin.getMinBet(cur)) {
            plugin.msg(p, "§cМинимальная ставка: §e" + plugin.fmt(cur, plugin.getMinBet(cur)));
            return;
        }
        if (amount > plugin.getMaxBet(cur)) {
            plugin.msg(p, "§cМаксимальная ставка: §e" + plugin.fmt(cur, plugin.getMaxBet(cur)));
            return;
        }
        plugin.getAwaitingBet().remove(p.getUniqueId());
        plugin.createGame(p, pending.getChoice(), cur, amount);
    }

    private Long parseAmount(String text) {
        String s = text.replace(" ", "").replace(',', '.');
        double mult = 1;
        if (s.endsWith("k") || s.endsWith("к")) {
            mult = 1_000;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("m") || s.endsWith("м")) {
            mult = 1_000_000;
            s = s.substring(0, s.length() - 1);
        }
        try {
            double v = Double.parseDouble(s);
            if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0) {
                return null;
            }
            return Math.round(v * mult);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.getAwaitingBet().remove(e.getPlayer().getUniqueId());
    }
}
