package pe.edu.virgenmaria.cuentasclaras.panel.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;
import pe.edu.virgenmaria.cuentasclaras.panel.service.FijacionMuestra;
import pe.edu.virgenmaria.cuentasclaras.panel.service.SegundoNoContesta;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Reemplaza a la familia que no contestó dos veces (sprint 7, tanda 2; S6-M2 con la sección 3.6): lo hace
 * {@code sistema.panel} después del commit del segundo «No contesta» (una transacción PROPIA, con la conexión de
 * {@code cc_sistema}). Si eso falla, lo reintenta la pasada de cada 15 minutos en cada colegio.
 */
@Component
public class ReemplazosLlamadas {

	private static final Logger LOG = LoggerFactory.getLogger(ReemplazosLlamadas.class);

	private final FijacionMuestra fijacion;

	private final RecorridoColegios colegios;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	public ReemplazosLlamadas(FijacionMuestra fijacion, RecorridoColegios colegios,
			PlatformTransactionManager transacciones, Clock reloj) {
		this.fijacion = fijacion;
		this.colegios = colegios;
		this.transaccion = new TransactionTemplate(transacciones);
		this.reloj = reloj;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alSegundoNoContesta(SegundoNoContesta evento) {
		try {
			EjecucionComoSistema.como(ActorSistema.PANEL, evento.colegioId(),
					() -> transaccion.execute(estado -> fijacion.reemplazar(evento.semana(), evento.familiaId())));
		}
		catch (RuntimeException e) {
			// La llamada ya quedó registrada; el reemplazo lo reintenta la pasada de cada 15 minutos.
			LOG.error("No se pudo reemplazar a la familia {} en el colegio {}: {} (se reintentará)", evento.familiaId(),
					evento.colegioId(), e.getClass().getSimpleName());
		}
	}

	@Scheduled(cron = "${cuentasclaras.panel.reemplazos-cada:0 */15 * * * *}", zone = ConfiguracionTiempo.ZONA)
	public void reintentar() {
		LocalDate semana = LocalDate.now(reloj).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		colegios.enCadaColegio(ActorSistema.PANEL, colegio -> {
			Integer hechos = transaccion.execute(estado -> fijacion.reemplazarPendientes(semana));
			if (hechos != null && hechos > 0) {
				LOG.info("Se reemplazaron {} familia(s) de la llamada de control en el colegio {}.", hechos, colegio);
			}
		});
	}
}
