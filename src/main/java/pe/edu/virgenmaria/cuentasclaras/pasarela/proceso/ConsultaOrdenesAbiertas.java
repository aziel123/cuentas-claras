package pe.edu.virgenmaria.cuentasclaras.pasarela.proceso;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EstadoOrden;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.OrdenPago;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.EventoPasarelaRepository;
import pe.edu.virgenmaria.cuentasclaras.pasarela.repository.OrdenPagoRepository;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EstadoEvento;
import pe.edu.virgenmaria.cuentasclaras.pasarela.model.EventoPasarela;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Respaldo si un aviso se pierde (cada 2 minutos, en cada colegio, como {@code sistema.pasarela}): consulta las órdenes
 * en curso de más de un minuto (y las vence si su vigencia pasó), las vencidas de las últimas 24 horas por si llega un
 * pago tardío, y reintenta los avisos que fallaron.
 */
@Component
public class ConsultaOrdenesAbiertas {

	private final OrdenPagoRepository ordenes;

	private final EventoPasarelaRepository eventos;

	private final ProcesadorPagosEnLinea procesador;

	private final RecorridoColegios colegios;

	private final AplicadorIngresos aplicador;

	private final Clock reloj;

	public ConsultaOrdenesAbiertas(OrdenPagoRepository ordenes, EventoPasarelaRepository eventos,
			ProcesadorPagosEnLinea procesador, AplicadorIngresos aplicador, RecorridoColegios colegios, Clock reloj) {
		this.aplicador = aplicador;
		this.ordenes = ordenes;
		this.eventos = eventos;
		this.procesador = procesador;
		this.colegios = colegios;
		this.reloj = reloj;
	}

	@Scheduled(fixedDelayString = "${cuentasclaras.pasarela.consulta-ordenes-cada:2m}", initialDelayString = "45s")
	public void consultar() {
		colegios.enCadaColegio(ActorSistema.PASARELA, colegio -> consultarColegio(colegio));
	}

	/** Una pasada en el colegio (con el actor y el colegio ya fijados). @return cuántas órdenes consultó */
	public int consultarColegio(long colegio) {
		LocalDateTime ahora = LocalDateTime.now(reloj);
		List<Long> porConsultar = new ArrayList<>(ids(ordenes.findByEstadoAndCreadoEnBeforeOrderByIdAsc(EstadoOrden.CREADA,
				ahora.minusMinutes(1))));
		ids(ordenes.findByEstadoAndVenceEnAfterOrderByIdAsc(EstadoOrden.VENCIDA, ahora.minusHours(24))).stream()
				.filter(id -> !porConsultar.contains(id)).forEach(porConsultar::add);
		porConsultar.forEach(procesador::procesarEnContexto);
		for (EventoPasarela evento : eventos.findByEstadoOrderByIdAsc(EstadoEvento.RECIBIDO)) {
			if (evento.getCreadoEn() != null && evento.getCreadoEn().isBefore(ahora.minusMinutes(1))) {
				procesador.procesarEvento(colegio, evento.getId());
			}
		}
		aplicador.aplicarPendientes();
		return porConsultar.size();
	}

	private static List<Long> ids(List<OrdenPago> lista) {
		return lista.stream().map(OrdenPago::getId).toList();
	}
}
