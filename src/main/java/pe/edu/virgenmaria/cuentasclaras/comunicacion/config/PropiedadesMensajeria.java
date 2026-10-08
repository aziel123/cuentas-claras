package pe.edu.virgenmaria.cuentasclaras.comunicacion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code cuentasclaras.mensajeria} (sprint 5, sección 8.1). Los conectores reales están APAGADOS por defecto
 * ({@code NINGUNO}); el SIMULADO solo lo activan los perfiles dev, test y piloto, y su bean ni existe en prod. Los
 * secretos llegan solo por variable de entorno.
 */
@ConfigurationProperties("cuentasclaras.mensajeria")
public record PropiedadesMensajeria(@DefaultValue Whatsapp whatsapp, @DefaultValue Correo correo,
		@DefaultValue("30s") Duration envioCada, @DefaultValue("1m") Duration reintentoInicial,
		@DefaultValue("60m") Duration reintentoMaximo, @DefaultValue("8") int intentosMaximos,
		@DefaultValue("15") int alertaPendienteMinutos, @DefaultValue("60") int alertaPagoSinEntregarMinutos,
		@DefaultValue("12") int historialFamiliaMeses, @DefaultValue("http://localhost:8080") String urlPublica,
		@DefaultValue("48h") Duration vigenciaEnlace) {

	public static final String NINGUNO = "NINGUNO";

	public static final String SIMULADO = "SIMULADO";

	public PropiedadesMensajeria {
		if (intentosMaximos < 1 || intentosMaximos > 20) {
			throw new IllegalArgumentException("cuentasclaras.mensajeria.intentos-maximos: de 1 a 20");
		}
		if (reintentoInicial.isNegative() || reintentoMaximo.compareTo(reintentoInicial) < 0) {
			throw new IllegalArgumentException("cuentasclaras.mensajeria: el reintento máximo no puede ser menor que el inicial");
		}
		if (vigenciaEnlace.isNegative() || vigenciaEnlace.isZero() || vigenciaEnlace.compareTo(Duration.ofHours(71)) > 0) {
			throw new IllegalArgumentException("cuentasclaras.mensajeria.vigencia-enlace: hasta 71 horas");
		}
		urlPublica = urlPublica == null ? "" : urlPublica.strip().replaceAll("/+$", "");
	}

	/** Espera antes del intento {@code intento} (1, 2, 4… minutos, hasta el máximo). */
	public Duration esperaTrasIntentos(int intento) {
		long factor = 1L << Math.min(Math.max(intento - 1, 0), 20);
		Duration espera = reintentoInicial.multipliedBy(factor);
		return espera.compareTo(reintentoMaximo) > 0 ? reintentoMaximo : espera;
	}

	/** {@code cuentasclaras.mensajeria.whatsapp}: Cloud API de Meta. */
	public record Whatsapp(@DefaultValue(NINGUNO) String proveedor, @DefaultValue("https://graph.facebook.com") String api,
			@DefaultValue("v21.0") String versionApi, @DefaultValue("") String numeroId, @DefaultValue("") String token,
			@DefaultValue("") String secretoApp, @DefaultValue("") String tokenVerificacion,
			@DefaultValue("graph.facebook.com") String dominiosPermitidos,
			@DefaultValue("false") boolean permitirRealFueraDeProd, @DefaultValue("") String numerosDePrueba,
			@DefaultValue("es") String idiomaPlantillas) {

		public Whatsapp {
			proveedor = mayusculas(proveedor);
		}

		/** Fuera de prod, el conector real SOLO escribe a estos números (nunca a un padre real desde dev). */
		public Set<String> listaDePrueba() {
			return lista(numerosDePrueba);
		}
	}

	/** {@code cuentasclaras.mensajeria.correo}: SMTP (spring.mail.*) con el remitente del colegio. */
	public record Correo(@DefaultValue(NINGUNO) String proveedor, @DefaultValue("") String remitente,
			@DefaultValue("false") boolean permitirRealFueraDeProd, @DefaultValue("") String correosDePrueba) {

		public Correo {
			proveedor = mayusculas(proveedor);
		}

		public Set<String> listaDePrueba() {
			return lista(correosDePrueba);
		}
	}

	private static String mayusculas(String texto) {
		return texto == null || texto.isBlank() ? NINGUNO : texto.strip().toUpperCase(Locale.ROOT);
	}

	private static Set<String> lista(String texto) {
		if (texto == null || texto.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(texto.split(",")).map(String::strip).filter(s -> !s.isEmpty())
				.map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
	}
}
