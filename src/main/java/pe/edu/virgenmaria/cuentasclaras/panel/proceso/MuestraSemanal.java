package pe.edu.virgenmaria.cuentasclaras.panel.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;
import pe.edu.virgenmaria.cuentasclaras.panel.model.MuestraLlamada;
import pe.edu.virgenmaria.cuentasclaras.panel.service.FijacionMuestra;
import pe.edu.virgenmaria.cuentasclaras.panel.service.FijadorMuestra;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * Fija la muestra de la llamada de control de la semana (sprint 7, tanda 2; sección 3.6 y decisión 87): el lunes a las
 * 00:10 (Lima), en cada colegio y como {@code sistema.panel}. Si esa tarea no corrió, la primera consulta de la semana se
 * la pide ({@link #asegurar}), siempre en una transacción PROPIA del actor (con la conexión de {@code cc_sistema}): ninguna
 * persona inserta la muestra, y nadie puede elegirla ni predecirla antes (la semilla efectiva se deriva con la clave del
 * servidor).
 */
@Component
public class MuestraSemanal implements FijadorMuestra {

	private static final Logger LOG = LoggerFactory.getLogger(MuestraSemanal.class);

	private final FijacionMuestra fijacion;

	private final RecorridoColegios colegios;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	public MuestraSemanal(FijacionMuestra fijacion, RecorridoColegios colegios, PlatformTransactionManager transacciones,
			Clock reloj) {
		this.fijacion = fijacion;
		this.colegios = colegios;
		this.transaccion = new TransactionTemplate(transacciones);
		this.reloj = reloj;
	}

	@Scheduled(cron = "${cuentasclaras.panel.muestra-semanal:0 10 0 * * MON}", zone = ConfiguracionTiempo.ZONA)
	public void ejecutar() {
		LocalDate semana = LocalDate.now(reloj).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		for (Long colegio : colegios.activos()) {
			try {
				int familias = asegurar(colegio, semana).size();
				LOG.info("Muestra de la llamada de control de la semana del {} fijada en el colegio {}: {} familia(s).",
						semana, colegio, familias);
			}
			catch (RuntimeException e) {
				LOG.error("No se pudo fijar la muestra de la semana en el colegio {}: {}", colegio,
						e.getClass().getSimpleName());
			}
		}
	}

	@Override
	public List<MuestraLlamada> asegurar(long colegioId, LocalDate semana) {
		return EjecucionComoSistema.como(ActorSistema.PANEL, colegioId,
				() -> transaccion.execute(estado -> fijacion.fijar(semana)));
	}
}
