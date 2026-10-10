package me.knb;

import java.util.UUID;

public class Game {
    private final int id;
    private final UUID creator;
    private final String creatorName;
    private final Choice choice;
    private final long bet;
    private final CurrencyType currency;

    public Game(int id, UUID creator, String creatorName, Choice choice, long bet, CurrencyType currency) {
        this.id = id;
        this.creator = creator;
        this.creatorName = creatorName;
        this.choice = choice;
        this.bet = bet;
        this.currency = currency;
    }

    public int getId() {
        return id;
    }

    public UUID getCreator() {
        return creator;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public Choice getChoice() {
        return choice;
    }

    public long getBet() {
        return bet;
    }

    public CurrencyType getCurrency() {
        return currency;
    }
}
