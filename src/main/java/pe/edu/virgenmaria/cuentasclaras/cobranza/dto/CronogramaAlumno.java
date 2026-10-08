package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.CabeceraAlumno;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Cronograma de un alumno con los totales. Las anuladas no suman; {@code vencido} es el saldo de las cuotas
 * vencidas a la fecha {@code hoy} (Lima).
 */
public record CronogramaAlumno(CabeceraAlumno alumno, List<CuotaVista> cuotas, BigDecimal total, BigDecimal pagado,
		BigDecimal saldo, BigDecimal vencido, LocalDate hoy) {
}
