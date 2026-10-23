package me.medieval.shops;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.nisovin.shopkeepers.api.events.ShopkeeperEditedEvent;
import com.nisovin.shopkeepers.api.shopkeeper.Shopkeeper;
import com.nisovin.shopkeepers.api.events.ShopkeeperOpenUIEvent;
import com.nisovin.shopkeepers.api.events.ShopkeeperTradeCompletedEvent;
import com.nisovin.shopkeepers.api.events.ShopkeeperTradeEvent;
import com.nisovin.shopkeepers.api.ui.DefaultUITypes;
import me.medieval.shops.bank.BankManager;
import me.medieval.shops.currency.Currencies;
import me.medieval.shops.gui.BankMenu;

final class EconomyListener implements Listener {

	private final MedievalEconomy economy;

	EconomyListener(MedievalEconomy economy) {
		this.economy = economy;
	}

	// Un mercader marcado como banquero abre el banco en vez del comercio.
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	void onOpenUI(ShopkeeperOpenUIEvent event) {
		if (!economy.config().getBoolean("banco.habilitado", true)) return;
		if (!DefaultUITypes.TRADING().equals(event.getUIType())) return;
		Shopkeeper shopkeeper = event.getShopkeeper();
		if (!economy.profiles().isBanker(shopkeeper.getUniqueId())) return;
		event.setCancelled(true);
		Player player = event.getPlayer();
		String key = BankManager.keyOf(shopkeeper.getUniqueId());
		String name = shopkeeper.getName();
		Bukkit.getScheduler().runTask(economy.getPlugin(), () -> {
			if (player.isOnline()) new BankMenu(economy, player, key, name).open();
		});
	}

	// Cada compra completada sube/baja la demanda, suena y recalcula precios.
	@EventHandler(priority = EventPriority.MONITOR)
	void onTradeCompleted(ShopkeeperTradeCompletedEvent event) {
		try {
			ShopkeeperTradeEvent trade = event.getCompletedTrade();
			UUID shopId = event.getShopkeeper().getUniqueId();
			// Solo los mercaderes que tu marcas cuentan para la demanda y el mercado.
			if (!economy.pricing().isMarked(shopId)) return;
			economy.market().recordTrade(trade.getTradingRecipe(), Currencies.of(shopId));
			Sfx.play(trade.getPlayer(), Sfx.Type.TRADE_OK);
			economy.pricing().refreshSoon();
		} catch (Exception e) {
			economy.getPlugin().getLogger().warning("Error procesando una compra: " + e);
		}
	}

	// Si un admin edita una tienda dinamica, sus precios pasan a ser el nuevo precio base.
	@EventHandler(priority = EventPriority.MONITOR)
	void onEdited(ShopkeeperEditedEvent event) {
		economy.pricing().onEdited(event.getShopkeeper());
	}

	@EventHandler
	void onJoin(PlayerJoinEvent event) {
		if (!economy.config().getBoolean("gui.resourcepack.enviar-al-entrar", false)) return;
		String url = economy.config().getString("gui.resourcepack.url", "");
		if (url == null || url.isEmpty()) return;
		String sha1 = economy.config().getString("gui.resourcepack.sha1", "");
		Player player = event.getPlayer();
		Bukkit.getScheduler().runTaskLater(economy.getPlugin(), () -> {
			if (!player.isOnline()) return;
			if (sha1 != null && sha1.length() == 40) {
				player.setResourcePack(url, hex(sha1));
			} else {
				player.setResourcePack(url);
			}
		}, 40L);
	}

	private static byte[] hex(String s) {
		byte[] out = new byte[s.length() / 2];
		for (int i = 0; i < out.length; i++) {
			out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
		}
		return out;
	}
}
