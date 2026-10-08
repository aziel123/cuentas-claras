package pe.edu.virgenmaria.cuentasclaras.panel.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.caja.service.CifrasCaja;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.CifrasCobranza;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.CifrasResumen;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Las cifras de un día (sprint 6): las usan el panel y, desde la tanda 2, el resumen diario, para que ambos digan lo
 * mismo. Solo combina los puertos de caja y cobranza; no usa repositorios de otros módulos.
 * (En la tanda 2 se agrega el actor {@code SISTEMA_PANEL} a la expresión de permisos.)
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('PROMOTOR')")
public class CifrasDelDia {

	private final CifrasCaja caja;

	private final CifrasCobranza cobranza;

	public CifrasDelDia(CifrasCaja caja, CifrasCobranza cobranza) {
		this.caja = caja;
		this.cobranza = cobranza;
	}

	public CifrasResumen calcular(LocalDate fecha) {
		Objects.requireNonNull(fecha, "fecha");
		LocalDate inicioMes = fecha.withDayOfMonth(1);
		return new CifrasResumen(fecha, caja.cobrado(fecha, fecha), caja.cobrado(inicioMes, fecha),
				caja.anulado(inicioMes, fecha), caja.cajas(fecha), cobranza.deudaVencida(fecha));
	}
}
