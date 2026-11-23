package me.medieval.shops.city;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.World;

import me.medieval.shops.market.MarketCategory;

/** Una ciudad: zona (cubo), impuestos, modificadores de precio por categoria y datos de trofeos. */
public final class City {

	public final String id;
	public String name;
	public String world;
	public int minX, minY, minZ, maxX, maxY, maxZ;
	public double spawnX, spawnY, spawnZ;
	public float spawnYaw;
	/** Impuesto en % sobre compras y ventas de los mercaderes de la ciudad. */
	public double tax = 5.0D;
	/** Modificador de precio por categoria en % (+20 = mas caro, -15 = mas barato). */
	public final Map<MarketCategory, Double> mods = new EnumMap<>(MarketCategory.class);
	/** Dinero acumulado por impuestos (en monedas base). */
	public long treasury = 0L;
	/** Valor base del trofeo de esta ciudad (en monedas base). */
	public long trophyBase = 100L;
	/** Trofeos disponibles ahora mismo (-1 = sin iniciar). */
	public double trophyStock = -1.0D;
	/** Trofeos de otras ciudades vendidos aqui hace poco (baja el precio): origen -> unidades. */
	public final Map<String, Double> saturation = new HashMap<>();

	public City(String id, String name) {
		this.id = id;
		this.name = name;
	}

	public boolean contains(Location l) {
		if (l == null || l.getWorld() == null || !l.getWorld().getName().equals(world)) return false;
		int x = l.getBlockX();
		int y = l.getBlockY();
		int z = l.getBlockZ();
		return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
	}

	public double centerX() {
		return (minX + maxX + 1) / 2.0D;
	}

	public double centerZ() {
		return (minZ + maxZ + 1) / 2.0D;
	}

	/** Distancia horizontal desde un punto hasta la zona de la ciudad (0 si esta dentro). */
	public double horizontalDistance(Location l) {
		double dx = Math.max(Math.max(minX - l.getX(), 0.0D), l.getX() - (maxX + 1));
		double dz = Math.max(Math.max(minZ - l.getZ(), 0.0D), l.getZ() - (maxZ + 1));
		return Math.hypot(dx, dz);
	}

	public long volume() {
		return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
	}

	/** Multiplicador de precio (1.0 = normal) de una categoria en esta ciudad. */
	public double mult(MarketCategory category) {
		return 1.0D + mods.getOrDefault(category, 0.0D) / 100.0D;
	}

	public Location spawn(World w) {
		return new Location(w, spawnX, spawnY, spawnZ, spawnYaw, 0.0F);
	}
}
