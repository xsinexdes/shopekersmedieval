package me.medieval.shops.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import me.medieval.shops.MedievalEconomy;

/** Base de los menus de cofre propios (mercado, grafica, banco). */
public abstract class Menu implements InventoryHolder {

	protected final MedievalEconomy economy;
	protected final Player player;
	protected Inventory inventory;

	protected Menu(MedievalEconomy economy, Player player) {
		this.economy = economy;
		this.player = player;
	}

	@Override
	public Inventory getInventory() {
		return inventory;
	}

	protected void create(int size, String title) {
		this.inventory = Bukkit.createInventory(this, size, title);
	}

	public void open() {
		render();
		player.openInventory(inventory);
	}

	/** Dibuja (o redibuja) el contenido. */
	protected abstract void render();

	protected abstract void onClick(int slot, ClickType click);

	final void handleClick(InventoryClickEvent event) {
		onClick(event.getRawSlot(), event.getClick());
	}

	protected void refresh() {
		inventory.clear();
		render();
	}
}
