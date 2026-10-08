package pe.edu.virgenmaria.cuentasclaras.panel.dto;

import pe.edu.virgenmaria.cuentasclaras.caja.dto.AnuladoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.CobradoPeriodo;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.EstadoCajas;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.DeudaVencida;

import java.time.LocalDate;

/**
 * Las cifras de un día que comparten el panel y (tanda 2) el resumen diario, para que ambos digan lo mismo: lo cobrado
 * en el día y en el mes hasta ese día, lo anulado en el mes, las cajas del día y la deuda vencida.
 */
public record CifrasResumen(LocalDate fecha, CobradoPeriodo dia, CobradoPeriodo mes, AnuladoPeriodo anuladoMes,
		EstadoCajas cajas, DeudaVencida deuda) {
}
