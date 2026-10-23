package me.medieval.shops.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;

import me.medieval.shops.MedievalEconomy;
import me.medieval.shops.ShopProfiles;
import me.medieval.shops.Sfx;
import me.medieval.shops.bank.BankManager;
import me.medieval.shops.currency.Currencies;
import me.medieval.shops.currency.Currency;
import me.medieval.shops.currency.CurrencySet;
import me.medieval.shops.market.ShopPricing;

/**
 * Panel de configuracion de UN mercader: precios dinamicos, banquero y moneda propia.
 * La moneda se elige tomando un item con el mouse y haciendo click en el slot correspondiente.
 */
public final class ShopConfigMenu extends Menu {

	private final Shopkeeper shop;

	public ShopConfigMenu(MedievalEconomy economy, Player player, Shopkeeper shop) {
		super(economy, player);
		this.shop = shop;
		create(54, Gui.title(economy, "&0Mercader #" + shop.getId(), Gui.BG_CONFIG));
	}

	@Override
	boolean allowsInventoryPickup() {
		return true;
	}

	@Override
	protected void render() {
		UUID id = shop.getUniqueId();
		ShopPricing pricing = economy.pricing();
		ShopProfiles profiles = economy.profiles();
		boolean dynamic = pricing.isMarked(id);
		boolean banker = profiles.isBanker(id);
		boolean custom = profiles.hasCustomCurrency(id);
		CurrencySet set = Currencies.of(id);
		Currency base = set.getBase();
		Currency high = set.getHighOrNull();

		if (!Gui.usesBackgrounds(economy)) {
			for (int i = 0; i < 54; i++) inventory.setItem(i, Gui.filler());
		}

		String name = shop.getName();
		inventory.setItem(4, Gui.item(Material.NAME_TAG, "&6Mercader #" + shop.getId(),
				"&7Nombre: &f" + (name == null || name.isEmpty() ? "(sin nombre)" : name),
				"&7Posicion: &f" + shop.getPositionString(),
				"&7Precios dinamicos: " + (dynamic ? "&asi" : "&cno"),
				"&7Banquero: " + (banker ? "&asi" : "&cno"),
				"&7Moneda: &f" + base.getDisplayName() + (high == null ? "" : " / " + high.getDisplayName())
						+ (custom ? " &7(propia)" : " &7(global)")));

		inventory.setItem(20, Gui.item(dynamic ? Material.LIME_DYE : Material.GRAY_DYE,
				"&ePrecios dinamicos: " + (dynamic ? "&aACTIVADOS" : "&cdesactivados"),
				"&7Los precios de este mercader siguen la oferta y la demanda.",
				"&7Al activarlos, los precios que tiene AHORA son el precio base.",
				"", "&eClick: &fcambiar"));
		inventory.setItem(24, Gui.item(banker ? Material.GOLD_BLOCK : Material.IRON_BLOCK,
				"&eBanquero: " + (banker ? "&aSI" : "&cno"),
				"&7Al hacerle click se abre SU banco (cuentas y moneda propias).",
				"&7Necesita al menos 1 trade para poder abrirse.",
				"", "&eClick: &fcambiar"));

		inventory.setItem(29, Gui.item(base.getItemData().getType(), "&eMoneda base: &f" + base.getDisplayName(),
				"&7" + (custom ? "Moneda de ESTE mercader." : "Moneda global del servidor."), "",
				"&eToma un item con el mouse", "&ey haz click aqui para usarlo como moneda."));
		if (high != null) {
			inventory.setItem(31, Gui.item(high.getItemData().getType(),
					"&eMoneda alta: &f" + high.getDisplayName(), "&7Vale &f" + high.getValue() + " &7monedas base.", "",
					"&eToma un item y haz click: cambiarla", "&eClick derecho sin item: quitarla"));
		} else {
			inventory.setItem(31, Gui.item(Material.BARRIER, "&eMoneda alta: &7ninguna", "",
					"&eToma un item y haz click aqui", "&epara ponerla."));
		}
		inventory.setItem(33, Gui.item(Material.GOLD_NUGGET,
				"&eValor de la moneda alta: &f" + (high == null ? profiles.getHighValue(id) : high.getValue()),
				"&7Cuantas monedas base vale una alta.", "",
				"&eClick izq: &f+1   &eClick der: &f-1", "&eShift: &f+5 / -5"));

		inventory.setItem(38, Gui.item(Material.RED_DYE, "&cUsar la moneda global",
				"&7Quita la moneda propia de este mercader."));
		inventory.setItem(40, Gui.item(Material.COMPASS, "&aActualizar precios ahora"));
		if (banker) {
			inventory.setItem(42, Gui.item(Material.GOLD_INGOT, "&6Abrir este banco", "&7Mira las cuentas como cliente."));
		}
		inventory.setItem(49, Gui.button(economy, Gui.BTN_CLOSE, Material.BARRIER, "&cCerrar"));
	}

	private void say(String text) {
		player.sendMessage(MedievalEconomy.color("&6&l[Mercader] &r" + text));
	}

	private void afterCurrencyChange() {
		if (economy.pricing().isMarked(shop.getUniqueId())) {
			say("&7Si tus ofertas usan otra moneda, editalas con la nueva y vuelve a activar los precios dinamicos.");
			economy.pricing().refreshAll();
		}
		Sfx.play(player, Sfx.Type.COIN);
		refresh();
	}

	@Override
	protected void onClick(int slot, ClickType click) {
		UUID id = shop.getUniqueId();
		ShopProfiles profiles = economy.profiles();
		ShopPricing pricing = economy.pricing();
		switch (slot) {
		case 20 -> {
			if (pricing.isMarked(id)) {
				pricing.unmark(shop);
				say("&ePrecios dinamicos desactivados: vuelven los precios base.");
			} else {
				int count = pricing.mark(shop);
				if (count < 0) {
					Sfx.play(player, Sfx.Type.ERROR);
					say("&cEse tipo de tienda no admite precios dinamicos (usa admin, venta o compra).");
					return;
				}
				say("&aPrecios dinamicos activados. Ofertas detectadas: &e" + count);
			}
			Sfx.play(player, Sfx.Type.CLICK);
			refresh();
		}
		case 24 -> {
			boolean now = !profiles.isBanker(id);
			profiles.setBanker(id, now);
			say(now ? "&aAhora es banquero: al hacerle click se abre su banco." : "&eYa no es banquero.");
			Sfx.play(player, Sfx.Type.CLICK);
			refresh();
		}
		case 29 -> {
			if (cursor == null || !cursor.getType().isItem()) {
				say("&7Toma un item con el mouse (de tu inventario) y haz click en este slot.");
				return;
			}
			profiles.setBase(id, cursor.getType());
			say("&aMoneda base: &f" + MedievalEconomy.pretty(cursor.getType().name()));
			afterCurrencyChange();
		}
		case 31 -> {
			if (cursor != null && cursor.getType().isItem()) {
				profiles.setHigh(id, cursor.getType(), profiles.getHighValue(id));
				say("&aMoneda alta: &f" + MedievalEconomy.pretty(cursor.getType().name()));
				afterCurrencyChange();
			} else if (click == ClickType.RIGHT) {
				profiles.setHigh(id, null, 0);
				say("&eMoneda alta quitada.");
				afterCurrencyChange();
			} else {
				say("&7Toma un item con el mouse y haz click aqui para ponerlo como moneda alta.");
			}
		}
		case 33 -> {
			int delta = switch (click) {
			case LEFT -> 1;
			case RIGHT -> -1;
			case SHIFT_LEFT -> 5;
			case SHIFT_RIGHT -> -5;
			default -> 0;
			};
			if (delta != 0) {
				Currency high = Currencies.of(id).getHighOrNull();
				int current = high == null ? profiles.getHighValue(id) : high.getValue();
				profiles.setHighValue(id, current + delta);
				Sfx.play(player, Sfx.Type.CLICK);
				refresh();
			}
		}
		case 38 -> {
			profiles.clearCurrency(id);
			say("&eAhora usa la moneda global.");
			afterCurrencyChange();
		}
		case 40 -> {
			pricing.refreshAll();
			say("&aPrecios actualizados.");
			Sfx.play(player, Sfx.Type.CLICK);
		}
		case 42 -> {
			if (profiles.isBanker(id)) {
				Sfx.play(player, Sfx.Type.PAGE);
				new BankMenu(economy, player, BankManager.keyOf(id), shop.getName()).open();
			}
		}
		case 49 -> player.closeInventory();
		default -> {
			// Sin accion.
		}
		}
	}
}
