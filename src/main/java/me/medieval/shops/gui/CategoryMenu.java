package me.medieval.shops.gui;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import me.medieval.shops.MedievalEconomy;
import me.medieval.shops.Sfx;
import me.medieval.shops.market.MarketCategory;
import me.medieval.shops.market.MarketManager;

/** Menu principal del mercado: una puerta por categoria, cada una con su propio fondo. */
public final class CategoryMenu extends Menu {

	private static final int[] SLOTS = { 11, 13, 15, 29, 31, 33 };
	private static final int ALL_SLOT = 22;

	private final MarketManager market;

	public CategoryMenu(MedievalEconomy economy, Player player) {
		super(economy, player);
		this.market = economy.market();
		create(54, Gui.title(economy,
				economy.config().getString("gui.titulos.mercado", "&0Mercado del Reino"),
				Gui.BG_MARKET));
	}

	@Override
	protected void render() {
		if (!Gui.usesBackgrounds(economy)) {
			for (int i = 0; i < 54; i++) inventory.setItem(i, Gui.filler());
		}
		MarketCategory[] categories = MarketCategory.values();
		for (int i = 0; i < categories.length; i++) {
			MarketCategory category = categories[i];
			int count = 0;
			String hottest = null;
			double hottestDev = 0.0D;
			for (String key : market.keys()) {
				if (MarketCategory.of(key) != category) continue;
				count++;
				double dev = Math.abs(market.getMultiplier(key) - 1.0D);
				if (dev > hottestDev) {
					hottestDev = dev;
					hottest = key;
				}
			}
			List<String> lore = new ArrayList<>();
			for (String line : category.getDescription()) lore.add(line);
			lore.add("");
			lore.add("&7Objetos con precio: &f" + count);
			if (hottest != null && hottestDev > 0.02D) {
				double m = market.getMultiplier(hottest);
				lore.add("&7Mayor cambio: &f" + MedievalEconomy.pretty(hottest) + " "
						+ MarketMenu.color(m) + MarketMenu.percent(m));
			}
			lore.add("");
			lore.add("&eClick: &fentrar");
			inventory.setItem(SLOTS[i], Gui.item(category.getIcon(), category.getDisplayName(), lore));
		}
		inventory.setItem(ALL_SLOT, Gui.button(economy, Gui.BTN_SCROLL, Material.PAPER,
				"&6&lTodos los objetos", "&7Lista completa de precios.", "", "&eClick: &fver todo"));

		List<String> eventLore = new ArrayList<>();
		List<MarketManager.MarketEvent> events = market.activeEvents();
		if (events.isEmpty()) eventLore.add("&7No hay eventos activos.");
		for (MarketManager.MarketEvent event : events) {
			eventLore.add(event.name + " &7(" + MarketMenu.mult(event.mult) + ", quedan "
					+ event.minutesLeft() + " min)");
		}
		inventory.setItem(47, Gui.button(economy, Gui.BTN_BANNER, Material.WHITE_BANNER,
				"&6Eventos del mercado", eventLore));
		inventory.setItem(49, Gui.button(economy, Gui.BTN_CLOSE, Material.BARRIER, "&cCerrar"));
		inventory.setItem(51, Gui.button(economy, Gui.BTN_BANK, Material.GOLD_BLOCK,
				"&6Banco del Reino", "&7Click para abrir tu cuenta"));
	}

	@Override
	protected void onClick(int slot, ClickType click) {
		MarketCategory[] categories = MarketCategory.values();
		for (int i = 0; i < SLOTS.length; i++) {
			if (slot == SLOTS[i]) {
				Sfx.play(player, Sfx.Type.PAGE);
				new MarketMenu(economy, player, 0, categories[i]).open();
				return;
			}
		}
		switch (slot) {
		case ALL_SLOT -> {
			Sfx.play(player, Sfx.Type.PAGE);
			new MarketMenu(economy, player, 0, null).open();
		}
		case 51 -> {
			Sfx.play(player, Sfx.Type.CLICK);
			new BankMenu(economy, player).open();
		}
		case 49 -> player.closeInventory();
		default -> {
			// Sin accion.
		}
		}
	}
}
