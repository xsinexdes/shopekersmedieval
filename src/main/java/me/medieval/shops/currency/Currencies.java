package me.medieval.shops.currency;

import java.io.File;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Monedas del servidor. Por defecto se leen de la config de Shopkeepers (currency-item,
 * high-currency-item, high-currency-value), asi coinciden con las de tus tiendas.
 */
public final class Currencies {

	private static Currency base = new Currency(Material.EMERALD, 1);
	private static Currency high = new Currency(Material.EMERALD_BLOCK, 9);

	public static Currency getBase() {
		return base;
	}

	/** @return la moneda alta o null si esta desactivada */
	public static Currency getHighOrNull() {
		return high;
	}

	public static Currency match(ItemStack item) {
		if (item == null) return null;
		if (base.getItemData().matches(item)) return base;
		if (high != null && high.getItemData().matches(item)) return high;
		return null;
	}

	private static Material material(Object value) {
		if (value == null) return null;
		String name = null;
		if (value instanceof String s) {
			name = s;
		} else if (value instanceof Map<?, ?> map) {
			Object type = map.get("type");
			if (type != null) name = String.valueOf(type);
		} else if (value instanceof ConfigurationSection section) {
			name = section.getString("type");
		}
		if (name == null) return null;
		name = name.trim();
		if (name.equalsIgnoreCase("AIR") || name.equalsIgnoreCase("minecraft:air")) return Material.AIR;
		return Material.matchMaterial(name);
	}

	public static void load(JavaPlugin plugin, FileConfiguration config) {
		Material baseType = Material.matchMaterial(config.getString("moneda.base", "EMERALD"));
		Material highType = Material.matchMaterial(config.getString("moneda.alta", "EMERALD_BLOCK"));
		int highValue = config.getInt("moneda.valor-alta", 9);

		if (config.getBoolean("moneda.leer-de-shopkeepers", true)) {
			File file = new File(plugin.getDataFolder().getParentFile(), "Shopkeepers/config.yml");
			if (file.exists()) {
				YamlConfiguration sk = YamlConfiguration.loadConfiguration(file);
				Material b = material(sk.get("currency-item"));
				Material h = material(sk.get("high-currency-item"));
				if (b != null && b != Material.AIR) baseType = b;
				if (sk.contains("high-currency-item") && h != null) highType = h;
				if (sk.contains("high-currency-value")) highValue = sk.getInt("high-currency-value", 9);
				plugin.getLogger().info("Monedas leidas de la config de Shopkeepers: " + baseType
						+ (highType == Material.AIR ? "" : " / " + highType + " (x" + highValue + ")"));
			} else {
				plugin.getLogger().info("No se encontro plugins/Shopkeepers/config.yml, usando monedas del addon.");
			}
		}
		if (baseType == null) baseType = Material.EMERALD;
		base = new Currency(baseType, 1);
		high = (highType == null || highType == Material.AIR) ? null : new Currency(highType, highValue);
	}

	private Currencies() {
	}
}
