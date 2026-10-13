package pe.edu.virgenmaria.cuentasclaras.conectores.dto;

import pe.edu.virgenmaria.cuentasclaras.conectores.model.TipoConector;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Sprint 8, tanda 2 (sección 3.6): la configuración SIN secretos de un conector, un record por tipo. Se guarda como JSON
 * en {@code conector_colegio.configuracion} y no cambia (cada cambio es una fila nueva). Los valores son textos (no los
 * enums de comprobantes, pasarela o recaudación: esos módulos dependen de {@code conectores}, no al revés); cada módulo
 * los traduce al usar el conector.
 */
public sealed interface ConfiguracionConector {

	TipoConector tipo();

	/** El texto que se muestra (sin secretos ni números completos de cuenta). */
	String resumen();

	default String aJson() {
		return Json.MAPA.writeValueAsString(this);
	}

	static ConfiguracionConector deJson(TipoConector tipo, String json) {
		Class<? extends ConfiguracionConector> clase = switch (tipo) {
			case OSE -> Ose.class;
			case PASARELA -> Pasarela.class;
			case CUENTA_BANCARIA -> CuentaBancaria.class;
			case RECAUDACION -> Recaudacion.class;
		};
		try {
			return Json.MAPA.readValue(json, clase);
		}
		catch (JacksonException e) {
			throw new IllegalStateException("La configuración guardada del conector " + tipo + " no se puede leer");
		}
	}

	/** El lector de JSON (uno para todos). */
	final class Json {

		static final JsonMapper MAPA = JsonMapper.builder().build();

		private Json() {
		}
	}

	/**
	 * OSE: la ruta de la API (solo NUBEFACT), el RUC emisor (el del colegio), las cuatro series y la afectación al IGV
	 * ({@code INAFECTO} o {@code EXONERADO}).
	 */
	record Ose(String ruta, String rucEmisor, String serieBoleta, String serieFactura, String serieNotaBoleta,
			String serieNotaFactura, String afectacionIgv) implements ConfiguracionConector {

		@Override
		public TipoConector tipo() {
			return TipoConector.OSE;
		}

		@Override
		public String resumen() {
			return "Series " + serieBoleta + ", " + serieFactura + ", " + serieNotaBoleta + " y " + serieNotaFactura
					+ "; RUC emisor " + rucEmisor + "; " + afectacionIgv.toLowerCase(Locale.ROOT) + " al IGV";
		}

		public List<String> series() {
			return List.of(serieBoleta, serieFactura, serieNotaBoleta, serieNotaFactura);
		}
	}

	/** Pasarela: la llave pública, los medios aceptados ({@code YAPE}, {@code PLIN}, {@code TARJETA}) y el monto máximo. */
	record Pasarela(String llavePublica, List<String> medios, BigDecimal montoMaximo) implements ConfiguracionConector {

		@Override
		public TipoConector tipo() {
			return TipoConector.PASARELA;
		}

		@Override
		public String resumen() {
			return "Medios " + String.join(", ", medios) + "; hasta S/ " + montoMaximo.toPlainString() + " por pago";
		}
	}

	/**
	 * Cuenta bancaria: número, CCI (20 dígitos) y moneda (PEN). Reemplaza a {@code caja.cuentas-deposito}. Correcciones del
	 * sprint 8 (S8-M3): el titular de la cuenta (razón social) y su RUC, que debe ser el del colegio. Las filas anteriores
	 * no los tienen ({@code null}).
	 */
	record CuentaBancaria(String numero, String cci, String moneda, String titular, String rucTitular)
			implements ConfiguracionConector {

		public CuentaBancaria(String numero, String cci, String moneda) {
			this(numero, cci, moneda, null, null);
		}

		@Override
		public TipoConector tipo() {
			return TipoConector.CUENTA_BANCARIA;
		}

		@Override
		public String resumen() {
			return "Cuenta en soles que termina en " + terminacion(numero) + " (CCI que termina en " + terminacion(cci)
					+ ")";
		}

		/** Los 4 últimos dígitos (lo único que va en avisos y pantallas). */
		public static String terminacion(String numero) {
			if (numero == null || numero.length() < 4) {
				return "****";
			}
			return numero.substring(numero.length() - 4);
		}
	}

	/**
	 * Recaudación: la modalidad ({@code CON_BASE_DE_DEUDAS} o {@code SIN_BASE}), la glosa con que el banco abona el lote
	 * (la regla EXACTA de la conciliación; vacía si no se usa) y si abona por lote o pago por pago.
	 */
	record Recaudacion(String modalidad, String patronAbono, String abono) implements ConfiguracionConector {

		@Override
		public TipoConector tipo() {
			return TipoConector.RECAUDACION;
		}

		@Override
		public String resumen() {
			return ("CON_BASE_DE_DEUDAS".equals(modalidad) ? "Con base de deudas" : "Sin base de deudas") + "; abono "
					+ ("POR_PAGO".equals(abono) ? "pago por pago" : "por lote")
					+ (patronAbono == null || patronAbono.isBlank() ? "" : "; glosa «" + patronAbono + "»");
		}
	}
}
