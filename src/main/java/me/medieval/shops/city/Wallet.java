package me.medieval.shops.city;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import me.medieval.shops.bank.BankManager;
import me.medieval.shops.currency.Currency;
import me.medieval.shops.currency.CurrencySet;

/** Cobrar y pagar con las monedas (items) que lleva el jugador, con cambio entre moneda alta y base. */
public final class Wallet {

	public static long countValue(Player player, CurrencySet set) {
		PlayerInventory inv = player.getInventory();
		long total = BankManager.countCurrency(inv, set.getBase());
		Currency high = set.getHighOrNull();
		if (high != null) total += (long) BankManager.countCurrency(inv, high) * high.getValue();
		return total;
	}

	/** Cobra {@code value} monedas base. @return false si no le alcanza (no cobra nada). */
	public static boolean pay(Player player, CurrencySet set, long value) {
		if (value <= 0) return true;
		Currency base = set.getBase();
		Currency high = set.getHighOrNull();
		PlayerInventory inv = player.getInventory();
		long baseHave = BankManager.countCurrency(inv, base);
		long highHave = high == null ? 0 : BankManager.countCurrency(inv, high);
		long hv = high == null ? 1 : Math.max(1, high.getValue());
		if (baseHave + highHave * hv < value) return false;

		long useHigh = high == null ? 0 : Math.min(highHave, value / hv);
		long rem = value - useHigh * hv;
		long useBase = Math.min(baseHave, rem);
		rem -= useBase;
		long extraHigh = 0;
		if (rem > 0) {
			// Faltan monedas base: se gasta una moneda alta de mas y se devuelve el cambio.
			extraHigh = (rem + hv - 1) / hv;
			if (useHigh + extraHigh > highHave) return false;
		}
		long paid = (useHigh + extraHigh) * hv + useBase;
		long change = paid - value;
		if (useHigh + extraHigh > 0 && high != null) BankManager.removeCurrency(inv, high, (int) (useHigh + extraHigh));
		if (useBase > 0) BankManager.removeCurrency(inv, base, (int) useBase);
		if (change > 0) give(player, set, change);
		return true;
	}

	/** Entrega {@code value} monedas base (en moneda alta + base). Lo que no cabe cae al suelo. */
	public static void give(Player player, CurrencySet set, long value) {
		if (value <= 0) return;
		Currency base = set.getBase();
		Currency high = set.getHighOrNull();
		long highCount = high == null ? 0 : value / Math.max(1, high.getValue());
		long baseCount = high == null ? value : value % Math.max(1, high.getValue());
		giveStacks(player, high, highCount);
		giveStacks(player, base, baseCount);
	}

	private static void giveStacks(Player player, Currency currency, long amount) {
		if (currency == null || amount <= 0) return;
		int notFit = BankManager.giveCurrency(player.getInventory(), currency, amount);
		int stack = Math.max(1, currency.getMaxStackSize());
		int drops = 0;
		while (notFit > 0 && drops < 200) {
			int n = Math.min(stack, notFit);
			player.getWorld().dropItemNaturally(player.getLocation(), currency.getItemData().createItemStack(n));
			notFit -= n;
			drops++;
		}
	}

	/** true si caben {@code amount} unidades de ese item en el inventario. */
	public static boolean hasRoom(PlayerInventory inv, ItemStack template, int amount) {
		int need = amount;
		int max = Math.max(1, template.getMaxStackSize());
		for (ItemStack it : inv.getStorageContents()) {
			if (it == null || it.getType() == Material.AIR) need -= max;
			else if (it.isSimilar(template)) need -= Math.max(0, max - it.getAmount());
			if (need <= 0) return true;
		}
		return need <= 0;
	}

	private Wallet() {
	}
}
