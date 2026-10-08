package pe.edu.virgenmaria.cuentasclaras.caja.dto;

import pe.edu.virgenmaria.cuentasclaras.caja.model.CanalCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.comprobantes.model.TipoComprobante;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una fila del Excel para el contador (sprint 6, sección 10.3, Ley 29733): solo lo que necesita la contabilidad.
 * <ul>
 *   <li>La familia va por su CÓDIGO (el id), nunca por apellido;</li>
 *   <li>sin DNI, nombres de alumnos o apoderados, celulares, correos, direcciones ni secciones;</li>
 *   <li>el RUC solo en facturas (lo trae la consulta; nunca el documento de una boleta);</li>
 *   <li>los conceptos son los textos que genera el sistema («Pensión marzo 2027»); el saldo inicial, texto fijo.</li>
 * </ul>
 */
public record PagoExportable(LocalDate fecha, String comprobante, TipoComprobante tipo, MedioPago medio,
		CanalCaja canal, String numeroOperacion, BigDecimal total, EstadoPago estado, LocalDate fechaNotaCredito,
		String notaCredito, String conceptos, Long familiaId, String registradoPor, String ruc) {
}
