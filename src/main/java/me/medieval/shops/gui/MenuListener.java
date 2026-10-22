package me.medieval.shops.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class MenuListener implements Listener {

	@EventHandler(priority = EventPriority.HIGHEST)
	void onClick(InventoryClickEvent event) {
		Inventory top = event.getInventory();
		if (!(top.getHolder() instanceof Menu menu)) return;
		// Nunca se puede mover nada dentro de estos menus (ni desde el inventario del jugador).
		event.setCancelled(true);
		Inventory clicked = event.getClickedInventory();
		if (clicked == null || clicked != top) return;
		menu.handleClick(event);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	void onDrag(InventoryDragEvent event) {
		if (event.getInventory().getHolder() instanceof Menu) {
			event.setCancelled(true);
		}
	}
}
