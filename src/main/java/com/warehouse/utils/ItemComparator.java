package com.warehouse.utils;

import com.warehouse.model.Item;

import java.util.Comparator;

public class ItemComparator {
    // Сравнение по имени товара (по алфавиту)
    public static final Comparator<Item> BY_NAME = Comparator.comparing(Item::getName, String.CASE_INSENSITIVE_ORDER);

    // Сравнение по количеству товара (по возрастанию)
    public static final Comparator<Item> BY_QUANTITY = Comparator.comparingInt(Item::getQuantity);

    // Сравнение по количеству проданных товаров (по возрастанию)
    public static final Comparator<Item> BY_SOLD = Comparator.comparingInt(Item::getSold);
}

