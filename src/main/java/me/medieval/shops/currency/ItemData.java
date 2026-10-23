package me.medieval.shops.currency;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Item de moneda (se compara solo por tipo de material). */
public final class ItemData {

	private final Material type;

	ItemData(Material type) {
		this.type = type;
	}

	public Material getType() {
		return type;
	}

	public boolean matches(ItemStack item) {
		return item != null && item.getType() == type;
	}

	public ItemStack createItemStack(int amount) {
		return new ItemStack(type, Math.max(1, amount));
	}
}
