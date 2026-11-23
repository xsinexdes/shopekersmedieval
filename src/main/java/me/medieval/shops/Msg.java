package me.medieval.shops;

import org.bukkit.command.CommandSender;

/** Mensajes editables (seccion "mensajes:" de medieval.yml). Soportan colores con & y {marcadores}. */
public final class Msg {

	/** Texto del mensaje con los marcadores reemplazados: Msg.get("clave", "ciudad", "Valmora", ...). */
	public static String get(String key, String... pairs) {
		MedievalEconomy economy = MedievalEconomy.get();
		String text = economy == null ? key : economy.config().getString("mensajes." + key, key);
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			text = text.replace("{" + pairs[i] + "}", pairs[i + 1]);
		}
		return MedievalEconomy.color(text);
	}

	public static void send(CommandSender to, String key, String... pairs) {
		to.sendMessage(get(key, pairs));
	}

	private Msg() {
	}
}
