package me.medieval.shops.market;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;
import me.medieval.shops.MedievalEconomy;
import me.medieval.shops.ShopProfiles;
import me.medieval.shops.Targeting;
import me.medieval.shops.gui.CategoryMenu;
import me.medieval.shops.gui.GraphMenu;
import me.medieval.shops.gui.MarketMenu;
import me.medieval.shops.gui.ShopConfigMenu;

public final class MarketCommand implements TabExecutor {

	private static final String P = "&6&l[Mercado] &r";

	private final MedievalEconomy economy;
	private final boolean admin;

	public MarketCommand(MedievalEconomy economy, boolean admin) {
		this.economy = economy;
		this.admin = admin;
	}

	private static void msg(CommandSender to, String text) {
		to.sendMessage(MedievalEconomy.color(P + text));
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		MarketManager market = economy.market();
		return admin ? adminCommand(sender, market, args) : userCommand(sender, market, args);
	}

	/** Item de la mano o nombre dado. */
	private static String resolveKey(CommandSender sender, String[] args, int index) {
		if (args.length > index) {
			Material material = Material.matchMaterial(args[index]);
			return material == null ? null : material.name();
		}
		if (sender instanceof Player player) {
			ItemStack hand = player.getInventory().getItemInMainHand();
			if (hand.getType() != Material.AIR) return MarketManager.keyOf(hand);
		}
		return null;
	}

	// ---------------------------------------------------------------- Jugador

	private boolean userCommand(CommandSender sender, MarketManager market, String[] args) {
		if (!sender.hasPermission("shopkeeper.market")) {
			msg(sender, "&cNo tienes permiso.");
			return true;
		}
		if (!(sender instanceof Player player)) {
			msg(sender, "&cSolo jugadores.");
			return true;
		}
		if (args.length == 0) {
			if (economy.config().getBoolean("mercado.categorias", true)) {
				new CategoryMenu(economy, player).open();
			} else {
				new MarketMenu(economy, player, 0).open();
			}
			return true;
		}
		String sub = args[0].toLowerCase(Locale.ROOT);
		switch (sub) {
		case "precio" -> {
			String key = resolveKey(player, args, 1);
			if (key == null) {
				msg(player, "&7Uso: /mercado precio [ITEM] (o sostiene un item)");
				return true;
			}
			double m = market.getMultiplier(key);
			msg(player, "&f" + MedievalEconomy.pretty(key) + "&7: " + MarketMenu.color(m)
					+ MarketMenu.mult(m) + " &7(" + MarketMenu.percent(m) + ") demanda &f"
					+ String.format(Locale.ROOT, "%.2f", market.getDemand(key)));
		}
		case "grafica" -> {
			String key = resolveKey(player, args, 1);
			if (key == null) {
				msg(player, "&7Uso: /mercado grafica [ITEM] (o sostiene un item)");
				return true;
			}
			new GraphMenu(economy, player, key, 1).open();
		}
		case "eventos" -> {
			List<MarketManager.MarketEvent> events = market.activeEvents();
			if (events.isEmpty()) msg(player, "&7No hay eventos activos.");
			for (MarketManager.MarketEvent e : events) {
				msg(player, e.name + " &7x" + String.format(Locale.ROOT, "%.2f", e.mult) + " - quedan "
						+ e.minutesLeft() + " min");
			}
		}
		default -> msg(player, "&7Uso: /mercado [precio|grafica|eventos]");
		}
		return true;
	}

	// ---------------------------------------------------------------- Admin

	private boolean adminCommand(CommandSender sender, MarketManager market, String[] args) {
		if (!sender.hasPermission("shopkeeper.market.admin")) {
			msg(sender, "&cNo tienes permiso.");
			return true;
		}
		if (args.length == 0) {
			msg(sender, "&7Uso: /mercadoadmin <evento|demanda|info|reiniciar|recargar|guardar> ...");
			return true;
		}
		String sub = args[0].toLowerCase(Locale.ROOT);
		switch (sub) {
		case "evento" -> event(sender, market, args);
		case "tienda" -> shop(sender, args);
		case "demanda" -> {
			if (args.length < 3) {
				msg(sender, "&7Uso: /mercadoadmin demanda <ITEM> <valor>  (0 = normal)");
				return true;
			}
			String key = resolveKey(sender, args, 1);
			if (key == null) {
				msg(sender, "&cItem invalido.");
				return true;
			}
			try {
				market.setDemand(key, Double.parseDouble(args[2]));
			} catch (NumberFormatException e) {
				msg(sender, "&cValor invalido.");
				return true;
			}
			msg(sender, "&aDemanda de &f" + key + "&a: &e" + String.format(Locale.ROOT, "%.3f", market.getDemand(key))
					+ " &7(precio " + MarketMenu.mult(market.getMultiplier(key)) + ")");
		}
		case "info" -> {
			String key = resolveKey(sender, args, 1);
			if (key == null) {
				msg(sender, "&cItem invalido.");
				return true;
			}
			msg(sender, "&f" + key + " &7demanda &e" + String.format(Locale.ROOT, "%.3f", market.getDemand(key))
					+ " &7evento &e" + MarketMenu.mult(market.getEventMultiplier(key))
					+ " &7final &e" + MarketMenu.mult(market.getMultiplier(key))
					+ " &7muestras &e" + market.history(key).size());
		}
		case "reiniciar" -> {
			if (args.length < 2 || args[1].equalsIgnoreCase("todo")) {
				market.resetAll();
				msg(sender, "&aMercado reiniciado (demanda e historial).");
			} else {
				String key = resolveKey(sender, args, 1);
				if (key == null) {
					msg(sender, "&cItem invalido.");
					return true;
				}
				market.resetItem(key);
				msg(sender, "&aReiniciado: &f" + key);
			}
		}
		case "recargar" -> {
			economy.reload();
			msg(sender, "&aConfiguracion recargada.");
		}
		case "guardar" -> {
			market.save();
			economy.bank().save();
			economy.routes().save();
			msg(sender, "&aDatos guardados.");
		}
		default -> msg(sender, "&7Uso: /mercadoadmin <evento|demanda|info|reiniciar|recargar|guardar> ...");
		}
		return true;
	}

	/** /mercadoadmin tienda [config|marcar|desmarcar|banquero|moneda|moneda-alta|lista|refrescar] (mirando al mercader) */
	private void shop(CommandSender sender, String[] args) {
		ShopPricing pricing = economy.pricing();
		ShopProfiles profiles = economy.profiles();
		String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "config";
		if (sub.equals("lista")) {
			if (pricing.markedIds().isEmpty()) msg(sender, "&7No hay tiendas con precios dinamicos.");
			for (UUID id : pricing.markedIds()) {
				Shopkeeper s = com.nisovin.shopkeepers.api.ShopkeepersAPI.getShopkeeperRegistry()
						.getShopkeeperByUniqueId(id);
				msg(sender, s == null ? "&c(tienda borrada) " + id : "&f#" + s.getId() + " &7" + s.getPositionString());
			}
			return;
		}
		if (sub.equals("refrescar")) {
			pricing.refreshAll();
			msg(sender, "&aPrecios actualizados.");
			return;
		}
		if (!(sender instanceof Player player)) {
			msg(sender, "&cSolo jugadores.");
			return;
		}
		Shopkeeper target = Targeting.lookingAt(player, 8.0D);
		if (target == null) {
			msg(player, "&cMira a un mercader (a menos de 8 bloques).");
			return;
		}
		UUID id = target.getUniqueId();
		switch (sub) {
		case "config" -> new ShopConfigMenu(economy, player, target).open();
		case "marcar" -> {
			int count = pricing.mark(target);
			if (count < 0) msg(player, "&cEse tipo de tienda no admite precios dinamicos (usa tiendas de admin, de venta o de compra).");
			else msg(player, "&aTienda " + pricing.describe(target) + " ahora sigue el mercado. Ofertas: &e" + count
					+ " &7(sus precios actuales son el precio BASE).");
		}
		case "desmarcar" -> msg(player, pricing.unmark(target) ? "&aTienda " + target.getId()
				+ " vuelve a sus precios base." : "&cEsa tienda no estaba marcada.");
		case "banquero" -> {
			boolean now = !profiles.isBanker(id);
			profiles.setBanker(id, now);
			msg(player, now ? "&aAhora es banquero (abre SU banco al hacerle click)." : "&eYa no es banquero.");
		}
		case "moneda" -> {
			if (args.length < 3) {
				msg(player, "&7Uso: /mercadoadmin tienda moneda <ITEM|mano|global>");
				return;
			}
			if (args[2].equalsIgnoreCase("global")) {
				profiles.clearCurrency(id);
				msg(player, "&eUsa la moneda global.");
				return;
			}
			Material m = materialArg(player, args[2]);
			if (m == null) {
				msg(player, "&cItem invalido.");
				return;
			}
			profiles.setBase(id, m);
			msg(player, "&aMoneda base: &f" + MedievalEconomy.pretty(m.name()));
			pricing.refreshAll();
		}
		case "moneda-alta" -> {
			if (args.length < 3) {
				msg(player, "&7Uso: /mercadoadmin tienda moneda-alta <ITEM|mano|ninguna> [valor]");
				return;
			}
			if (args[2].equalsIgnoreCase("ninguna")) {
				profiles.setHigh(id, null, 0);
				msg(player, "&eMoneda alta quitada.");
				return;
			}
			Material m = materialArg(player, args[2]);
			if (m == null) {
				msg(player, "&cItem invalido.");
				return;
			}
			int value = profiles.getHighValue(id);
			if (args.length >= 4) {
				try {
					value = Integer.parseInt(args[3]);
				} catch (NumberFormatException e) {
					msg(player, "&cValor invalido.");
					return;
				}
			}
			profiles.setHigh(id, m, value);
			msg(player, "&aMoneda alta: &f" + MedievalEconomy.pretty(m.name()) + " &7(x" + profiles.getHighValue(id) + ")");
			pricing.refreshAll();
		}
		default -> msg(player, "&7Uso: /mercadoadmin tienda <config|marcar|desmarcar|banquero|moneda|moneda-alta|lista|refrescar>");
		}
	}

	private static Material materialArg(Player player, String arg) {
		Material m;
		if (arg.equalsIgnoreCase("mano")) m = player.getInventory().getItemInMainHand().getType();
		else m = Material.matchMaterial(arg);
		return (m == null || m == Material.AIR || !m.isItem()) ? null : m;
	}

	private void event(CommandSender sender, MarketManager market, String[] args) {
		if (args.length < 2) {
			msg(sender, "&7Uso: /mercadoadmin evento <iniciar|parar|listar|plantillas|crear> ...");
			return;
		}
		switch (args[1].toLowerCase(Locale.ROOT)) {
		case "iniciar" -> {
			if (args.length < 3) {
				msg(sender, "&7Uso: /mercadoadmin evento iniciar <plantilla> [minutos]");
				return;
			}
			int minutes = 0;
			if (args.length >= 4) {
				try {
					minutes = Integer.parseInt(args[3]);
				} catch (NumberFormatException e) {
					msg(sender, "&cMinutos invalidos.");
					return;
				}
			}
			if (market.startTemplate(args[2], minutes) == null) {
				msg(sender, "&cPlantilla desconocida. Usa /mercadoadmin evento plantillas");
			}
		}
		case "parar" -> {
			String which = args.length >= 3 ? args[2] : "todos";
			msg(sender, market.stopEvent(which) ? "&aEvento(s) detenido(s)." : "&cNo se encontro ese evento.");
		}
		case "listar" -> {
			List<MarketManager.MarketEvent> events = market.activeEvents();
			if (events.isEmpty()) msg(sender, "&7No hay eventos activos.");
			for (MarketManager.MarketEvent e : events) {
				msg(sender, "&f" + e.id + " &7- " + e.name + " &7x" + String.format(Locale.ROOT, "%.2f", e.mult)
						+ " (" + e.minutesLeft() + " min) items: " + e.patterns);
			}
		}
		case "plantillas" -> msg(sender, "&7Plantillas: &f" + String.join(", ", market.templateIds()));
		case "crear" -> {
			// /mercadoadmin evento crear <mult> <minutos> <items,separados,por,coma> <nombre...>
			if (args.length < 6) {
				msg(sender, "&7Uso: /mercadoadmin evento crear <mult> <minutos> <ITEMS,coma|*> <nombre...>");
				return;
			}
			try {
				double mult = Double.parseDouble(args[2]);
				int minutes = Integer.parseInt(args[3]);
				List<String> items = new ArrayList<>(Arrays.asList(args[4].split(",")));
				String name = String.join(" ", Arrays.copyOfRange(args, 5, args.length));
				market.startCustom(name, mult, minutes, items);
			} catch (NumberFormatException e) {
				msg(sender, "&cNumero invalido.");
			}
		}
		default -> msg(sender, "&7Uso: /mercadoadmin evento <iniciar|parar|listar|plantillas|crear> ...");
		}
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
		List<String> options = new ArrayList<>();
		if (!admin) {
			if (args.length == 1) options.addAll(Arrays.asList("precio", "grafica", "eventos"));
		} else if (args.length == 1) {
			options.addAll(Arrays.asList("evento", "tienda", "demanda", "info", "reiniciar", "recargar", "guardar"));
		} else if (args.length == 2 && args[0].equalsIgnoreCase("tienda")) {
			options.addAll(Arrays.asList("config", "marcar", "desmarcar", "banquero", "moneda", "moneda-alta", "lista", "refrescar"));
		} else if (args.length == 2 && args[0].equalsIgnoreCase("evento")) {
			options.addAll(Arrays.asList("iniciar", "parar", "listar", "plantillas", "crear"));
		} else if (args.length == 3 && args[0].equalsIgnoreCase("evento")
				&& args[1].equalsIgnoreCase("iniciar")) {
			options.addAll(economy.market().templateIds());
		} else if (args.length == 2 && (args[0].equalsIgnoreCase("demanda")
				|| args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("reiniciar"))) {
			options.addAll(economy.market().keys());
		}
		String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
		options.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
		return options;
	}
}
