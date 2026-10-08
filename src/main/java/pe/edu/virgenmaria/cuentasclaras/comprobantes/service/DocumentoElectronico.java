package pe.edu.virgenmaria.cuentasclaras.comprobantes.service;

import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.AfectacionIgv;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Comprobante;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.LineaDocumento;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.Receptor;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Lo que se envía al OSE: los datos tributarios del comprobante, sin entidades. */
public record DocumentoElectronico(TipoComprobante tipo, String serie, int numero, LocalDate fecha, Receptor receptor,
		String moneda, BigDecimal total, AfectacionIgv afectacion, List<LineaDocumento> lineas, Referencia modifica) {

	/** El comprobante que anula una nota de crédito (tanda 2). */
	public record Referencia(TipoComprobante tipo, String serie, int numero, String codigoMotivo) {
	}

	/** Catálogo 09 de SUNAT: 01 = anulación de la operación. */
	public static final String MOTIVO_ANULACION = "01";

	public static DocumentoElectronico de(Comprobante c) {
		return de(c, null);
	}

	/** {@code modificado}: el comprobante que anula una nota de crédito (null en boletas y facturas). */
	public static DocumentoElectronico de(Comprobante c, Comprobante modificado) {
		List<LineaDocumento> lineas = c.getLineas().stream()
				.map(l -> new LineaDocumento(l.getDescripcion(), l.getMonto())).toList();
		Referencia referencia = modificado == null ? null
				: new Referencia(modificado.getTipo(), modificado.getSerie(), modificado.getNumero(), MOTIVO_ANULACION);
		return new DocumentoElectronico(c.getTipo(), c.getSerie(), c.getNumero(), c.getFechaEmision(), c.getReceptor(),
				c.getMoneda(), c.getTotal(), c.getAfectacionIgv(), lineas, referencia);
	}
}
