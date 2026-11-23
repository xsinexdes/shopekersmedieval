package me.medieval.shops.city;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import me.medieval.shops.MedievalEconomy;
import me.medieval.shops.Msg;

/**
 * Varita de seleccion: click izquierdo = esquina 1, click derecho = esquina 2 (sobre bloques).
 * Agachado + click derecho abre el menu de ciudades (ahi se crea la ciudad con la seleccion).
 */
public final class WandManager implements Listener {

	public static final class Selection {
		public Location a;
		public Location b;

		public boolean complete() {
			return a != null && b != null && a.getWorld() != null && a.getWorld().equals(b.getWorld());
		}

		public long volume() {
			if (!complete()) return 0L;
			return (long) (Math.abs(a.getBlockX() - b.getBlockX()) + 1) * (Math.abs(a.getBlockY() - b.getBlockY()) + 1)
					* (Math.abs(a.getBlockZ() - b.getBlockZ()) + 1);
		}
	}

	private final MedievalEconomy economy;
	private final NamespacedKey key;
	private final Map<UUID, Selection> selections = new HashMap<>();

	public WandManager(MedievalEconomy economy) {
		this.economy = economy;
		this.key = new NamespacedKey(economy.getPlugin(), "varita_ciudad");
	}

	public ItemStack create() {
		Material material = Material.matchMaterial(economy.config().getString("ciudades.varita.material", "BLAZE_ROD"));
		ItemStack item = new ItemStack(material == null ? Material.BLAZE_ROD : material);
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			meta.setDisplayName(MedievalEconomy.color(economy.config().getString("ciudades.varita.nombre", "&6&lVarita de Ciudad")));
			meta.setLore(List.of(
					MedievalEconomy.color("&7Click izquierdo: &fesquina 1"),
					MedievalEconomy.color("&7Click derecho: &fesquina 2"),
					MedievalEconomy.color("&7Agachado + click derecho: &fmenu de ciudades")));
			meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
			item.setItemMeta(meta);
		}
		return item;
	}

	public boolean isWand(ItemStack item) {
		if (item == null || !item.hasItemMeta()) return false;
		return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
	}

	public Selection get(UUID player) {
		return selections.get(player);
	}

	public void clear() {
		selections.clear();
	}

	@EventHandler(priority = EventPriority.HIGH)
	void onInteract(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) return;
		Player player = event.getPlayer();
		if (!isWand(player.getInventory().getItemInMainHand())) return;
		if (!player.hasPermission("shopkeeper.city.admin")) return;
		event.setCancelled(true);
		Action action = event.getAction();

		if (player.isSneaking() && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
			new CityListMenu(economy, player).open();
			return;
		}
		if (event.getClickedBlock() == null) return;
		Selection sel = selections.computeIfAbsent(player.getUniqueId(), k -> new Selection());
		Location at = event.getClickedBlock().getLocation();
		String pos = at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ();
		if (action == Action.LEFT_CLICK_BLOCK) {
			sel.a = at;
			Msg.send(player, "varita-esquina", "n", "1", "pos", pos);
		} else if (action == Action.RIGHT_CLICK_BLOCK) {
			sel.b = at;
			Msg.send(player, "varita-esquina", "n", "2", "pos", pos);
		} else {
			return;
		}
		if (sel.complete()) {
			Msg.send(player, "varita-lista", "volumen", String.valueOf(sel.volume()));
		}
	}

	@EventHandler
	void onQuit(PlayerQuitEvent event) {
		selections.remove(event.getPlayer().getUniqueId());
	}

	/** Cada medio segundo: dibuja el contorno de la seleccion a quien tiene la varita en la mano. */
	public void showParticles() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			Selection sel = selections.get(player.getUniqueId());
			if (sel == null || !sel.complete() || !player.getWorld().equals(sel.a.getWorld())) continue;
			if (!isWand(player.getInventory().getItemInMainHand())) continue;
			double x0 = Math.min(sel.a.getBlockX(), sel.b.getBlockX());
			double y0 = Math.min(sel.a.getBlockY(), sel.b.getBlockY());
			double z0 = Math.min(sel.a.getBlockZ(), sel.b.getBlockZ());
			double x1 = Math.max(sel.a.getBlockX(), sel.b.getBlockX()) + 1;
			double y1 = Math.max(sel.a.getBlockY(), sel.b.getBlockY()) + 1;
			double z1 = Math.max(sel.a.getBlockZ(), sel.b.getBlockZ()) + 1;
			double total = 4 * ((x1 - x0) + (y1 - y0) + (z1 - z0));
			double step = Math.max(1.0D, total / 300.0D);
			for (double x = x0; x <= x1; x += step) {
				for (double y : new double[] { y0, y1 }) {
					for (double z : new double[] { z0, z1 }) point(player, x, y, z);
				}
			}
			for (double y = y0; y <= y1; y += step) {
				for (double x : new double[] { x0, x1 }) {
					for (double z : new double[] { z0, z1 }) point(player, x, y, z);
				}
			}
			for (double z = z0; z <= z1; z += step) {
				for (double x : new double[] { x0, x1 }) {
					for (double y : new double[] { y0, y1 }) point(player, x, y, z);
				}
			}
		}
	}

	private static void point(Player player, double x, double y, double z) {
		player.spawnParticle(Particle.END_ROD, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
	}
}
