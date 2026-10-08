package pe.edu.virgenmaria.cuentasclaras.comun.texto;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Forma canónica de un celular o un correo para COMPARAR contactos (correcciones del sprint 5, S5-A1 y S5-M5). Nunca se
 * guarda ni se usa como destino: el mensaje sale siempre al contacto registrado tal cual.
 * <ul>
 *   <li>Correo: minúsculas; la parte local pierde todo desde el primer «+» (alias de Gmail, Outlook, iCloud, Fastmail…);
 *       en {@code gmail.com} y {@code googlemail.com} también los puntos, y {@code googlemail.com} es {@code gmail.com}.
 *       Así {@code Lucia.Caja+ramos@googlemail.com} es {@code luciacaja@gmail.com}.</li>
 *   <li>Celular: solo los dígitos; un celular peruano de 9 dígitos gana el 51 («987654321», «+51 987 654 321» y
 *       «51987654321» son el mismo).</li>
 * </ul>
 * La misma regla está en MySQL como función {@code cc_contacto_normal} (03-triggers.sql), que usa trg_mensaje_nace.
 */
public final class ContactoNormal {

	private static final Set<String> GMAIL = Set.of("gmail.com", "googlemail.com");

	private ContactoNormal() {
	}

	/** La forma canónica (vacío si es {@code null} o está en blanco). */
	public static String de(String contacto) {
		if (contacto == null || contacto.isBlank()) {
			return "";
		}
		String limpio = contacto.strip().toLowerCase(Locale.ROOT);
		int arroba = limpio.lastIndexOf('@');
		if (arroba > 0) {
			return correo(limpio.substring(0, arroba), limpio.substring(arroba + 1));
		}
		String digitos = limpio.replaceAll("\\D", "");
		return digitos.matches("^9\\d{8}$") ? "51" + digitos : digitos;
	}

	/** {@code true} si los dos contactos llegan al mismo buzón o al mismo celular. */
	public static boolean iguales(String a, String b) {
		String na = de(a);
		return !na.isEmpty() && Objects.equals(na, de(b));
	}

	private static String correo(String local, String dominio) {
		int mas = local.indexOf('+');
		String base = mas >= 0 ? local.substring(0, mas) : local;
		if (GMAIL.contains(dominio)) {
			return base.replace(".", "") + "@gmail.com";
		}
		return base + "@" + dominio;
	}
}
