package me.medieval.shops.city;

import java.util.List;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import me.medieval.shops.MedievalEconomy;
import me.medieval.shops.Sfx;
import me.medieval.shops.gui.ButtonMenu;
import me.medieval.shops.gui.Gui;
import me.medieval.shops.gui.Layouts;
import me.medieval.shops.market.MarketCategory;

/** Que se vende mas caro o mas barato en la ciudad, por categoria. */
public final class CityPricesMenu extends ButtonMenu {

	private final City city;

	public CityPricesMenu(MedievalEconomy economy, Player player, City city) {
		super(economy, player, Layouts.CITY_PRICES, "&0Precios: " + city.name);
		this.city = city;
	}

	private static String idOf(MarketCategory category) {
		return switch (category) {
		case FORJA -> "forja";
		case GRANJA -> "granja";
		case ALQUIMIA -> "alquimia";
		case MINERALES -> "minas";
		case CONSTRUCCION -> "construccion";
		case VARIOS -> "bazar";
		};
	}

	private static String pct(double v) {
		return String.format(Locale.ROOT, "%s%.0f%%", v >= 0 ? "+" : "", v);
	}

	@Override
	protected void buildButtons() {
		inventory.setItem(4, Gui.item(Material.GOLD_INGOT, "&6Precios de " + city.name,
				"&7Positivo = mas caro (vende y paga mas).", "&7Negativo = mas barato.",
				"&7Se suma al mercado normal, a los eventos y al stock."));

		for (MarketCategory category : MarketCategory.values()) {
			double current = city.mods.getOrDefault(category, 0.0D);
			String color = current > 0 ? "&c" : current < 0 ? "&a" : "&e";
			button(idOf(category), category.getIcon(), category.getDisplayName() + " &7" + color + pct(current),
					List.of("&7Aqui lo de esta categoria se vende "
							+ (current > 0 ? "&cmas caro" : current < 0 ? "&amas barato" : "&eal precio normal") + "&7.",
							"", "&eClick izq: &f+5%   &eClick der: &f-5%", "&eShift: &f+-1%"),
					click -> {
						double d = switch (click) {
						case LEFT -> 5.0D;
						case RIGHT -> -5.0D;
						case SHIFT_LEFT -> 1.0D;
						case SHIFT_RIGHT -> -1.0D;
						default -> 0.0D;
						};
						if (d == 0.0D) return;
						city.mods.put(category, Math.max(-50.0D, Math.min(100.0D, current + d)));
						changed();
					});
		}

		button("reset", Material.RED_DYE, "&cReiniciar todo", List.of("&7Vuelve todos los modificadores a 0%."), click -> {
			city.mods.clear();
			changed();
		});
		button("volver", Material.ARROW, "&eVolver", List.of(), click -> new CityMenu(economy, player, city).open());
		button("cerrar", Material.BARRIER, "&cCerrar", List.of(), click -> player.closeInventory());
	}

	private void changed() {
		economy.cities().markDirty();
		economy.pricing().refreshAll();
		Sfx.play(player, Sfx.Type.CLICK);
		refresh();
	}
}
