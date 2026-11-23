package me.medieval.shops.city;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import me.medieval.shops.MedievalEconomy;
import me.medieval.shops.Msg;
import me.medieval.shops.Sfx;
import me.medieval.shops.currency.Currencies;
import me.medieval.shops.gui.ButtonMenu;
import me.medieval.shops.gui.Gui;
import me.medieval.shops.gui.Layouts;

/** Panel de una ciudad: impuestos, precios por categoria, trofeo, entrada, zona, tesoreria. */
public final class CityMenu extends ButtonMenu {

	private final City city;

	public CityMenu(MedievalEconomy economy, Player player, City city) {
		super(economy, player, Layouts.CITY, "&0Ciudad: " + city.name);
		this.city = city;
	}

	private static String f(double v) {
		return String.format(Locale.ROOT, "%.1f", v);
	}

	private void changed() {
		economy.cities().markDirty();
		economy.pricing().refreshAll();
		Sfx.play(player, Sfx.Type.CLICK);
		refresh();
	}

	/** Cambio segun el click: izq +paso, der -paso, con shift x5. */
	private static double delta(ClickType click, double step) {
		return switch (click) {
		case LEFT -> step;
		case RIGHT -> -step;
		case SHIFT_LEFT -> step * 5;
		case SHIFT_RIGHT -> -step * 5;
		default -> 0.0D;
		};
	}

	@Override
	protected void buildButtons() {
		inventory.setItem(4, Gui.item(Material.WHITE_BANNER, "&6&l" + city.name,
				"&7Mundo: &f" + city.world, "&7Zona: &f" + city.volume() + " bloques",
				"&7Impuesto: &f" + f(city.tax) + "%", "&7Tesoreria: &e" + city.treasury));

		button("impuestos", Material.PAPER, "&eImpuestos: &f" + f(city.tax) + "%",
				List.of("&7Se suman al comprar y se restan al vender en los", "&7mercaderes de esta ciudad. Van a la tesoreria.", "",
						"&eClick izq: &f+1%   &eClick der: &f-1%", "&eShift: &f+-5%"),
				click -> {
					double d = delta(click, 1.0D);
					if (d == 0.0D) return;
					city.tax = Math.max(0.0D, Math.min(50.0D, city.tax + d));
					changed();
				});

		button("precios", Material.GOLD_INGOT, "&ePrecios por categoria",
				List.of("&7Elige que se vende mas caro o mas barato", "&7en esta ciudad (Forja, Granja, Minas...).", "", "&eClick: &fabrir"),
				click -> {
					Sfx.play(player, Sfx.Type.PAGE);
					new CityPricesMenu(economy, player, city).open();
				});

		List<String> trophyLore = new ArrayList<>();
		trophyLore.add("&7Valor base del Trofeo de " + city.name + ".");
		trophyLore.add("&7Stock ahora: &f" + economy.trophies().stockOf(city) + "/" + economy.trophies().maxStock());
		trophyLore.add("&7Precio de compra: &e" + economy.trophies().buyPrice(city));
		trophyLore.add("");
		trophyLore.add("&eClick izq: &f+10   &eClick der: &f-10");
		trophyLore.add("&eShift: &f+-50");
		button("trofeos", Material.NETHER_STAR, "&eTrofeo: valor base &f" + city.trophyBase, trophyLore, click -> {
			double d = delta(click, 10.0D);
			if (d == 0.0D) return;
			city.trophyBase = Math.max(1L, city.trophyBase + (long) d);
			changed();
		});

		button("spawn", Material.COMPASS, "&eEntrada de la ciudad",
				List.of("&7Aqui llegan los viajeros (boton IR).", "&7Click: &fusar donde estoy parado."), click -> {
					if (!city.contains(player.getLocation())) {
						Sfx.play(player, Sfx.Type.ERROR);
						Msg.send(player, "ciudad-fuera-zona");
						return;
					}
					Location l = player.getLocation();
					city.spawnX = l.getX();
					city.spawnY = l.getY();
					city.spawnZ = l.getZ();
					city.spawnYaw = l.getYaw();
					Msg.send(player, "ciudad-entrada-ok");
					changed();
				});

		button("zona", Material.BLAZE_ROD, "&eRedefinir la zona",
				List.of("&7Usa la zona seleccionada con la varita.", "", "&eShift+click: &fconfirmar"), click -> {
					WandManager.Selection sel = economy.wand().get(player.getUniqueId());
					if (sel == null || !sel.complete()) {
						Sfx.play(player, Sfx.Type.ERROR);
						Msg.send(player, "ciudad-sin-seleccion");
						return;
					}
					if (!click.isShiftClick()) {
						Msg.send(player, "ciudad-confirma-shift");
						return;
					}
					economy.cities().setZone(city, sel.a, sel.b);
					Msg.send(player, "ciudad-zona-ok");
					changed();
				});

		button("tesoreria", Material.GOLD_BLOCK, "&6Tesoreria: &e" + city.treasury,
				List.of("&7Dinero de los impuestos de esta ciudad.", "", "&eClick: &fretirar todo (monedas)"), click -> {
					long amount = city.treasury;
					if (amount <= 0) {
						Sfx.play(player, Sfx.Type.ERROR);
						return;
					}
					city.treasury = 0L;
					Wallet.give(player, Currencies.global(), amount);
					Msg.send(player, "ciudad-tesoro-retirado", "monto", String.valueOf(amount));
					Sfx.play(player, Sfx.Type.WITHDRAW);
					changed();
				});

		button("renombrar", Material.NAME_TAG, "&eRenombrar", List.of("&7Cambia el nombre (escribelo en el chat)."), click ->
				economy.prompts().ask(player, "ciudad-pide-nombre", name -> {
					if (name.length() < 2 || name.length() > 24) {
						Msg.send(player, "ciudad-nombre-invalido");
						return;
					}
					city.name = name;
					economy.cities().markDirty();
					Msg.send(player, "ciudad-renombrada", "ciudad", name);
					new CityMenu(economy, player, city).open();
				}));

		button("ir", Material.ENDER_PEARL, "&bIr a la ciudad", List.of("&7Te teletransporta a la entrada."), click -> {
			World w = economy.cities().worldOf(city);
			if (w == null) return;
			player.closeInventory();
			player.teleport(city.spawn(w));
		});

		button("borrar", Material.TNT, "&cBorrar la ciudad",
				List.of("&7Elimina la ciudad (los mercaderes no se borran).", "", "&eShift+click: &fconfirmar"), click -> {
					if (!click.isShiftClick()) {
						Msg.send(player, "ciudad-confirma-shift");
						return;
					}
					economy.cities().delete(city);
					Msg.send(player, "ciudad-borrada", "ciudad", city.name);
					economy.pricing().refreshAll();
					new CityListMenu(economy, player).open();
				});

		button("volver", Material.ARROW, "&eVolver", List.of(), click -> new CityListMenu(economy, player).open());
		button("cerrar", Material.BARRIER, "&cCerrar", List.of(), click -> player.closeInventory());
	}
}
