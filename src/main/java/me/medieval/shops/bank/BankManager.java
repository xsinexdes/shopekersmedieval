package me.medieval.shops.bank;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import me.medieval.shops.currency.Currencies;
import me.medieval.shops.currency.Currency;
import me.medieval.shops.MedievalEconomy;

/**
 * Cuentas bancarias. El saldo se guarda en "monedas base" (valor de la moneda base = 1).
 */
public final class BankManager {

	private final MedievalEconomy economy;
	private final File file;
	private final Map<UUID, Long> balances = new HashMap<>();
	private final Map<UUID, String> names = new HashMap<>();
	private final Set<UUID> bankers = new HashSet<>();
	private boolean dirty = false;
	private int minuteCounter = 0;

	private long balanceLimit;
	private double interestPercent;
	private long interestMax;
	private double transferTaxPercent;

	public BankManager(MedievalEconomy economy) {
		this.economy = economy;
		this.file = new File(economy.getPlugin().getDataFolder(), "bank-data.yml");
		reloadConfig();
	}

	public void reloadConfig() {
		FileConfiguration c = economy.config();
		balanceLimit = c.getLong("banco.limite-saldo", 0L);
		interestPercent = c.getDouble("banco.interes-por-hora-porcentaje", 0.0D);
		interestMax = c.getLong("banco.interes-maximo-por-hora", 1000L);
		transferTaxPercent = c.getDouble("banco.impuesto-transferencia-porcentaje", 0.0D);
	}

	public boolean isEnabled() {
		return economy.config().getBoolean("banco.habilitado", true);
	}

	// ---------------------------------------------------------------- Persistencia

	public void load() {
		balances.clear();
		names.clear();
		bankers.clear();
		if (!file.exists()) return;
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		ConfigurationSection accounts = yaml.getConfigurationSection("cuentas");
		if (accounts != null) {
			for (String key : accounts.getKeys(false)) {
				try {
					UUID id = UUID.fromString(key);
					balances.put(id, accounts.getLong(key + ".saldo", 0L));
					String name = accounts.getString(key + ".nombre");
					if (name != null) names.put(id, name);
				} catch (IllegalArgumentException ignored) {
					// Clave invalida.
				}
			}
		}
		for (String s : yaml.getStringList("banqueros")) {
			try {
				bankers.add(UUID.fromString(s));
			} catch (IllegalArgumentException ignored) {
				// Clave invalida.
			}
		}
	}

	public void save() {
		YamlConfiguration yaml = new YamlConfiguration();
		for (Map.Entry<UUID, Long> entry : balances.entrySet()) {
			String base = "cuentas." + entry.getKey();
			yaml.set(base + ".saldo", entry.getValue());
			String name = names.get(entry.getKey());
			if (name != null) yaml.set(base + ".nombre", name);
		}
		List<String> list = new ArrayList<>();
		for (UUID id : bankers) {
			list.add(id.toString());
		}
		yaml.set("banqueros", list);
		try {
			economy.getPlugin().getDataFolder().mkdirs();
			yaml.save(file);
			dirty = false;
		} catch (IOException e) {
			economy.getPlugin().getLogger()
					.warning("No se pudo guardar bank-data.yml: " + e.getMessage());
		}
	}

	public void tickMinute() {
		minuteCounter++;
		if (minuteCounter % 60 == 0 && interestPercent > 0.0D) {
			for (Map.Entry<UUID, Long> entry : balances.entrySet()) {
				long interest = (long) Math.floor(entry.getValue() * interestPercent / 100.0D);
				interest = Math.min(interest, interestMax);
				if (interest > 0) {
					entry.setValue(clamp(entry.getValue() + interest));
					dirty = true;
				}
			}
		}
		if (dirty && minuteCounter % 2 == 0) save();
	}

	// ---------------------------------------------------------------- Cuentas

	private long clamp(long value) {
		if (balanceLimit > 0 && value > balanceLimit) return balanceLimit;
		return Math.max(0L, value);
	}

	public long getBalance(UUID id) {
		return balances.getOrDefault(id, 0L);
	}

	public long getLimit() {
		return balanceLimit;
	}

	public double getTransferTaxPercent() {
		return transferTaxPercent;
	}

	public double getInterestPercent() {
		return interestPercent;
	}

	public void remember(UUID id, String name) {
		if (name != null && !name.equals(names.put(id, name))) dirty = true;
	}

	public UUID findByName(String name) {
		for (Map.Entry<UUID, String> entry : names.entrySet()) {
			if (entry.getValue().equalsIgnoreCase(name)) return entry.getKey();
		}
		Player online = Bukkit.getPlayerExact(name);
		if (online != null) return online.getUniqueId();
		return null;
	}

	public void setBalance(UUID id, long value) {
		balances.put(id, clamp(value));
		dirty = true;
	}

	/** @return false si el saldo resultante seria negativo o supera el limite. */
	public boolean add(UUID id, long delta) {
		long current = getBalance(id);
		long result = current + delta;
		if (result < 0) return false;
		if (balanceLimit > 0 && result > balanceLimit) return false;
		balances.put(id, result);
		dirty = true;
		return true;
	}

	public Map<UUID, Long> all() {
		return balances;
	}

	public String nameOf(UUID id) {
		String name = names.get(id);
		return name != null ? name : id.toString().substring(0, 8);
	}

	// ---------------------------------------------------------------- Banqueros

	public boolean isBanker(UUID shopkeeperId) {
		return bankers.contains(shopkeeperId);
	}

	/** @return true si ahora es banquero, false si se quito. */
	public boolean toggleBanker(UUID shopkeeperId) {
		dirty = true;
		if (bankers.remove(shopkeeperId)) return false;
		bankers.add(shopkeeperId);
		return true;
	}

	// ---------------------------------------------------------------- Inventario

	public static int countCurrency(PlayerInventory inventory, Currency currency) {
		int count = 0;
		for (ItemStack item : inventory.getStorageContents()) {
			if (item != null && currency.getItemData().matches(item)) {
				count += item.getAmount();
			}
		}
		return count;
	}

	/** Quita hasta {@code amount} monedas. @return cuantas se quitaron. */
	public static int removeCurrency(PlayerInventory inventory, Currency currency, int amount) {
		int remaining = amount;
		ItemStack[] contents = inventory.getStorageContents();
		for (int i = 0; i < contents.length && remaining > 0; i++) {
			ItemStack item = contents[i];
			if (item == null || !currency.getItemData().matches(item)) continue;
			int take = Math.min(item.getAmount(), remaining);
			remaining -= take;
			if (take >= item.getAmount()) {
				contents[i] = null;
			} else {
				item.setAmount(item.getAmount() - take);
			}
		}
		inventory.setStorageContents(contents);
		return amount - remaining;
	}

	/** Entrega monedas. @return cuantas NO cupieron en el inventario. */
	public static int giveCurrency(PlayerInventory inventory, Currency currency, long amount) {
		long remaining = amount;
		int notFit = 0;
		int stackSize = Math.max(1, currency.getMaxStackSize());
		while (remaining > 0) {
			int give = (int) Math.min(stackSize, remaining);
			ItemStack stack = currency.getItemData().createItemStack(give);
			Map<Integer, ItemStack> left = inventory.addItem(stack);
			for (ItemStack leftover : left.values()) {
				notFit += leftover.getAmount();
			}
			remaining -= give;
		}
		return notFit;
	}

	/** Deposita monedas de un tipo. @return cuantas monedas se depositaron realmente. */
	public int deposit(Player player, Currency currency, int requested) {
		if (requested <= 0) return 0;
		int available = countCurrency(player.getInventory(), currency);
		int amount = Math.min(requested, available);
		if (amount <= 0) return 0;
		long value = (long) amount * currency.getValue();
		long current = getBalance(player.getUniqueId());
		if (balanceLimit > 0 && current + value > balanceLimit) {
			long room = balanceLimit - current;
			amount = (int) Math.min(amount, room / Math.max(1, currency.getValue()));
			if (amount <= 0) return 0;
			value = (long) amount * currency.getValue();
		}
		int removed = removeCurrency(player.getInventory(), currency, amount);
		if (removed <= 0) return 0;
		add(player.getUniqueId(), (long) removed * currency.getValue());
		remember(player.getUniqueId(), player.getName());
		return removed;
	}

	/** Retira monedas de un tipo. @return cuantas monedas se entregaron. */
	public int withdraw(Player player, Currency currency, int requested) {
		if (requested <= 0) return 0;
		long balance = getBalance(player.getUniqueId());
		long maxByBalance = balance / Math.max(1, currency.getValue());
		int amount = (int) Math.min(requested, maxByBalance);
		if (amount <= 0) return 0;
		add(player.getUniqueId(), -(long) amount * currency.getValue());
		int notFit = giveCurrency(player.getInventory(), currency, amount);
		if (notFit > 0) {
			// Lo que no cabe vuelve a la cuenta.
			add(player.getUniqueId(), (long) notFit * currency.getValue());
		}
		return amount - notFit;
	}

	// ---------------------------------------------------------------- Formato

	public static String format(long units) {
		Currency base = Currencies.getBase();
		Currency high = Currencies.getHighOrNull();
		StringBuilder sb = new StringBuilder();
		sb.append(units).append(" monedas");
		if (high != null && units > 0) {
			long hv = Math.max(1, high.getValue());
			long h = units / hv;
			long b = units % hv;
			sb.append(" (").append(h).append(' ').append(high.getDisplayName()).append(" + ")
					.append(b).append(' ').append(base.getDisplayName()).append(')');
		}
		return sb.toString();
	}
}
