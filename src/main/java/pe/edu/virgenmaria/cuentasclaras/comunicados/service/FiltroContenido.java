package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.ContenidoSinCobranza;

import java.net.URI;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sprint 9, tanda 3 (sección 3.8, decisión 155, E21, E22, B4 y B7): lo que un comunicado (título, texto y el texto del
 * PDF) no puede decir. SIN EXCEPCIONES, tampoco para Dirección: lo de cobranza va por los recordatorios y el portal de
 * la cobranza. Rechaza con un motivo claro y el fragmento (que ve solo quien escribe; la bitácora guarda solo la regla).
 * <p>
 * Antes de revisar, normaliza (B4): NFKC (las letras de ancho completo pasan a las normales), quita los caracteres de
 * formato ({@code \p{Cf}}: ancho cero, guion suave), los acentos combinados ({@code \p{M}} tras NFD) y los dígitos que no
 * son ASCII (árabes, índicos…), y lleva las letras cirílicas y griegas que parecen latinas a la latina. Para las palabras,
 * además: minúsculas, {@code 0→o 1→i 3→e 4→a 5→s 7→t}, letras sueltas separadas por espacios o signos se juntan
 * («d e u d a») y las letras repetidas tres o más veces cuentan una.
 * <p>
 * Reglas: cobranza (deuda, deben pagar, moroso, pensión, cuota, pago, pagar, vencido, cobranza, adeuda…); montos («S/»,
 * soles, US$, cifras con dos decimales que no son horas); medios de pago y cuentas (Yape, Plin, transferencia, depósito,
 * número de cuenta, CCI, interbancaria, bancos, números de 13 a 20 dígitos); contactos y documentos (celulares, números
 * de 8 dígitos y correos); enlaces fuera de la plataforma. Y, como segunda capa, la de la base
 * ({@link ContenidoSinCobranza}, la misma de {@code trg_comunicado_nace}).
 */
@Component
public class FiltroContenido {

	/** La regla que coincidió (va en la bitácora; nunca el texto). */
	public enum Regla {
		COBRANZA("menciona una deuda, un pago o un cobro", true),
		MONTO("menciona un monto", true),
		MEDIO_DE_PAGO("menciona un medio de pago o una cuenta", true),
		CONTACTO("menciona un celular, un documento o un correo", false),
		ENLACE("tiene un enlace fuera de la plataforma", false);

		private final String motivo;

		private final boolean deCobranza;

		Regla(String motivo, boolean deCobranza) {
			this.motivo = motivo;
			this.deCobranza = deCobranza;
		}

		public String motivo() {
			return motivo;
		}

		/** Si es de cobranza o de pagos (el evento va resaltado y, si es de un docente, alerta a Dirección). */
		public boolean deCobranza() {
			return deCobranza;
		}
	}

	/** El resultado: aceptado, o la regla, el motivo claro y el fragmento que coincidió. */
	public record Resultado(boolean aceptado, Regla regla, String motivo, String fragmento) {

		static final Resultado ACEPTADO = new Resultado(true, null, null, null);

		static Resultado rechazo(Regla regla, String detalle, String fragmento) {
			return new Resultado(false, regla, detalle == null ? regla.motivo() : detalle, fragmento);
		}
	}

	private static final int BANDERAS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

	private static final Pattern COBRANZA = Pattern.compile("\\b(deuda\\w*|adeud\\w*|moros\\w*|pensi[oó]n(es)?|cuotas?"
			+ "|pagos?|pagar\\w*|pague\\w*|pagad[oa]s?|vencid[oa]s?|cobranzas?|cobros?|cobrar\\w*|atrasad[oa]s? en (sus )?pagos"
			+ "|deben? (pagar|abonar|cancelar|regularizar|ponerse al dia|\\S+ (pensi|cuota|mes|monto|sol)))\\b", BANDERAS);

	private static final Pattern MONTO_PALABRAS = Pattern.compile("\\b(nuevos soles|soles|dolares|us dolares)\\b",
			BANDERAS);

	private static final Pattern MEDIOS = Pattern.compile("\\b(yape\\w*|plin|transferencias?|transferir|depositos?"
			+ "|deposit\\w*|numero de cuenta|nro\\.? de cuenta|n° de cuenta|cuenta (bancaria|corriente|de ahorros?)|cci"
			+ "|interbancari[oa]|bcp|bbva|interbank|scotiabank|banco de la nacion|tunki|agora)\\b", BANDERAS);

	private static final Pattern CUENTA_LARGA = Pattern.compile("(?<![0-9])[0-9](?:[ \\-]?[0-9]){12,19}(?![0-9])");

	private static final Pattern CELULAR = Pattern.compile("(?<![0-9])(?:\\+?51[ \\-]?)?9(?:[ \\-]?[0-9]){8}(?![0-9])");

	private static final Pattern DOCUMENTO = Pattern.compile("(?<![0-9])[0-9]{8}(?![0-9])");

	private static final Pattern CORREO = Pattern.compile("[\\p{L}0-9._%+\\-]+@[\\p{L}0-9\\-]+(\\.[\\p{L}0-9\\-]+)+",
			BANDERAS);

	private static final Pattern ENLACE = Pattern.compile("(?:https?://|www\\.)[^\\s]+|\\b[\\p{L}0-9\\-]+(?:\\.[\\p{L}0-9\\-]+)*"
			+ "\\.(?:com|net|org|pe|edu|gob|io|me|app|info|biz|co|xyz|ly|gl|link|site|online|shop|store)(?:\\.[a-z]{2})?\\b",
			BANDERAS);

	private static final Pattern LETRAS_SUELTAS = Pattern.compile("(?<=(?:^|[^\\p{L}])\\p{L})[ .\\-_*·/]+(?=\\p{L}(?:[^\\p{L}]|$))");

	private static final Pattern REPETIDAS = Pattern.compile("(\\p{L})\\1{2,}");

	private static final Pattern SIGNOS_REPETIDOS = Pattern.compile("([^\\p{L}0-9\\s])\\1+");

	private static final Map<Character, Character> HOMOGLIFOS = homoglifos();

	private static final Pattern BLANCOS_INVISIBLES = Pattern.compile("[\\u115F\\u1160\\u3164\\uFFA0\\u2800]+");

	private final Set<String> dominiosPermitidos;

	public FiltroContenido(@Value("${cuentasclaras.mensajeria.url-publica:http://localhost:8080}") String urlPublica,
			@Value("${cuentasclaras.comunicados.dominios-permitidos:}") String otros) {
		Set<String> dominios = new LinkedHashSet<>();
		host(urlPublica).ifPresent(dominios::add);
		Arrays.stream(otros.split(",")).map(d -> d.strip().toLowerCase(Locale.ROOT)).filter(d -> !d.isEmpty())
				.forEach(dominios::add);
		this.dominiosPermitidos = Set.copyOf(dominios);
	}

	/** Los dominios a los que un comunicado o su PDF pueden enlazar (el de la plataforma y la lista permitida). */
	public Set<String> dominiosPermitidos() {
		return dominiosPermitidos;
	}

	/** Revisa un texto (título, texto del comunicado o texto extraído del PDF). */
	public Resultado revisar(String texto) {
		if (texto == null || texto.isBlank()) {
			return Resultado.ACEPTADO;
		}
		String base = normalizar(texto);
		String palabras = paraPalabras(base);
		Optional<Resultado> rechazo = buscar(COBRANZA, palabras, Regla.COBRANZA)
				.or(() -> buscar(MONTO_PALABRAS, palabras, Regla.MONTO))
				.or(() -> buscar(MEDIOS, palabras, Regla.MEDIO_DE_PAGO))
				.or(() -> buscar(CORREO, base, Regla.CONTACTO))
				.or(() -> enlaceAjeno(base))
				.or(() -> buscar(CUENTA_LARGA, base, Regla.MEDIO_DE_PAGO))
				.or(() -> buscar(CELULAR, base, Regla.CONTACTO))
				.or(() -> buscar(DOCUMENTO, base, Regla.CONTACTO))
				.or(() -> ContenidoSinCobranza.motivo(base).map(m -> Resultado.rechazo(m.contains("monto") ? Regla.MONTO
						: Regla.COBRANZA, m, null)))
				.or(() -> ContenidoSinCobranza.motivo(palabras).map(m -> Resultado.rechazo(Regla.COBRANZA, m, null)));
		return rechazo.orElse(Resultado.ACEPTADO);
	}

	private static Optional<Resultado> buscar(Pattern patron, String texto, Regla regla) {
		Matcher m = patron.matcher(texto);
		return m.find() ? Optional.of(Resultado.rechazo(regla, null, recortar(m.group()))) : Optional.empty();
	}

	private Optional<Resultado> enlaceAjeno(String texto) {
		Matcher m = ENLACE.matcher(texto);
		while (m.find()) {
			String encontrado = m.group();
			String host = host(encontrado.startsWith("http") ? encontrado : "https://" + encontrado).orElse(encontrado)
					.toLowerCase(Locale.ROOT);
			boolean permitido = dominiosPermitidos.stream().anyMatch(d -> host.equals(d) || host.endsWith("." + d));
			if (!permitido) {
				return Optional.of(Resultado.rechazo(Regla.ENLACE, null, recortar(encontrado)));
			}
		}
		return Optional.empty();
	}

	/**
	 * B4: NFKC, sin caracteres de formato ni acentos combinados, dígitos a ASCII y homoglifos a latino. Conserva
	 * mayúsculas, números y signos (para las reglas de montos, contactos y enlaces).
	 */
	public static String normalizar(String texto) {
		String nfkc = Normalizer.normalize(texto, Normalizer.Form.NFKC);
		// QA-S9-1: las «letras en blanco» (relleno hangul, blanco braille) no son \p{Cf} pero no se ven («pago» partido con U+3164).
		String sinFormato = BLANCOS_INVISIBLES.matcher(sinCaracteresDeFormato(nfkc)).replaceAll("");
		String nfd = Normalizer.normalize(sinFormato, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		StringBuilder salida = new StringBuilder(nfd.length());
		nfd.codePoints().forEach(cp -> {
			if (Character.getType(cp) == Character.DECIMAL_DIGIT_NUMBER) {
				salida.append((char) ('0' + Character.digit(cp, 10)));
			}
			else if (cp < Character.MIN_SUPPLEMENTARY_CODE_POINT && HOMOGLIFOS.containsKey((char) cp)) {
				salida.append(HOMOGLIFOS.get((char) cp));
			}
			else {
				salida.appendCodePoint(cp);
			}
		});
		return Normalizer.normalize(salida, Normalizer.Form.NFC);
	}

	/** Quita los caracteres de formato (\p{Cf}): ancho cero, guion suave, marcas de dirección (B4, como la base). */
	public static String sinCaracteresDeFormato(String texto) {
		return texto == null ? null : texto.replaceAll("\\p{Cf}+", "");
	}

	/** La forma para buscar palabras: minúsculas, números leídos como letras, letras sueltas juntas, sin repetidas. */
	static String paraPalabras(String normalizado) {
		String minusculas = normalizado.toLowerCase(Locale.ROOT);
		StringBuilder leet = new StringBuilder(minusculas.length());
		for (char c : minusculas.toCharArray()) {
			leet.append(switch (c) {
				case '0' -> 'o';
				case '1' -> 'i';
				case '3' -> 'e';
				case '4' -> 'a';
				case '5' -> 's';
				case '7' -> 't';
				case '@' -> 'a';
				case '$' -> 's';
				default -> c;
			});
		}
		String sinSignos = SIGNOS_REPETIDOS.matcher(leet).replaceAll("$1");
		String juntas = LETRAS_SUELTAS.matcher(sinSignos).replaceAll("");
		return REPETIDAS.matcher(juntas).replaceAll("$1");
	}

	private static String recortar(String fragmento) {
		String limpio = fragmento.strip();
		return limpio.length() > 40 ? limpio.substring(0, 40) + "…" : limpio;
	}

	private static Optional<String> host(String url) {
		try {
			String host = URI.create(url.strip()).getHost();
			return Optional.ofNullable(host).map(h -> h.toLowerCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	private static Map<Character, Character> homoglifos() {
		// Cirílico y griego que se ven como latino (minúsculas y mayúsculas).
		// QA del sprint 9 (QA-S9-1): también las latinas «raras» que NFKC no lleva a la normal (ı sin punto, alfa latina,
		// iota latina, ʋ, ȷ), las versalitas fonéticas (ᴅᴇᴜᴅᴀ, ᴘᴇɴsɪᴏɴ), más cirílicas (ӏ, Ӏ, һ, ԛ, ԝ, ү), armenias que
		// parecen latinas (օ, ս, ո, ց, հ, զ) y las griegas ϲ y ϳ. Con ellas, «pensiօn», «deսda» o «pɑgo» pasaban.
		String origen = "аеорсухкмтнвіјѕԁɡАВЕКМНОРСТХУІЈЅ" + "αορεντικυχΑΒΕΖΗΙΚΜΝΟΡΤΥΧ"
				+ "ıɩɑʋȷ" + "ᴀʙᴄᴅᴇɢʜɪᴊᴋʟᴍɴᴏᴘʀꜱᴛᴜᴠᴡʏᴢ" + "ӏӀһԛԝү" + "օսոցհզ" + "ϲϳ";
		String destino = "aeopcyxkmthbijsdgABEKMHOPCTXYIJS" + "aopevtikuxABEZHIKMNOPTYX"
				+ "iiauj" + "abcdeghijklmnoprstuvwyz" + "llhqwy" + "ounghq" + "cj";
		if (origen.length() != destino.length()) {
			throw new IllegalStateException("La tabla de letras parecidas está descuadrada");
		}
		Map<Character, Character> mapa = new java.util.HashMap<>();
		for (int i = 0; i < origen.length(); i++) {
			mapa.put(origen.charAt(i), destino.charAt(i));
		}
		return Map.copyOf(mapa);
	}

	/** Los motivos de todas las reglas (para la ayuda de la pantalla). */
	public static List<String> motivos() {
		return Arrays.stream(Regla.values()).map(Regla::motivo).toList();
	}
}
