package me.medieval.shops.city;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.nisovin.shopkeepers.api.ShopkeepersAPI;
import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;
import com.nisovin.shopkeepers.api.shopkeeper.TradingRecipe;

import me.medieval.shops.MedievalEconomy;
import me.medieval.shops.currency.Currency;
import me.medieval.shops.currency.CurrencySet;
import me.medieval.shops.market.MarketCategory;

/** Guarda y consulta las ciudades (cities-data.yml). */
public final class CityManager {

	private final MedievalEconomy economy;
	private final File file;
	private final Map<String, City> cities = new LinkedHashMap<>();
	private final Map<UUID, String> shopCity = new HashMap<>(); // cache: mercader -> id de ciudad ("" = ninguna)
	private boolean dirty = false;
	private int minuteCounter = 0;

	public CityManager(MedievalEconomy economy) {
		this.economy = economy;
		this.file = new File(economy.getPlugin().getDataFolder(), "cities-data.yml");
	}

	public boolean isEnabled() {
		return economy.config().getBoolean("ciudades.habilitado", true);
	}

	public Collection<City> all() {
		return cities.values();
	}

	public City get(String id) {
		return id == null ? null : cities.get(id);
	}

	public City findByName(String name) {
		for (City c : cities.values()) {
			if (c.id.equalsIgnoreCase(name) || c.name.equalsIgnoreCase(name)) return c;
		}
		return null;
	}

	public static String slug(String name) {
		String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		return s.isEmpty() ? "ciudad" : s;
	}

	public void markDirty() {
		dirty = true;
		shopCity.clear();
	}

	// ---------------------------------------------------------------- Crear / borrar

	/** @return la ciudad creada, o null si el nombre ya existe */
	public City create(String name, Location a, Location b, Location spawn) {
		String id = slug(name);
		if (cities.containsKey(id) || findByName(name) != null) return null;
		City city = new City(id, name);
		setBounds(city, a, b);
		city.spawnX = spawn.getX();
		city.spawnY = spawn.getY();
		city.spawnZ = spawn.getZ();
		city.spawnYaw = spawn.getYaw();
		city.tax = economy.config().getDouble("ciudades.impuesto-defecto", 5.0D);
		city.trophyBase = economy.config().getLong("trofeos.valor-base-defecto", 100L);
		cities.put(id, city);
		markDirty();
		return city;
	}

	private static void setBounds(City city, Location a, Location b) {
		city.world = a.getWorld().getName();
		city.minX = Math.min(a.getBlockX(), b.getBlockX());
		city.minY = Math.min(a.getBlockY(), b.getBlockY());
		city.minZ = Math.min(a.getBlockZ(), b.getBlockZ());
		city.maxX = Math.max(a.getBlockX(), b.getBlockX());
		city.maxY = Math.max(a.getBlockY(), b.getBlockY());
		city.maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
	}

	public void setZone(City city, Location a, Location b) {
		setBounds(city, a, b);
		markDirty();
	}

	public void delete(City city) {
		cities.remove(city.id);
		markDirty();
	}

	// ---------------------------------------------------------------- Consultas

	public City cityAt(Location l) {
		if (l == null) return null;
		for (City c : cities.values()) {
			if (c.contains(l)) return c;
		}
		return null;
	}

	/** Ciudad en la que esta un mercader (por su posicion). null si no esta en ninguna. */
	public City cityOfShop(UUID shopId) {
		if (!isEnabled()) return null;
		String cached = shopCity.get(shopId);
		if (cached != null) return cached.isEmpty() ? null : cities.get(cached);
		Shopkeeper shopkeeper = ShopkeepersAPI.getShopkeeperRegistry().getShopkeeperByUniqueId(shopId);
		City found = null;
		if (shopkeeper != null) found = cityAt(shopkeeper.getLocation());
		shopCity.put(shopId, found == null ? "" : found.id);
		return found;
	}

	/** Distancia en bloques entre dos ciudades (si estan en mundos distintos, la distancia configurada). */
	public double distance(City a, City b) {
		if (!a.world.equals(b.world)) return economy.config().getDouble("trofeos.distancia-otro-mundo", 5000.0D);
		return Math.hypot(a.centerX() - b.centerX(), a.centerZ() - b.centerZ());
	}

	public World worldOf(City c) {
		return Bukkit.getWorld(c.world);
	}

	public List<City> sorted() {
		List<City> list = new ArrayList<>(cities.values());
		list.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
		return list;
	}

	// ---------------------------------------------------------------- Impuestos

	/** Suma al tesoro de la ciudad la parte de impuestos de una compra/venta en un mercader suyo. */
	public void accrueTax(UUID shopId, TradingRecipe recipe, CurrencySet set) {
		City city = cityOfShop(shopId);
		if (city == null || city.tax <= 0.0D) return;
		double t = city.tax / 100.0D;
		Currency cr = set.match(recipe.getResultItem().copy());
		Currency c1 = set.match(recipe.getItem1().copy());
		long taxPart;
		if (cr != null && c1 == null) {
			// El mercader le pago al jugador: el impuesto se descontó del pago.
			long paid = (long) recipe.getResultItem().getAmount() * cr.getValue();
			taxPart = Math.round(paid * t / Math.max(0.01D, 1.0D - t));
		} else if (c1 != null) {
			long paid = (long) recipe.getItem1().getAmount() * c1.getValue();
			if (recipe.getItem2() != null) {
				Currency c2 = set.match(recipe.getItem2().copy());
				if (c2 != null) paid += (long) recipe.getItem2().getAmount() * c2.getValue();
			}
			taxPart = Math.round(paid * t / (1.0D + t));
		} else {
			return;
		}
		if (taxPart > 0) {
			city.treasury += taxPart;
			dirty = true;
		}
	}

	// ---------------------------------------------------------------- Persistencia

	public void load() {
		cities.clear();
		shopCity.clear();
		if (!file.exists()) return;
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		ConfigurationSection section = yaml.getConfigurationSection("ciudades");
		if (section == null) return;
		for (String id : section.getKeys(false)) {
			ConfigurationSection s = section.getConfigurationSection(id);
			if (s == null) continue;
			City c = new City(id, s.getString("nombre", id));
			c.world = s.getString("mundo", "world");
			List<Integer> a = s.getIntegerList("min");
			List<Integer> b = s.getIntegerList("max");
			if (a.size() < 3 || b.size() < 3) continue;
			c.minX = a.get(0);
			c.minY = a.get(1);
			c.minZ = a.get(2);
			c.maxX = b.get(0);
			c.maxY = b.get(1);
			c.maxZ = b.get(2);
			c.spawnX = s.getDouble("entrada.x", c.centerX());
			c.spawnY = s.getDouble("entrada.y", c.maxY);
			c.spawnZ = s.getDouble("entrada.z", c.centerZ());
			c.spawnYaw = (float) s.getDouble("entrada.yaw", 0.0D);
			c.tax = s.getDouble("impuesto", 5.0D);
			c.treasury = s.getLong("tesoro", 0L);
			c.trophyBase = s.getLong("trofeo-valor", 100L);
			c.trophyStock = s.getDouble("trofeo-stock", -1.0D);
			ConfigurationSection mods = s.getConfigurationSection("mods");
			if (mods != null) {
				for (String key : mods.getKeys(false)) {
					try {
						c.mods.put(MarketCategory.valueOf(key), mods.getDouble(key));
					} catch (IllegalArgumentException ignored) {
						// Categoria desconocida.
					}
				}
			}
			ConfigurationSection sat = s.getConfigurationSection("saturacion");
			if (sat != null) {
				for (String key : sat.getKeys(false)) c.saturation.put(key, sat.getDouble(key));
			}
			cities.put(id, c);
		}
	}

	public void save() {
		YamlConfiguration yaml = new YamlConfiguration();
		for (City c : cities.values()) {
			String base = "ciudades." + c.id;
			yaml.set(base + ".nombre", c.name);
			yaml.set(base + ".mundo", c.world);
			yaml.set(base + ".min", List.of(c.minX, c.minY, c.minZ));
			yaml.set(base + ".max", List.of(c.maxX, c.maxY, c.maxZ));
			yaml.set(base + ".entrada.x", c.spawnX);
			yaml.set(base + ".entrada.y", c.spawnY);
			yaml.set(base + ".entrada.z", c.spawnZ);
			yaml.set(base + ".entrada.yaw", (double) c.spawnYaw);
			yaml.set(base + ".impuesto", c.tax);
			yaml.set(base + ".tesoro", c.treasury);
			yaml.set(base + ".trofeo-valor", c.trophyBase);
			yaml.set(base + ".trofeo-stock", c.trophyStock);
			for (Map.Entry<MarketCategory, Double> e : c.mods.entrySet()) {
				if (e.getValue() != 0.0D) yaml.set(base + ".mods." + e.getKey().name(), e.getValue());
			}
			for (Map.Entry<String, Double> e : c.saturation.entrySet()) {
				if (e.getValue() > 0.01D) yaml.set(base + ".saturacion." + e.getKey(), e.getValue());
			}
		}
		try {
			economy.getPlugin().getDataFolder().mkdirs();
			yaml.save(file);
			dirty = false;
		} catch (IOException e) {
			economy.getPlugin().getLogger().warning("No se pudo guardar cities-data.yml: " + e.getMessage());
		}
	}

	/** Cada minuto: guarda si hubo cambios y refresca la cache de mercaderes. */
	public void tickMinute() {
		minuteCounter++;
		if (minuteCounter % 5 == 0) shopCity.clear();
		if (dirty && minuteCounter % 2 == 0) save();
	}
}
