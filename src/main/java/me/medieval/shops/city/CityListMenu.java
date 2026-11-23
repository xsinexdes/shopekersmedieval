package me.medieval.shops.city;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import me.medieval.shops.MedievalEconomy;
import me.medieval.shops.Msg;
import me.medieval.shops.Sfx;
import me.medieval.shops.gui.Gui;
import me.medieval.shops.gui.Menu;

/** Lista de ciudades. Los admins pueden entrar a cada una y crear una nueva con la seleccion de la varita. */
public final class CityListMenu extends Menu {

	private List<City> shown = new ArrayList<>();

	public CityListMenu(MedievalEconomy economy, Player player) {
		super(economy, player);
		create(54, Gui.title(economy, "&0Ciudades", Gui.BG_MARKET));
	}

	private boolean admin() {
		return player.hasPermission("shopkeeper.city.admin");
	}

	@Override
	protected void render() {
		shown = economy.cities().sorted();
		for (int i = 0; i < Math.min(36, shown.size()); i++) {
			City c = shown.get(i);
			List<String> lore = new ArrayList<>();
			lore.add("&7Mundo: &f" + c.world);
			lore.add("&7Impuesto: &f" + String.format(Locale.ROOT, "%.1f", c.tax) + "%");
			lore.add("&7Tamano: &f" + c.volume() + " bloques");
			if (admin()) {
				lore.add("");
				lore.add("&eClick: &fconfigurar");
			}
			inventory.setItem(i, Gui.item(Material.WHITE_BANNER, "&6&l" + c.name, lore));
		}
		if (shown.isEmpty()) {
			inventory.setItem(22, Gui.item(Material.PAPER, "&7Aun no hay ciudades",
					admin() ? "&8Usa la varita para elegir la zona y crea una." : "&8Un admin debe crearlas."));
		}
		if (admin()) {
			WandManager.Selection sel = economy.wand().get(player.getUniqueId());
			List<String> lore = new ArrayList<>();
			if (sel != null && sel.complete()) {
				lore.add("&7Seleccion lista: &f" + sel.volume() + " bloques");
				lore.add("");
				lore.add("&eClick: &fcrear una ciudad aqui");
			} else {
				lore.add("&7Primero selecciona la zona con la varita:");
				lore.add("&fclick izq = esquina 1, click der = esquina 2");
			}
			inventory.setItem(45, Gui.button(economy, Gui.BTN_BANNER, Material.EMERALD, "&aCrear ciudad con la seleccion", lore));
			inventory.setItem(46, Gui.button(economy, Gui.BTN_SCROLL, Material.BLAZE_ROD, "&6Recibir la varita",
					"&7Click para recibir la varita de seleccion."));
		}
		inventory.setItem(49, Gui.button(economy, Gui.BTN_CLOSE, Material.BARRIER, "&cCerrar"));
	}

	@Override
	protected void onClick(int slot, ClickType click) {
		if (slot >= 0 && slot < 36 && slot < shown.size()) {
			if (admin()) {
				Sfx.play(player, Sfx.Type.PAGE);
				new CityMenu(economy, player, shown.get(slot)).open();
			}
			return;
		}
		if (slot == 45 && admin()) {
			create();
		} else if (slot == 46 && admin()) {
			player.getInventory().addItem(economy.wand().create());
			Sfx.play(player, Sfx.Type.COIN);
			Msg.send(player, "varita-recibida");
		} else if (slot == 49) {
			player.closeInventory();
		}
	}

	private void create() {
		WandManager.Selection sel = economy.wand().get(player.getUniqueId());
		if (sel == null || !sel.complete()) {
			Sfx.play(player, Sfx.Type.ERROR);
			Msg.send(player, "ciudad-sin-seleccion");
			return;
		}
		long max = economy.config().getLong("ciudades.volumen-maximo", 4_000_000L);
		if (sel.volume() > max) {
			Sfx.play(player, Sfx.Type.ERROR);
			Msg.send(player, "ciudad-muy-grande", "max", String.valueOf(max));
			return;
		}
		economy.prompts().ask(player, "ciudad-pide-nombre", name -> {
			if (name.length() < 2 || name.length() > 24) {
				Msg.send(player, "ciudad-nombre-invalido");
				return;
			}
			Location spawn = player.getLocation();
			City probe = new City("x", "x");
			probe.world = sel.a.getWorld().getName();
			probe.minX = Math.min(sel.a.getBlockX(), sel.b.getBlockX());
			probe.minY = Math.min(sel.a.getBlockY(), sel.b.getBlockY());
			probe.minZ = Math.min(sel.a.getBlockZ(), sel.b.getBlockZ());
			probe.maxX = Math.max(sel.a.getBlockX(), sel.b.getBlockX());
			probe.maxY = Math.max(sel.a.getBlockY(), sel.b.getBlockY());
			probe.maxZ = Math.max(sel.a.getBlockZ(), sel.b.getBlockZ());
			if (!probe.contains(spawn)) {
				// Si no esta dentro de la zona, la entrada queda en el centro, arriba.
				spawn = new Location(sel.a.getWorld(), probe.centerX(), probe.maxY + 1, probe.centerZ());
			}
			City city = economy.cities().create(name, sel.a, sel.b, spawn);
			if (city == null) {
				Msg.send(player, "ciudad-existe", "ciudad", name);
				return;
			}
			Sfx.play(player, Sfx.Type.TRADE_OK);
			Msg.send(player, "ciudad-creada", "ciudad", city.name);
			new CityMenu(economy, player, city).open();
		});
	}
}
