package pe.edu.virgenmaria.cuentasclaras.comprobantes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.AfectacionIgv;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.ProveedorComprobantes;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.SerieComprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;

/**
 * {@code cuentasclaras.comprobantes}: proveedor (SIMULADO hasta conectar el OSE), afectación al IGV y series. Por ahora
 * es global; con un segundo colegio pasa a una tabla de configuración por colegio. Una serie mal escrita impide
 * arrancar.
 */
@ConfigurationProperties("cuentasclaras.comprobantes")
public record PropiedadesComprobantes(
		@DefaultValue("SIMULADO") ProveedorComprobantes proveedor,
		@DefaultValue("INAFECTO") AfectacionIgv afectacionIgv,
		@DefaultValue("B001") String serieBoleta,
		@DefaultValue("F001") String serieFactura,
		@DefaultValue("BC01") String serieNotaBoleta,
		@DefaultValue("FC01") String serieNotaFactura) {

	public PropiedadesComprobantes {
		exigir(TipoComprobante.BOLETA, serieBoleta, 'B', "serie-boleta");
		exigir(TipoComprobante.FACTURA, serieFactura, 'F', "serie-factura");
		exigir(TipoComprobante.NOTA_CREDITO, serieNotaBoleta, 'B', "serie-nota-boleta");
		exigir(TipoComprobante.NOTA_CREDITO, serieNotaFactura, 'F', "serie-nota-factura");
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
}
