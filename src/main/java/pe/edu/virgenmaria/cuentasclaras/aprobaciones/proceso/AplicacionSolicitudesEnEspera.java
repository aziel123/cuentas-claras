package pe.edu.virgenmaria.cuentasclaras.aprobaciones.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;

import java.util.List;

/**
 * Sprint 11, tanda 1c (A1c-2 y A1c-3, decisión 223): cada 15 minutos, en cada colegio que opera, {@code sistema.roles}
 * ({@code cc_sistema}) aplica las directas de Promotoría cuya espera de 24 horas venció sin freno: dar Promotoría en
 * producción, quitarla y desactivar a una Promotoría. Cada una en su transacción: si una falla, queda en el log y la pasada
 * siguiente la retoma.
 */
@Component
public class AplicacionSolicitudesEnEspera {

	private static final Logger LOG = LoggerFactory.getLogger(AplicacionSolicitudesEnEspera.class);

	private final BandejaAprobaciones bandeja;

	private final RecorridoColegios colegios;

	private final TransactionTemplate transaccion;

	public AplicacionSolicitudesEnEspera(BandejaAprobaciones bandeja, RecorridoColegios colegios,
			PlatformTransactionManager transacciones) {
		this.bandeja = bandeja;
		this.colegios = colegios;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Scheduled(cron = "${cuentasclaras.directa.aplicacion-cron:0 */15 * * * *}", zone = ConfiguracionTiempo.ZONA)
	public void ejecutar() {
		for (Long colegio : colegios.activos()) {
			aplicar(colegio);
		}
	}

	/** @return cuántas aplicó en ese colegio (la usan también las pruebas) */
	public int aplicar(long colegioId) {
		List<Long> ids = EjecucionComoSistema.como(ActorSistema.ROLES, colegioId,
				() -> transaccion.execute(t -> bandeja.enEsperaVencidas()));
		int hechas = 0;
		for (Long id : ids) {
			try {
				Boolean aplicada = EjecucionComoSistema.como(ActorSistema.ROLES, colegioId,
						() -> transaccion.execute(t -> bandeja.aplicarAlVencer(id)));
				if (Boolean.TRUE.equals(aplicada)) {
					hechas++;
				}
			}
			catch (RuntimeException e) {
				LOG.error("No se pudo aplicar la solicitud {} en espera del colegio {}: {} (se reintentará)", id, colegioId,
						e.getClass().getSimpleName());
			}
		}
		return hechas;
	}
}
