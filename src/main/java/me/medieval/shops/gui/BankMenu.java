package me.medieval.shops.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import me.medieval.shops.MedievalEconomy;
import me.medieval.shops.Sfx;
import me.medieval.shops.bank.BankManager;
import me.medieval.shops.currency.Currency;
import me.medieval.shops.currency.CurrencySet;

/** Menu del banco (global o de un mercader banquero): depositar y retirar monedas. */
public final class BankMenu extends Menu {

	private static final int[] AMOUNTS = { 1, 10, 64, -1 }; // -1 = todo
	private static final int DEPOSIT_BASE = 10;
	private static final int DEPOSIT_HIGH = 14;
	private static final int WITHDRAW_BASE = 19;
	private static final int WITHDRAW_HIGH = 23;

	private final BankManager bank;
	private final String bankKey;

	public BankMenu(MedievalEconomy economy, Player player) {
		this(economy, player, BankManager.GLOBAL, null);
	}

	public BankMenu(MedievalEconomy economy, Player player, String bankKey, String bankName) {
		super(economy, player);
		this.bank = economy.bank();
		this.bankKey = bankKey;
		String title = economy.config().getString("gui.titulos.banco", "&0Banco del Reino");
		if (bankName != null && !bankName.isEmpty()) {
			title = title + " &8- &0" + org.bukkit.ChatColor.stripColor(MedievalEconomy.color(bankName));
		}
		create(54, Gui.title(economy, title, Gui.BG_BANK));
	}

	private String amountLabel(int amount) {
		return amount < 0 ? "todo" : String.valueOf(amount);
	}

	@Override
	protected void render() {
		bank.remember(player.getUniqueId(), player.getName());
		CurrencySet set = bank.currencies(bankKey);
		Currency base = set.getBase();
		Currency high = set.getHighOrNull();
		long balance = bank.getBalance(bankKey, player.getUniqueId());

		if (!Gui.usesBackgrounds(economy)) {
			for (int i = 0; i < 54; i++) inventory.setItem(i, Gui.filler());
		}

		List<String> balanceLore = new ArrayList<>();
		balanceLore.add("&7" + BankManager.format(balance, set));
		if (bank.getLimit() > 0) balanceLore.add("&7Limite de saldo: &f" + bank.getLimit());
		if (bank.getInterestPercent() > 0.0D) {
			balanceLore.add("&7Interes: &a" + String.format(Locale.ROOT, "%.2f", bank.getInterestPercent())
					+ "% &7por hora");
		}
		inventory.setItem(4, Gui.button(economy, Gui.BTN_COINS, Material.GOLD_BLOCK,
				"&6Tu saldo: &e" + balance, balanceLore));

		inventory.setItem(9, Gui.button(economy, Gui.BTN_DEPOSIT, Material.HOPPER, "&aDEPOSITAR",
				"&7Guarda monedas en el banco."));
		inventory.setItem(18, Gui.button(economy, Gui.BTN_WITHDRAW, Material.DISPENSER, "&cRETIRAR",
				"&7Saca monedas del banco."));

		renderRow(set, base, DEPOSIT_BASE, true);
		renderRow(set, base, WITHDRAW_BASE, false);
		if (high != null) {
			renderRow(set, high, DEPOSIT_HIGH, true);
			renderRow(set, high, WITHDRAW_HIGH, false);
		}

		if (BankManager.GLOBAL.equals(bankKey)) {
			inventory.setItem(31, Gui.item(Material.PAPER, "&eTransferir a otro jugador",
					"&7Usa: &f/banco pagar <jugador> <monto>"));
		}
		inventory.setItem(45, Gui.button(economy, Gui.BTN_MARKET, Material.MAP, "&6Mercado del Reino",
				"&7Ver precios y graficas"));
		inventory.setItem(49, Gui.button(economy, Gui.BTN_CLOSE, Material.BARRIER, "&cCerrar"));
	}

	private void renderRow(CurrencySet set, Currency currency, int firstSlot, boolean deposit) {
		Material material = currency.getItemData().getType();
		int have = BankManager.countCurrency(player.getInventory(), currency);
		long balance = bank.getBalance(bankKey, player.getUniqueId());
		long inBank = balance / Math.max(1, currency.getValue());
		for (int i = 0; i < AMOUNTS.length; i++) {
			int amount = AMOUNTS[i];
			String name = (deposit ? "&aDepositar " : "&cRetirar ") + amountLabel(amount) + " &7"
					+ currency.getDisplayName();
			String stock = deposit ? "&7Tienes: &f" + have : "&7En el banco: &f" + inBank;
			inventory.setItem(firstSlot + i, Gui.item(material, name, stock, "",
					deposit ? "&eClick: &fdepositar" : "&eClick: &fretirar"));
		}
	}

	private void handleRow(Currency currency, int index, boolean deposit) {
		int amount = AMOUNTS[index];
		int done;
		if (deposit) {
			done = bank.deposit(player, bankKey, currency, amount < 0 ? Integer.MAX_VALUE : amount);
		} else {
			long balance = bank.getBalance(bankKey, player.getUniqueId());
			int max = (int) Math.min(Integer.MAX_VALUE, balance / Math.max(1, currency.getValue()));
			done = bank.withdraw(player, bankKey, currency, amount < 0 ? max : amount);
		}
		Sfx.play(player, done <= 0 ? Sfx.Type.ERROR : deposit ? Sfx.Type.DEPOSIT : Sfx.Type.WITHDRAW);
		if (done <= 0) {
			player.sendMessage(MedievalEconomy.color("&6&l[Banco] &cNo se pudo "
					+ (deposit ? "depositar (no tienes monedas o llegaste al limite)."
							: "retirar (saldo insuficiente o inventario lleno).")));
		} else {
			player.sendMessage(MedievalEconomy.color("&6&l[Banco] &a" + (deposit ? "Depositaste " : "Retiraste ")
					+ "&e" + done + " &a" + currency.getDisplayName() + "&a."));
		}
		refresh();
	}

	@Override
	protected void onClick(int slot, ClickType click) {
		CurrencySet set = bank.currencies(bankKey);
		Currency base = set.getBase();
		Currency high = set.getHighOrNull();
		if (slot >= DEPOSIT_BASE && slot < DEPOSIT_BASE + 4) {
			handleRow(base, slot - DEPOSIT_BASE, true);
		} else if (slot >= WITHDRAW_BASE && slot < WITHDRAW_BASE + 4) {
			handleRow(base, slot - WITHDRAW_BASE, false);
		} else if (high != null && slot >= DEPOSIT_HIGH && slot < DEPOSIT_HIGH + 4) {
			handleRow(high, slot - DEPOSIT_HIGH, true);
		} else if (high != null && slot >= WITHDRAW_HIGH && slot < WITHDRAW_HIGH + 4) {
			handleRow(high, slot - WITHDRAW_HIGH, false);
		} else if (slot == 45) {
			Sfx.play(player, Sfx.Type.PAGE);
			new CategoryMenu(economy, player).open();
		} else if (slot == 49) {
			player.closeInventory();
		}
	}
}
