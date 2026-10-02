package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Un plan en la tarjeta de su nivel y en el historial de versiones. */
public record PlanResumen(Long id, String nombre, int numeroVersion, String estado, String estadoEtiqueta,
		String estadoVariante, BigDecimal montoMatricula, BigDecimal montoPension, int pensiones,
		LocalDate primerVencimiento, LocalDate ultimoVencimiento, String cobroDesde, String editadoPor,
		String aprobadoPor, LocalDateTime aprobadoEn, String motivoCambio) {
}
