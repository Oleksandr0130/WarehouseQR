package com.warehouse.utils;

import com.warehouse.model.Item;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

public final class ItemComparator {
    private static final Collator RU = Collator.getInstance(new Locale("ru","RU"));
    static { RU.setStrength(Collator.PRIMARY); } // регистронезависимая сортировка

    // Имя: null уходит в конец, сам Item тоже страхуем
    public static final Comparator<Item> BY_NAME =
            Comparator.comparing(
                    (Item i) -> i == null ? null : i.getName(),
                    Comparator.nullsLast(RU)
            );

    // quantity/sold у вас примитивы int — NPE не будет, но подстрахуем null Item
    public static final Comparator<Item> BY_QUANTITY =
            Comparator.comparingInt(i -> i == null ? Integer.MIN_VALUE : i.getQuantity());

    public static final Comparator<Item> BY_SOLD =
            Comparator.comparingInt(i -> i == null ? Integer.MIN_VALUE : i.getSold());

    private ItemComparator() {}
}
