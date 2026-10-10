package me.knb;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class KnbHolder implements InventoryHolder {

    public enum Type {
        MAIN, LIST, CURRENCY, CREATE, PICK
    }

    private final Map<Integer, Integer> slotToGame = new HashMap<>();
    private final Type type;
    private final int page;
    private final int gameId;
    private final CurrencyType currency;
    private Inventory inventory;

    public KnbHolder(Type type, int page, int gameId, CurrencyType currency) {
        this.type = type;
        this.page = page;
        this.gameId = gameId;
        this.currency = currency;
    }

    public Type getType() {
        return type;
    }

    public int getPage() {
        return page;
    }

    public int getGameId() {
        return gameId;
    }

    public CurrencyType getCurrency() {
        return currency;
    }

    public Map<Integer, Integer> getSlotToGame() {
        return slotToGame;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
