package pe.edu.virgenmaria.cuentasclaras.comprobantes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.AfectacionIgv;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ProveedorComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.SerieComprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;

import java.time.Duration;

/**
 * {@code cuentasclaras.comprobantes}: proveedor (SIMULADO hasta conectar el OSE), afectación al IGV, series y, desde el
 * sprint 4, el outbox del OSE (plazo legal, alertas y espera creciente de los reintentos) y la cuenta de Nubefact (sus
 * secretos solo por variable de entorno). Por ahora es global; con un segundo colegio pasa a una tabla de configuración
 * por colegio. Una serie mal escrita o un plazo imposible impide arrancar.
 */
@ConfigurationProperties("cuentasclaras.comprobantes")
public record PropiedadesComprobantes(
		@DefaultValue("SIMULADO") ProveedorComprobantes proveedor,
		@DefaultValue("INAFECTO") AfectacionIgv afectacionIgv,
		@DefaultValue("B001") String serieBoleta,
		@DefaultValue("F001") String serieFactura,
		@DefaultValue("BC01") String serieNotaBoleta,
		@DefaultValue("FC01") String serieNotaFactura,
		@DefaultValue("false") boolean permitirRealFueraDeProd,
		@DefaultValue Nubefact nubefact,
		@DefaultValue("3") int plazoEnvioDias,
		@DefaultValue("4") int alertaHorasSinAceptar,
		@DefaultValue("1m") Duration reintentoInicial,
		@DefaultValue("60m") Duration reintentoMaximo,
		@DefaultValue("5m") Duration consultaCada) {

	/** Cuenta del colegio en Nubefact: la RUTA y el TOKEN llegan por variable de entorno (NUBEFACT_RUTA, NUBEFACT_TOKEN). */
	public record Nubefact(String ruta, String token, @DefaultValue("api.nubefact.com") String dominiosPermitidos,
			@DefaultValue("5s") Duration tiempoConexion, @DefaultValue("20s") Duration tiempoLectura) {

		@Override
		public String toString() {
			// Sin el token: este texto puede terminar en un log.
			return "Nubefact[ruta=" + ruta + ", dominios=" + dominiosPermitidos + "]";
		}
	}

	@ConstructorBinding
	public PropiedadesComprobantes {
		exigir(TipoComprobante.BOLETA, serieBoleta, 'B', "serie-boleta");
		exigir(TipoComprobante.FACTURA, serieFactura, 'F', "serie-factura");
		exigir(TipoComprobante.NOTA_CREDITO, serieNotaBoleta, 'B', "serie-nota-boleta");
		exigir(TipoComprobante.NOTA_CREDITO, serieNotaFactura, 'F', "serie-nota-factura");
		if (plazoEnvioDias < 1 || plazoEnvioDias > 7) {
			throw new IllegalArgumentException("cuentasclaras.comprobantes.plazo-envio-dias debe estar entre 1 y 7");
		}
		if (alertaHorasSinAceptar < 1) {
			throw new IllegalArgumentException("cuentasclaras.comprobantes.alerta-horas-sin-aceptar debe ser al menos 1");
		}
		if (reintentoInicial == null || reintentoMaximo == null || reintentoInicial.isNegative() || reintentoInicial.isZero()
				|| reintentoMaximo.compareTo(reintentoInicial) < 0) {
			throw new IllegalArgumentException("cuentasclaras.comprobantes: el reintento inicial debe ser positivo y no "
					+ "mayor que el máximo");
		}
		if (nubefact == null) {
			nubefact = new Nubefact(null, null, "api.nubefact.com", Duration.ofSeconds(5), Duration.ofSeconds(20));
		}
		if (consultaCada == null || consultaCada.isNegative() || consultaCada.isZero()) {
			consultaCada = Duration.ofMinutes(5);
		}
	}

	/** Las propiedades del sprint 3 con los valores por defecto del outbox (pruebas y quien no las configura). */
	public PropiedadesComprobantes(ProveedorComprobantes proveedor, AfectacionIgv afectacionIgv, String serieBoleta,
			String serieFactura, String serieNotaBoleta, String serieNotaFactura) {
		this(proveedor, afectacionIgv, serieBoleta, serieFactura, serieNotaBoleta, serieNotaFactura, false, null, 3, 4,
				Duration.ofMinutes(1), Duration.ofMinutes(60), Duration.ofMinutes(5));
	}

	private static void exigir(TipoComprobante tipo, String serie, char letra, String nombre) {
		if (!SerieComprobante.formatoValido(tipo, serie) || serie.charAt(0) != letra) {
			throw new IllegalArgumentException("cuentasclaras.comprobantes." + nombre + ": «" + serie
					+ "» no es una serie válida (4 caracteres que empiezan con " + letra + ")");
		}
	}

	/** Serie con la que se emite una boleta o una factura. */
	public String serieDe(TipoComprobante tipo) {
		return switch (tipo) {
			case BOLETA -> serieBoleta;
			case FACTURA -> serieFactura;
			case NOTA_CREDITO -> throw new IllegalArgumentException("La serie de una nota depende del comprobante");
		};
	}

	/** Espera antes del intento siguiente: inicial × 2^(intentos−1), sin pasar del máximo (1, 2, 4… hasta 60 min). */
	public Duration esperaTrasIntentos(int intentos) {
		int exponente = Math.max(0, Math.min(intentos - 1, 20));
		Duration espera = reintentoInicial.multipliedBy(1L << exponente);
		return espera.compareTo(reintentoMaximo) > 0 ? reintentoMaximo : espera;
	}
}
