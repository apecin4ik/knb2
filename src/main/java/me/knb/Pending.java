package me.knb;

/** Игрок выбрал валюту и ход и сейчас должен написать ставку в чат. */
public class Pending {
    private final Choice choice;
    private final CurrencyType currency;

    public Pending(Choice choice, CurrencyType currency) {
        this.choice = choice;
        this.currency = currency;
    }

    public Choice getChoice() {
        return choice;
    }

    public CurrencyType getCurrency() {
        return currency;
    }
}
