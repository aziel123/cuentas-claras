package pe.edu.virgenmaria.cuentasclaras.familias.dto;

import pe.edu.virgenmaria.cuentasclaras.matricula.dto.RenovacionFamilia;
import pe.edu.virgenmaria.cuentasclaras.pasarela.dto.CuentaEnLinea;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Inicio del portal (pantalla 1): la cuenta, la deuda vencida, el próximo vencimiento y las renovaciones. */
public record InicioFamilia(CuentaEnLinea cuenta, BigDecimal deudaVencida, Pendiente proximo,
		List<RenovacionFamilia> renovaciones) {

	public record Pendiente(String alumno, String descripcion, LocalDate vencimiento, BigDecimal saldo, boolean vencida) {
	}

	public boolean conDeudaVencida() {
		return deudaVencida.signum() > 0;
	}
}
