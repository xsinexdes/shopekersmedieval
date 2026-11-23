package me.medieval.shops.city;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import me.medieval.shops.MedievalEconomy;
import me.medieval.shops.Msg;

/** Pide un texto al jugador por el chat (para nombres): lo siguiente que escriba no se muestra a los demas. */
public final class ChatPrompt implements Listener {

	private static final class Prompt {
		final Consumer<String> callback;
		final long expires;

		Prompt(Consumer<String> callback, long expires) {
			this.callback = callback;
			this.expires = expires;
		}
	}

	private final MedievalEconomy economy;
	private final Map<UUID, Prompt> prompts = new HashMap<>();

	public ChatPrompt(MedievalEconomy economy) {
		this.economy = economy;
	}

	public void ask(Player player, String messageKey, Consumer<String> callback) {
		prompts.put(player.getUniqueId(), new Prompt(callback, System.currentTimeMillis() + 60_000L));
		player.closeInventory();
		Msg.send(player, messageKey);
		Msg.send(player, "escribe-cancelar");
	}

	public void clear() {
		prompts.clear();
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	void onChat(AsyncPlayerChatEvent event) {
		Prompt prompt = prompts.get(event.getPlayer().getUniqueId());
		if (prompt == null) return;
		event.setCancelled(true);
		prompts.remove(event.getPlayer().getUniqueId());
		String text = event.getMessage().trim();
		Player player = event.getPlayer();
		Bukkit.getScheduler().runTask(economy.getPlugin(), () -> {
			if (System.currentTimeMillis() > prompt.expires) {
				Msg.send(player, "escribe-expiro");
			} else if (text.equalsIgnoreCase("cancelar")) {
				Msg.send(player, "escribe-cancelado");
			} else {
				prompt.callback.accept(text);
			}
		});
	}

	@EventHandler
	void onQuit(PlayerQuitEvent event) {
		prompts.remove(event.getPlayer().getUniqueId());
	}
}
