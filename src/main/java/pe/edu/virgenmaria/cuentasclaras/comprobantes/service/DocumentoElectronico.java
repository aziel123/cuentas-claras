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

	public static DocumentoElectronico de(Comprobante c) {
		List<LineaDocumento> lineas = c.getLineas().stream()
				.map(l -> new LineaDocumento(l.getDescripcion(), l.getMonto())).toList();
		return new DocumentoElectronico(c.getTipo(), c.getSerie(), c.getNumero(), c.getFechaEmision(), c.getReceptor(),
				c.getMoneda(), c.getTotal(), c.getAfectacionIgv(), lineas, null);
	}
}
