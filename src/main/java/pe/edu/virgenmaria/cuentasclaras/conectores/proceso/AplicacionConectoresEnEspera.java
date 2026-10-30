package pe.edu.virgenmaria.cuentasclaras.conectores.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;
import pe.edu.virgenmaria.cuentasclaras.conectores.service.EsperaConectores;

import java.util.List;

/**
 * Sprint 11, tanda 1c (decisión 218): cada 15 minutos, en cada colegio que opera, {@code sistema.conectores}
 * ({@code cc_sistema}) pone en vigencia la cuenta bancaria, la pasarela o la recaudación que aprobó directo una Promotoría
 * cuando vencen sus 24 horas de espera sin freno. Cada una en su transacción.
 */
@Component
public class AplicacionConectoresEnEspera {

	private static final Logger LOG = LoggerFactory.getLogger(AplicacionConectoresEnEspera.class);

	private final EsperaConectores espera;

	private final RecorridoColegios colegios;

	private final TransactionTemplate transaccion;

	public AplicacionConectoresEnEspera(EsperaConectores espera, RecorridoColegios colegios,
			PlatformTransactionManager transacciones) {
		this.espera = espera;
		this.colegios = colegios;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Scheduled(cron = "${cuentasclaras.directa.conectores-cron:0 */15 * * * *}", zone = ConfiguracionTiempo.ZONA)
	public void ejecutar() {
		for (Long colegio : colegios.activos()) {
			aplicar(colegio);
		}
	}

	/** @return cuántos puso en vigencia en ese colegio (la usan también las pruebas) */
	public int aplicar(long colegioId) {
		List<Long> ids = EjecucionComoSistema.como(ActorSistema.CONECTORES, colegioId,
				() -> transaccion.execute(t -> espera.vencidos()));
		int hechos = 0;
		for (Long id : ids) {
			try {
				Boolean aplicado = EjecucionComoSistema.como(ActorSistema.CONECTORES, colegioId,
						() -> transaccion.execute(t -> espera.aplicarAlVencer(id)));
				if (Boolean.TRUE.equals(aplicado)) {
					hechos++;
				}
			}
			catch (RuntimeException e) {
				LOG.error("No se pudo aplicar el conector {} en espera del colegio {}: {} (se reintentará)", id, colegioId,
						e.getClass().getSimpleName());
			}
		}
		return hechos;
	}
}
