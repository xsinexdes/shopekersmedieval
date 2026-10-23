package me.medieval.shops.market;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import com.nisovin.shopkeepers.api.ShopkeepersAPI;
import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;
import com.nisovin.shopkeepers.api.shopkeeper.admin.regular.RegularAdminShopkeeper;
import com.nisovin.shopkeepers.api.shopkeeper.offers.PriceOffer;
import com.nisovin.shopkeepers.api.shopkeeper.offers.TradeOffer;
import com.nisovin.shopkeepers.api.shopkeeper.player.buy.BuyingPlayerShopkeeper;
import com.nisovin.shopkeepers.api.shopkeeper.player.sell.SellingPlayerShopkeeper;

import me.medieval.shops.MedievalEconomy;
import me.medieval.shops.currency.Currencies;
import me.medieval.shops.currency.Currency;
import me.medieval.shops.currency.CurrencySet;

/**
 * Precios dinamicos SIN tocar el codigo de Shopkeepers: las tiendas "marcadas" por un admin
 * guardan sus precios base y este addon reescribe sus ofertas con la API cada minuto (y despues
 * de cada compra) segun la demanda y los eventos del mercado.
 */
public final class ShopPricing {

	private static final class Entry {
		// Tienda de admin
		ItemStack result;
		ItemStack item1;
		ItemStack item2;
		// Tienda de jugador (venta o compra)
		ItemStack item;
		int price;
	}

	private final MedievalEconomy economy;
	private final MarketManager market;
	private final File file;
	private final Map<UUID, List<Entry>> marked = new LinkedHashMap<>();
	private boolean applying = false;
	private boolean refreshQueued = false;

	public ShopPricing(MedievalEconomy economy, MarketManager market) {
		this.economy = economy;
		this.market = market;
		this.file = new File(economy.getPlugin().getDataFolder(), "marked-shops.yml");
	}

	// ---------------------------------------------------------------- Persistencia

	public void load() {
		marked.clear();
		if (!file.exists()) return;
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		ConfigurationSection shops = yaml.getConfigurationSection("tiendas");
		if (shops == null) return;
		for (String key : shops.getKeys(false)) {
			try {
				UUID id = UUID.fromString(key);
				ConfigurationSection section = shops.getConfigurationSection(key);
				if (section == null) continue;
				List<Entry> list = new ArrayList<>();
				for (String index : section.getKeys(false)) {
					ConfigurationSection e = section.getConfigurationSection(index);
					if (e == null) continue;
					Entry entry = new Entry();
					entry.result = e.getItemStack("result");
					entry.item1 = e.getItemStack("item1");
					entry.item2 = e.getItemStack("item2");
					entry.item = e.getItemStack("item");
					entry.price = e.getInt("price", 0);
					list.add(entry);
				}
				marked.put(id, list);
			} catch (IllegalArgumentException ignored) {
				// Clave invalida.
			}
		}
	}

	public void save() {
		YamlConfiguration yaml = new YamlConfiguration();
		for (Map.Entry<UUID, List<Entry>> shop : marked.entrySet()) {
			int i = 0;
			for (Entry e : shop.getValue()) {
				String base = "tiendas." + shop.getKey() + "." + (i++);
				if (e.result != null) yaml.set(base + ".result", e.result);
				if (e.item1 != null) yaml.set(base + ".item1", e.item1);
				if (e.item2 != null) yaml.set(base + ".item2", e.item2);
				if (e.item != null) yaml.set(base + ".item", e.item);
				yaml.set(base + ".price", e.price);
			}
		}
		try {
			economy.getPlugin().getDataFolder().mkdirs();
			yaml.save(file);
		} catch (IOException ex) {
			economy.getPlugin().getLogger().warning("No se pudo guardar marked-shops.yml: " + ex.getMessage());
		}
	}

	public Set<UUID> markedIds() {
		return marked.keySet();
	}

	public boolean isMarked(UUID id) {
		return marked.containsKey(id);
	}

	// ---------------------------------------------------------------- Captura y precios

	private static boolean supported(Shopkeeper shopkeeper) {
		return shopkeeper instanceof RegularAdminShopkeeper || shopkeeper instanceof SellingPlayerShopkeeper
				|| shopkeeper instanceof BuyingPlayerShopkeeper;
	}

	/** Lee las ofertas actuales de la tienda y las convierte en precios base (dividiendo por unbake). */
	private List<Entry> capture(Shopkeeper shopkeeper, boolean unbake) {
		CurrencySet set = Currencies.of(shopkeeper.getUniqueId());
		List<Entry> list = new ArrayList<>();
		if (shopkeeper instanceof RegularAdminShopkeeper admin) {
			for (TradeOffer offer : admin.getOffers()) {
				Entry e = new Entry();
				e.result = offer.getResultItem().copy();
				e.item1 = offer.getItem1().copy();
				e.item2 = offer.getItem2() == null ? null : offer.getItem2().copy();
				if (unbake) {
					String key = keyOf(e, set);
					if (key != null) {
						double mult = market.getMultiplier(key);
						if (mult > 0.0001D) {
							ItemStack[] base = scaleAdmin(e, 1.0D / mult, set);
							e.result = base[0];
							e.item1 = base[1];
							e.item2 = base[2];
						}
					}
				}
				list.add(e);
			}
		} else if (shopkeeper instanceof SellingPlayerShopkeeper selling) {
			for (PriceOffer offer : selling.getOffers()) {
				list.add(playerEntry(offer, unbake));
			}
		} else if (shopkeeper instanceof BuyingPlayerShopkeeper buying) {
			for (PriceOffer offer : buying.getOffers()) {
				list.add(playerEntry(offer, unbake));
			}
		}
		return list;
	}

	private Entry playerEntry(PriceOffer offer, boolean unbake) {
		Entry e = new Entry();
		e.item = offer.getItem().copy();
		e.price = offer.getPrice();
		if (unbake) {
			double mult = market.getMultiplier(MarketManager.keyOf(e.item));
			if (mult > 0.0001D) e.price = Math.max(1, (int) Math.round(e.price / mult));
		}
		return e;
	}

	/** Objeto del mercado al que corresponde esta oferta (o null si no se puede ajustar). */
	private static String keyOf(Entry e, CurrencySet set) {
		if (e.item != null) return MarketManager.keyOf(e.item);
		if (e.result == null || e.item1 == null) return null;
		Currency c1 = set.match(e.item1);
		Currency c2 = e.item2 == null ? null : set.match(e.item2);
		Currency cr = set.match(e.result);
		if (cr == null && c1 != null && (e.item2 == null || c2 != null)) return MarketManager.keyOf(e.result);
		if (cr != null && c1 == null) return MarketManager.keyOf(e.item1);
		return null;
	}

	private ItemStack[] scaleAdmin(Entry e, double mult, CurrencySet set) {
		ItemStack result = e.result.clone();
		ItemStack item1 = e.item1.clone();
		ItemStack item2 = e.item2 == null ? null : e.item2.clone();
		if (Math.abs(mult - 1.0D) < 0.0005D) return new ItemStack[] { result, item1, item2 };

		Currency c1 = set.match(item1);
		Currency c2 = item2 == null ? null : set.match(item2);
		Currency cr = set.match(result);

		if (cr == null && c1 != null && (item2 == null || c2 != null)) {
			// La tienda vende un objeto por monedas.
			long total = (long) item1.getAmount() * c1.getValue();
			if (item2 != null) total += (long) item2.getAmount() * c2.getValue();
			long scaled = Math.max(1L, Math.round(total * mult));
			ItemStack[] cost = splitCurrency(scaled, set);
			return new ItemStack[] { result, cost[0], cost[1] };
		} else if (cr != null && c1 == null) {
			// La tienda paga monedas por un objeto.
			int units = (int) Math.max(1L, Math.round(result.getAmount() * mult));
			result.setAmount(Math.min(units, cr.getMaxStackSize()));
			return new ItemStack[] { result, item1, item2 };
		}
		return new ItemStack[] { result, item1, item2 };
	}

	/** Devuelve {item1, item2 (puede ser null)} que suman el valor dado en monedas base. */
	private static ItemStack[] splitCurrency(long value, CurrencySet set) {
		Currency base = set.getBase();
		Currency high = set.getHighOrNull();
		if (high == null) {
			int amount = (int) Math.min(value, base.getMaxStackSize());
			return new ItemStack[] { base.getItemData().createItemStack(Math.max(1, amount)), null };
		}
		long highValue = Math.max(1, high.getValue());
		long highCount = value / highValue;
		long baseCount = value % highValue;
		if (highCount > high.getMaxStackSize()) {
			highCount = high.getMaxStackSize();
			baseCount = Math.min(baseCount, base.getMaxStackSize());
		}
		if (highCount > 0 && baseCount > 0) {
			return new ItemStack[] { high.getItemData().createItemStack((int) highCount),
					base.getItemData().createItemStack((int) baseCount) };
		} else if (highCount > 0) {
			return new ItemStack[] { high.getItemData().createItemStack((int) highCount), null };
		}
		return new ItemStack[] { base.getItemData().createItemStack((int) Math.max(1, baseCount)), null };
	}

	private double multiplierFor(String key) {
		if (key == null) return 1.0D;
		market.register(key);
		return market.getMultiplier(key);
	}

	// ---------------------------------------------------------------- Aplicar

	private void apply(Shopkeeper shopkeeper, boolean restoreBase) {
		List<Entry> base = marked.get(shopkeeper.getUniqueId());
		if (base == null) return;
		CurrencySet set = Currencies.of(shopkeeper.getUniqueId());
		try {
			if (shopkeeper instanceof RegularAdminShopkeeper admin) {
				List<TradeOffer> out = new ArrayList<>();
				for (Entry e : base) {
					double mult = restoreBase ? 1.0D : multiplierFor(keyOf(e, set));
					ItemStack[] arr = scaleAdmin(e, mult, set);
					out.add(TradeOffer.create(arr[0], arr[1], arr[2]));
				}
				if (!sameAdmin(admin.getOffers(), out)) {
					applying = true;
					try {
						admin.setOffers(out);
					} finally {
						applying = false;
					}
				}
			} else if (shopkeeper instanceof SellingPlayerShopkeeper selling) {
				List<PriceOffer> out = playerOffers(base, restoreBase);
				if (!samePrices(selling.getOffers(), out)) {
					applying = true;
					try {
						selling.setOffers(out);
					} finally {
						applying = false;
					}
				}
			} else if (shopkeeper instanceof BuyingPlayerShopkeeper buying) {
				List<PriceOffer> out = playerOffers(base, restoreBase);
				if (!samePrices(buying.getOffers(), out)) {
					applying = true;
					try {
						buying.setOffers(out);
					} finally {
						applying = false;
					}
				}
			}
		} catch (Exception e) {
			economy.getPlugin().getLogger().log(Level.WARNING,
					"No se pudieron actualizar los precios de la tienda #" + shopkeeper.getId(), e);
		}
	}

	private List<PriceOffer> playerOffers(List<Entry> base, boolean restoreBase) {
		List<PriceOffer> out = new ArrayList<>();
		for (Entry e : base) {
			double mult = restoreBase ? 1.0D : multiplierFor(MarketManager.keyOf(e.item));
			int price = Math.max(1, (int) Math.round(e.price * mult));
			out.add(PriceOffer.create(e.item.clone(), price));
		}
		return out;
	}

	private static boolean sameAdmin(List<? extends TradeOffer> current, List<TradeOffer> out) {
		if (current.size() != out.size()) return false;
		for (int i = 0; i < out.size(); i++) {
			TradeOffer a = current.get(i);
			TradeOffer b = out.get(i);
			if (!a.getResultItem().copy().equals(b.getResultItem().copy())) return false;
			if (!a.getItem1().copy().equals(b.getItem1().copy())) return false;
			ItemStack a2 = a.getItem2() == null ? null : a.getItem2().copy();
			ItemStack b2 = b.getItem2() == null ? null : b.getItem2().copy();
			if (a2 == null ? b2 != null : !a2.equals(b2)) return false;
		}
		return true;
	}

	private static boolean samePrices(List<? extends PriceOffer> current, List<PriceOffer> out) {
		if (current.size() != out.size()) return false;
		for (int i = 0; i < out.size(); i++) {
			if (current.get(i).getPrice() != out.get(i).getPrice()) return false;
			if (!current.get(i).getItem().copy().equals(out.get(i).getItem().copy())) return false;
		}
		return true;
	}

	// ---------------------------------------------------------------- API para comandos y eventos

	/** @return cantidad de ofertas capturadas, o -1 si el tipo de tienda no esta soportado */
	public int mark(Shopkeeper shopkeeper) {
		if (!supported(shopkeeper)) return -1;
		List<Entry> list = capture(shopkeeper, false);
		marked.put(shopkeeper.getUniqueId(), list);
		save();
		apply(shopkeeper, false);
		return list.size();
	}

	public boolean unmark(Shopkeeper shopkeeper) {
		if (!marked.containsKey(shopkeeper.getUniqueId())) return false;
		apply(shopkeeper, true); // Devuelve los precios base originales.
		marked.remove(shopkeeper.getUniqueId());
		save();
		return true;
	}

	/** Un jugador edito la tienda: sus precios pasan a ser el nuevo precio base. */
	public void onEdited(Shopkeeper shopkeeper) {
		if (applying || !marked.containsKey(shopkeeper.getUniqueId())) return;
		Bukkit.getScheduler().runTask(economy.getPlugin(), () -> {
			marked.put(shopkeeper.getUniqueId(), capture(shopkeeper, true));
			save();
			apply(shopkeeper, false);
		});
	}

	public void refreshAll() {
		if (!market.isEnabled()) return;
		for (UUID id : new ArrayList<>(marked.keySet())) {
			Shopkeeper shopkeeper = ShopkeepersAPI.getShopkeeperRegistry().getShopkeeperByUniqueId(id);
			if (shopkeeper != null) apply(shopkeeper, false);
		}
	}

	/** Recalcula los precios en el proximo tick (agrupa varias compras seguidas). */
	public void refreshSoon() {
		if (refreshQueued || marked.isEmpty()) return;
		refreshQueued = true;
		Bukkit.getScheduler().runTask(economy.getPlugin(), () -> {
			refreshQueued = false;
			refreshAll();
		});
	}

	/** Al apagar el servidor no se tocan los precios: quedan los ultimos aplicados. */
	public String describe(Shopkeeper shopkeeper) {
		return "#" + shopkeeper.getId() + (marked.containsKey(shopkeeper.getUniqueId()) ? " (dinamica)" : "");
	}
}
