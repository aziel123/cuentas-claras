package pe.edu.virgenmaria.cuentasclaras.comun.alertas;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Sprint 6, tanda 2: la foto del resumen diario quedó guardada. La mensajería crea, en la MISMA transacción, un mensaje
 * RESUMEN_DIARIO a cada persona de Promotoría activa y, si el DBA lo configuró, al correo externo del contador. Sin
 * mensaje no hay foto, y al revés. Vive en {@code comun} para que {@code comunicacion} no dependa del panel.
 *
 * @param parametros los 11 parámetros de la plantilla cc_resumen_diario (solo cifras, fechas y la huella)
 */
public record ResumenDiarioListo(long colegioId, long resumenId, LocalDate fecha, List<String> parametros) {

	public ResumenDiarioListo {
		Objects.requireNonNull(fecha, "fecha");
		parametros = List.copyOf(parametros);
	}
}
