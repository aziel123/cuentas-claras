package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.academico.curriculo.service.AperturaDeSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;

import java.util.List;

/**
 * Sprint 10, tanda 1 (sección 3.3): cada mañana (06:00 de Lima), en cada colegio, {@code sistema.academico} abre los
 * periodos cuyo inicio llegó. Primero el colegio y el actor, después la transacción; cada periodo en la suya y en orden:
 * si uno falla, queda en el log y la pasada siguiente lo retoma.
 */
@Component
public class AperturaPeriodos {

	private static final Logger LOG = LoggerFactory.getLogger(AperturaPeriodos.class);

	private final AperturaDeSistema apertura;

	private final RecorridoColegios colegios;

	private final TransactionTemplate transaccion;

	public AperturaPeriodos(AperturaDeSistema apertura, RecorridoColegios colegios, PlatformTransactionManager transacciones) {
		this.apertura = apertura;
		this.colegios = colegios;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Scheduled(cron = "${cuentasclaras.curriculo.apertura-periodos:0 0 6 * * *}", zone = ConfiguracionTiempo.ZONA)
	public void ejecutar() {
		for (Long colegio : colegios.activos()) {
			abrir(colegio);
		}
	}

	/** @return cuántos abrió en ese colegio */
	public int abrir(long colegioId) {
		List<Long> ids = EjecucionComoSistema.como(ActorSistema.ACADEMICO, colegioId,
				() -> transaccion.execute(t -> apertura.paraAbrir()));
		int abiertos = 0;
		for (Long id : ids) {
			try {
				Boolean abierto = EjecucionComoSistema.como(ActorSistema.ACADEMICO, colegioId,
						() -> transaccion.execute(t -> apertura.abrirEnSuFecha(id)));
				if (Boolean.TRUE.equals(abierto)) {
					abiertos++;
				}
			}
			catch (RuntimeException e) {
				LOG.error("No se pudo abrir el periodo {} en el colegio {}: {} (se reintentará)", id, colegioId,
						e.getClass().getSimpleName());
			}
		}
		return abiertos;
	}
}
