package pe.edu.virgenmaria.cuentasclaras.conciliacion.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.EstadoPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.PartidaConciliacion;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.model.ReglaPartida;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.repository.PartidaConciliacionRepository;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.Emparejador;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ExtractosConfirmados;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.PartidaConfirmada;
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.VerificacionesDePartidas;
import pe.edu.virgenmaria.cuentasclaras.recaudacion.service.LoteConfirmado;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Conciliación automática (sprint 4, tanda 3), como {@code sistema.conciliacion} (nunca una persona):
 * <ol>
 *   <li>vuelve a proponer parejas para lo que quedó sin pareja (un pago registrado tarde o un lote confirmado después
 *       pueden emparejar ahora);</li>
 *   <li>confirma las partidas EXACTAS de los extractos ya CONFIRMADOS a ciegas;</li>
 *   <li>deja la verificación bancaria AUTOMATICA de lo que cubren las partidas confirmadas (pagos directos, los pagos de
 *       cada liquidación y de cada lote, y los depósitos);</li>
 *   <li>audita el resumen ({@code CONCILIACION_AUTOMATICA}).</li>
 * </ol>
 * Corre después del commit de la confirmación de los extractos, de un lote de recaudación y de una partida confirmada
 * por una persona; como respaldo, cada 30 minutos en cada colegio. Cada pasada en su propia transacción.
 */
@Component
public class AplicadorConciliacion {

	private static final Logger LOG = LoggerFactory.getLogger(AplicadorConciliacion.class);

	/** Desde cuántos días atrás se buscan movimientos sin pareja y partidas confirmadas sin verificar. */
	static final int DIAS_ATRAS = 60;

	/** Qué hizo una pasada. */
	public record Resumen(int propuestas, int exactasConfirmadas, int verificaciones) {

		public boolean algo() {
			return propuestas + exactasConfirmadas + verificaciones > 0;
		}
	}

	private final PartidaConciliacionRepository partidas;

	private final Emparejador emparejador;

	private final VerificacionesDePartidas verificaciones;

	private final AuditoriaService auditoria;

	private final RecorridoColegios recorrido;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	public AplicadorConciliacion(PartidaConciliacionRepository partidas, Emparejador emparejador,
			VerificacionesDePartidas verificaciones, AuditoriaService auditoria, RecorridoColegios recorrido,
			PlatformTransactionManager transacciones, Clock reloj) {
		this.partidas = partidas;
		this.emparejador = emparejador;
		this.verificaciones = verificaciones;
		this.auditoria = auditoria;
		this.recorrido = recorrido;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.reloj = reloj;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alConfirmarExtractos(ExtractosConfirmados evento) {
		seguro(evento.colegioId(), "los extractos " + evento.extractos());
	}

	/** Después de que la recaudación aplicó los pagos del lote (que esta pasada verifica si su abono ya está). */
	@Order(Ordered.LOWEST_PRECEDENCE)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alConfirmarLote(LoteConfirmado evento) {
		seguro(evento.colegioId(), "el lote de recaudación " + evento.loteId());
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alConfirmarPartida(PartidaConfirmada evento) {
		seguro(evento.colegioId(), "la partida " + evento.partidaId());
	}

	@Scheduled(fixedDelayString = "${cuentasclaras.conciliacion.conciliar-cada:30m}",
			initialDelayString = "${cuentasclaras.conciliacion.conciliar-cada:30m}")
	public void conciliarPendientes() {
		recorrido.enCadaColegio(ActorSistema.CONCILIACION, colegio -> conciliarEnContexto());
	}

	/** Una pasada en ese colegio como {@code sistema.conciliacion}. */
	public Resumen conciliar(long colegioId) {
		return EjecucionComoSistema.como(ActorSistema.CONCILIACION, colegioId, this::conciliarEnContexto);
	}

	private void seguro(Long colegioId, String que) {
		try {
			conciliar(colegioId);
		}
		catch (RuntimeException e) {
			LOG.error("No se pudo conciliar después de confirmar {}: {} (se reintentará)", que, e.getClass().getSimpleName());
		}
	}

	private Resumen conciliarEnContexto() {
		return Objects.requireNonNull(transaccion.execute(e -> {
			LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
			LocalDate desde = ahora.toLocalDate().minusDays(DIAS_ATRAS);
			int propuestas = emparejador.proponerDesde(desde);
			int exactas = 0;
			for (PartidaConciliacion p : partidas.propuestasConfirmables(ReglaPartida.EXACTA)) {
				p.confirmar(ActorSistema.CONCILIACION.usuario(), ahora);
				// La verificación automática (trigger) lee la partida ya CONFIRMADA: primero se escribe.
				partidas.saveAndFlush(p);
				exactas++;
			}
			int verificadas = 0;
			for (PartidaConciliacion p : partidas.findByEstadoAndResueltoEnAfterOrderByIdAsc(EstadoPartida.CONFIRMADA,
					ahora.minusDays(DIAS_ATRAS))) {
				verificadas += verificaciones.verificar(p);
			}
			Resumen resumen = new Resumen(propuestas, exactas, verificadas);
			if (resumen.algo()) {
				auditoria.registrar(AccionAuditoria.CONCILIACION_AUTOMATICA, "partida_conciliacion", null, null,
						exactas + " exactas · " + propuestas + " propuestas · " + verificadas + " verificaciones",
						"El sistema concilió con el extracto: " + exactas + " pareja(s) exacta(s) confirmada(s) (misma "
								+ "operación y monto), " + propuestas + " pareja(s) propuesta(s) y " + verificadas
								+ " pago(s) o depósito(s) verificados en el banco. Las sugeridas las confirma una persona "
								+ "que no cobró ni depositó.");
			}
			return resumen;
		}));
	}
}
