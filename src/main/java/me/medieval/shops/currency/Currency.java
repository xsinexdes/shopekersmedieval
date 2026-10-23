package me.medieval.shops.currency;

import org.bukkit.Material;

import me.medieval.shops.MedievalEconomy;

public final class Currency {

	private final ItemData itemData;
	private final int value;

	Currency(Material type, int value) {
		this.itemData = new ItemData(type);
		this.value = Math.max(1, value);
	}

	public ItemData getItemData() {
		return itemData;
	}

	/** Valor en monedas base. */
	public int getValue() {
		return value;
	}

	public int getMaxStackSize() {
		return Math.max(1, itemData.getType().getMaxStackSize());
	}

	public String getDisplayName() {
		return MedievalEconomy.pretty(itemData.getType().name());
	}
}
