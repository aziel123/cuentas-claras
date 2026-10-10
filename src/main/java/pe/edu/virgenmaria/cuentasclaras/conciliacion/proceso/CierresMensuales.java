package pe.edu.virgenmaria.cuentasclaras.conciliacion.proceso;

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
import pe.edu.virgenmaria.cuentasclaras.conciliacion.service.ServicioCierreMensual;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.PasadaPorColegios;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Sprint 5, tanda 3: cada día a las 07:00 (Lima), como {@code sistema.conciliacion}, crea el cierre bancario del mes
 * anterior en cuanto los extractos CONFIRMADOS lo cubren completo (normalmente el día 1 o 2; si faltan extractos, lo
 * intenta cada día y Promotoría ve la alerta «mes sin extractos completos»).
 */
@Component
public class CierresMensuales {

	private static final Logger LOG = LoggerFactory.getLogger(CierresMensuales.class);

	private final ServicioCierreMensual servicio;

	private final RecorridoColegios colegios;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	public CierresMensuales(ServicioCierreMensual servicio, RecorridoColegios colegios,
			PlatformTransactionManager transacciones, Clock reloj) {
		this.servicio = servicio;
		this.colegios = colegios;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.reloj = reloj;
	}

	@Scheduled(cron = "${cuentasclaras.conciliacion.cierre-mensual-cron:0 0 7 * * *}", zone = ConfiguracionTiempo.ZONA)
	public void ejecutar() {
		LocalDate hoy = LocalDate.now(reloj);
		PasadaPorColegios.recorrer(LOG, "El cierre mensual", colegios.activos(), colegio -> enColegio(colegio, hoy));
	}

	/** @return cuántos cierres creó en el colegio */
	public int enColegio(Long colegioId, LocalDate hoy) {
		Integer creados = EjecucionComoSistema.como(ActorSistema.CONCILIACION, colegioId,
				() -> transaccion.execute(t -> servicio.crearPendientes(hoy)));
		return creados == null ? 0 : creados;
	}
}
