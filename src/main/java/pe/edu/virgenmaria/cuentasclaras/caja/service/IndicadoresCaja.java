package pe.edu.virgenmaria.cuentasclaras.caja.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CajaDiaria;
import pe.edu.virgenmaria.cuentasclaras.caja.model.CierreCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.EstadoCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.model.Pago;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.CajaDiariaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.CierreCajaRepository;
import pe.edu.virgenmaria.cuentasclaras.caja.repository.PagoRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.IndicadoresInicio;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Resumen del día de caja en el inicio de Promotoría: cobrado hoy (pagos VIGENTES), cuánto en efectivo y cuánto
 * digital (el sistema empuja a lo digital) y cuántas cajas están abiertas o cerradas, y cuántas con diferencia.
 */
@Service
@org.springframework.core.annotation.Order(1)
@Transactional(readOnly = true)
@PreAuthorize("hasRole('PROMOTOR')")
public class IndicadoresCaja implements IndicadoresInicio {

	private final PagoRepository pagos;

	private final CajaDiariaRepository cajas;

	private final CierreCajaRepository cierres;

	private final Clock reloj;

	public IndicadoresCaja(PagoRepository pagos, CajaDiariaRepository cajas, CierreCajaRepository cierres, Clock reloj) {
		this.pagos = pagos;
		this.cajas = cajas;
		this.cierres = cierres;
		this.reloj = reloj;
	}

	@Override
	public String titulo() {
		return "Hoy en caja · " + Calendario.formatear(LocalDate.now(reloj));
	}

	@Override
	public List<Indicador> indicadores() {
		LocalDate hoy = LocalDate.now(reloj);
		List<Pago> vigentes = pagos.findByFechaOrderByIdAsc(hoy).stream().filter(Pago::vigente).toList();
		List<Pago> efectivo = vigentes.stream().filter(p -> p.getMedio() == MedioPago.EFECTIVO).toList();
		BigDecimal total = Dinero.sumar(vigentes.stream().map(Pago::getTotal).toList());
		BigDecimal enEfectivo = Dinero.sumar(efectivo.stream().map(Pago::getTotal).toList());
		BigDecimal digital = total.subtract(enEfectivo);
		long porcentaje = total.signum() == 0 ? 0
				: digital.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP).longValue();
		List<CajaDiaria> delDia = cajas.findByCanalAndFechaOrderByCajeroAsc(
				pe.edu.virgenmaria.cuentasclaras.caja.model.CanalCaja.VENTANILLA, hoy);
		long abiertas = delDia.stream().filter(c -> c.getEstado() == EstadoCaja.ABIERTA).count();
		long cerradas = delDia.size() - abiertas;
		long conDiferencia = delDia.isEmpty() ? 0
				: cierres.findByCajaIdInOrderByNumeroAsc(delDia.stream().map(CajaDiaria::getId).toList()).stream()
						.filter(CierreCaja::conDiferencia).count();
		List<Pago> enLinea = vigentes.stream().filter(p -> p.getCaja().esDeCanal()).toList();
		return List.of(
				new Indicador("Cobrado hoy", Dinero.formatear(total), vigentes.size() + " pago(s)"),
				new Indicador("Entró solo (en línea y por banco)", Dinero.formatear(Dinero.sumar(enLinea.stream()
						.map(Pago::getTotal).toList())), enLinea.size() + " pago(s) sin que nadie digite"),
				new Indicador("Efectivo", Dinero.formatear(enEfectivo), efectivo.size() + " pago(s)"),
				new Indicador("Digital", Dinero.formatear(Dinero.normalizar(digital)), porcentaje + " % del total"),
				new Indicador("Cajas", abiertas + " abierta(s) · " + cerradas + " cerrada(s)",
						conDiferencia == 0 ? "Sin diferencias" : conDiferencia + " cierre(s) con diferencia"));
	}

	@Override
	public String enlace() {
		return "/aprobaciones/cajas";
	}

	@Override
	public String textoEnlace() {
		return "Ver cajas del día";
	}
}
