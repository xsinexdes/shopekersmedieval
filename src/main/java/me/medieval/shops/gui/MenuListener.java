package me.medieval.shops.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class MenuListener implements Listener {

	@EventHandler(priority = EventPriority.HIGHEST)
	void onClick(InventoryClickEvent event) {
		Inventory top = event.getInventory();
		if (!(top.getHolder() instanceof Menu menu)) return;
		Inventory clicked = event.getClickedInventory();
		// Nunca se puede mover nada dentro de estos menus. Unica excepcion: en el panel de
		// configuracion se pueden tomar items del propio inventario (click normal) para soltarlos
		// en un slot de moneda; shift-click, teclas numericas, etc. siguen bloqueados.
		boolean pickup = menu.allowsInventoryPickup() && clicked != null && clicked != top
				&& (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT);
		if (!pickup) event.setCancelled(true);
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
