package pe.edu.virgenmaria.cuentasclaras.academico.libreta.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.EmisorLibretas;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.PasadaPorColegios;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Sprint 10, tanda 3 (sección 3.7): cada 5 minutos, en cada colegio activo (también el que está en preparación: las
 * libretas se emiten pero no se avisan, sección 3.12), {@code sistema.libretas} emite lo pendiente de los periodos
 * CERRADOS ({@link EmisorLibretas}): primero el colegio y el actor (la conexión de {@code cc_sistema}), después la
 * transacción; cada sección en la suya: si una falla, queda en el log y la pasada siguiente la retoma.
 */
@Component
public class EmisionLibretas {

	private static final Logger LOG = LoggerFactory.getLogger(EmisionLibretas.class);

	private final EmisorLibretas emisor;

	private final RecorridoColegios colegios;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	public EmisionLibretas(EmisorLibretas emisor, RecorridoColegios colegios, PlatformTransactionManager transacciones,
			Clock reloj) {
		this.emisor = emisor;
		this.colegios = colegios;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.reloj = reloj;
	}

	@Scheduled(cron = "${cuentasclaras.libretas.emision-cada:0 */5 * * * *}", zone = ConfiguracionTiempo.ZONA)
	public void ejecutar() {
		PasadaPorColegios.recorrer(LOG, "La emisión de libretas", colegios.activos(), this::enColegio);
	}

	/** La pasada de un colegio (la usan también las pruebas y la demo). */
	public EmisorLibretas.Resultado enColegio(long colegioId) {
		List<EmisorLibretas.Pendiente> pendientes = EjecucionComoSistema.como(ActorSistema.LIBRETAS, colegioId,
				() -> transaccion.execute(t -> emisor.pendientes()));
		EmisorLibretas.Resultado total = new EmisorLibretas.Resultado(0, 0, 0);
		int fallas = 0;
		for (EmisorLibretas.Pendiente p : pendientes) {
			LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
			try {
				total = total.sumar(EjecucionComoSistema.como(ActorSistema.LIBRETAS, colegioId,
						() -> transaccion.execute(t -> emisor.emitir(p.periodoId(), p.seccionId(), ahora))));
			}
			catch (RuntimeException e) {
				fallas++;
				LOG.error("No se pudieron emitir las libretas del periodo {} y la sección {} en el colegio {}: {} (se "
						+ "reintentará)", p.periodoId(), p.seccionId(), colegioId, e.getClass().getSimpleName());
			}
		}
		if (fallas > 0) {
			throw new IllegalStateException(fallas + " sección(es) sin emitir en el colegio " + colegioId);
		}
		return total;
	}
}
