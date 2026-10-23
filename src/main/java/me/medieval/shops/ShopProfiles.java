package me.medieval.shops;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import me.medieval.shops.currency.CurrencySet;
import me.medieval.shops.currency.Currencies;

/**
 * Ajustes propios de cada mercader: si es banquero y que moneda usa (para su banco y para sus
 * precios dinamicos). Los precios dinamicos se guardan aparte (ShopPricing).
 */
public final class ShopProfiles {

	private static final class Profile {
		boolean banker = false;
		boolean custom = false; // true = usa moneda propia
		Material base = null;
		Material high = null;   // null = sin moneda alta
		int value = 9;
		CurrencySet cached = null;
	}

	private final MedievalEconomy economy;
	private final File file;
	private final Map<UUID, Profile> profiles = new HashMap<>();

	public ShopProfiles(MedievalEconomy economy) {
		this.economy = economy;
		this.file = new File(economy.getPlugin().getDataFolder(), "shop-profiles.yml");
	}

	public void load() {
		profiles.clear();
		if (!file.exists()) return;
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		ConfigurationSection section = yaml.getConfigurationSection("mercaderes");
		if (section == null) return;
		for (String key : section.getKeys(false)) {
			try {
				UUID id = UUID.fromString(key);
				ConfigurationSection s = section.getConfigurationSection(key);
				if (s == null) continue;
				Profile p = new Profile();
				p.banker = s.getBoolean("banquero", false);
				p.custom = s.getBoolean("moneda-propia", false);
				p.base = Material.matchMaterial(s.getString("base", ""));
				String high = s.getString("alta", "");
				p.high = high.isEmpty() ? null : Material.matchMaterial(high);
				p.value = Math.max(2, s.getInt("valor", 9));
				profiles.put(id, p);
			} catch (IllegalArgumentException ignored) {
				// Clave invalida.
			}
		}
	}

	public void save() {
		YamlConfiguration yaml = new YamlConfiguration();
		for (Map.Entry<UUID, Profile> entry : profiles.entrySet()) {
			Profile p = entry.getValue();
			if (!p.banker && !p.custom) continue;
			String base = "mercaderes." + entry.getKey();
			yaml.set(base + ".banquero", p.banker);
			yaml.set(base + ".moneda-propia", p.custom);
			if (p.base != null) yaml.set(base + ".base", p.base.name());
			yaml.set(base + ".alta", p.high == null ? "" : p.high.name());
			yaml.set(base + ".valor", p.value);
		}
		try {
			economy.getPlugin().getDataFolder().mkdirs();
			yaml.save(file);
		} catch (IOException e) {
			economy.getPlugin().getLogger().warning("No se pudo guardar shop-profiles.yml: " + e.getMessage());
		}
	}

	private Profile profile(UUID id) {
		return profiles.computeIfAbsent(id, k -> new Profile());
	}

	// ---------------------------------------------------------------- Banquero

	public boolean isBanker(UUID id) {
		Profile p = profiles.get(id);
		return p != null && p.banker;
	}

	public void setBanker(UUID id, boolean banker) {
		profile(id).banker = banker;
		save();
	}

	// ---------------------------------------------------------------- Moneda

	public boolean hasCustomCurrency(UUID id) {
		Profile p = profiles.get(id);
		return p != null && p.custom;
	}

	public int getHighValue(UUID id) {
		Profile p = profiles.get(id);
		return p == null ? 9 : p.value;
	}

	public CurrencySet currencies(UUID id, CurrencySet global) {
		Profile p = profiles.get(id);
		if (p == null || !p.custom) return global;
		if (p.cached == null) {
			Material base = p.base != null ? p.base : global.getBase().getItemData().getType();
			p.cached = Currencies.create(base, p.high, p.value);
		}
		return p.cached;
	}

	public void setBase(UUID id, Material material) {
		Profile p = profile(id);
		if (!p.custom) {
			// Al personalizar, se parte de las monedas globales.
			p.high = Currencies.global().getHighOrNull() == null ? null
					: Currencies.global().getHighOrNull().getItemData().getType();
			p.value = Currencies.global().getHighOrNull() == null ? 9 : Currencies.global().getHighOrNull().getValue();
		}
		p.custom = true;
		p.base = material;
		p.cached = null;
		save();
	}

	/** high = null para quitar la moneda alta. */
	public void setHigh(UUID id, Material material, int value) {
		Profile p = profile(id);
		if (!p.custom) {
			p.base = Currencies.global().getBase().getItemData().getType();
		}
		p.custom = true;
		p.high = material;
		if (value >= 2) p.value = value;
		p.cached = null;
		save();
	}

	public void setHighValue(UUID id, int value) {
		Profile p = profile(id);
		if (!p.custom) setBase(id, Currencies.global().getBase().getItemData().getType());
		p.value = Math.max(2, Math.min(64, value));
		p.cached = null;
		save();
	}

	public void clearCurrency(UUID id) {
		Profile p = profiles.get(id);
		if (p == null) return;
		p.custom = false;
		p.cached = null;
		save();
	}

	public void invalidateCaches() {
		for (Profile p : profiles.values()) p.cached = null;
	}
}
