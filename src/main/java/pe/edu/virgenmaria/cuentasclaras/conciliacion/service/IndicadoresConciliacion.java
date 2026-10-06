package pe.edu.virgenmaria.cuentasclaras.conciliacion.service;

import org.springframework.core.annotation.Order;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.IndicadoresInicio;
import pe.edu.virgenmaria.cuentasclaras.comun.dinero.Dinero;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.Calendario;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.MovimientoBancario;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.TipoMovimiento;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.MovimientoBancarioRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Resumen de la conciliación en el inicio de Promotoría: hasta qué día está confirmado el banco, qué parte de sus
 * movimientos (últimos 30 días) ya tiene pareja, y cuántas diferencias quedan (sugeridas, abonos sin pareja y lo que
 * debía estar en el banco y no está).
 */
@Service
@Order(2)
@Transactional(readOnly = true)
@PreAuthorize("hasRole('PROMOTOR')")
public class IndicadoresConciliacion implements IndicadoresInicio {

	private final DiferenciasConciliacion diferencias;

	private final MovimientoBancarioRepository movimientos;

	private final Clock reloj;

	public IndicadoresConciliacion(DiferenciasConciliacion diferencias, MovimientoBancarioRepository movimientos,
			Clock reloj) {
		this.diferencias = diferencias;
		this.movimientos = movimientos;
		this.reloj = reloj;
	}

	@Override
	public String titulo() {
		return diferencias.cobertura().map(c -> "Conciliación con el banco · confirmado hasta el "
				+ Calendario.formatear(c.hasta())).orElse("Conciliación con el banco · sin extractos confirmados");
	}

	@Override
	public List<Indicador> indicadores() {
		LocalDate hoy = LocalDate.now(reloj);
		LocalDate desde = hoy.minusDays(30);
		long total = movimientos.confirmadosDesde(desde);
		long conciliados = movimientos.conciliadosDesde(desde);
		long porcentaje = total == 0 ? 100 : conciliados * 100 / total;
		List<DiferenciasConciliacion.Faltante> faltantes = diferencias.faltantes(hoy);
		List<MovimientoBancario> abonos = diferencias.sinPareja(hoy).stream()
				.filter(m -> m.getTipo() == TipoMovimiento.ABONO).toList();
		Optional<DiferenciasConciliacion.Cobertura> cobertura = diferencias.cobertura();
		return List.of(
				new Indicador("Conciliado (30 días)", porcentaje + " %", conciliados + " de " + total + " movimiento(s)"),
				new Indicador("Debía estar en el banco y no está", String.valueOf(faltantes.size()),
						faltantes.isEmpty() ? "Nada" : Dinero.formatear(Dinero.sumar(faltantes.stream()
								.map(f -> f.objeto().monto()).toList()))),
				new Indicador("Abonos sin pareja", String.valueOf(abonos.size()), abonos.isEmpty() ? "Nada"
						: Dinero.formatear(Dinero.sumar(abonos.stream().map(MovimientoBancario::getMonto).toList()))),
				new Indicador("Sugeridas por confirmar", String.valueOf(diferencias.sugeridas().size()),
						cobertura.isEmpty() ? "Sube y confirma el primer extracto" : "Las confirma Administración"));
	}

	@Override
	public String enlace() {
		return "/conciliacion";
	}
}
