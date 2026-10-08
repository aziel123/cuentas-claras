package pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso;

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
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.ServicioRecordatorios;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Sprint 5, tanda 3: de lunes a sábado a las 08:00 (Lima), como {@code sistema.mensajeria}, prepara los recordatorios
 * de vencimiento de cada colegio en su propia transacción. Los envía {@link DespachoMensajes}, solo dentro de la
 * ventana de 08:00 a 20:00 y nunca en domingo ni feriado. Se puede apagar con
 * {@code cuentasclaras.recordatorios.activos=false}.
 */
@Component
public class Recordatorios {

	private static final Logger LOG = LoggerFactory.getLogger(Recordatorios.class);

	private final ServicioRecordatorios servicio;

	private final RecorridoColegios colegios;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	private final boolean activos;

	public Recordatorios(ServicioRecordatorios servicio, RecorridoColegios colegios,
			PlatformTransactionManager transacciones, Clock reloj,
			@org.springframework.beans.factory.annotation.Value("${cuentasclaras.recordatorios.activos:true}") boolean activos) {
		this.servicio = servicio;
		this.colegios = colegios;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.reloj = reloj;
		this.activos = activos;
	}

	@Scheduled(cron = "${cuentasclaras.recordatorios.cron:0 0 8 * * MON-SAT}", zone = ConfiguracionTiempo.ZONA)
	public void ejecutar() {
		if (!activos) {
			return;
		}
		LocalDate hoy = LocalDate.now(reloj);
		for (Long colegio : colegios.activos()) {
			try {
				enColegio(colegio, hoy);
			}
			catch (RuntimeException e) {
				LOG.error("Los recordatorios fallaron en el colegio {}: {}", colegio, e.getClass().getSimpleName());
			}
		}
	}

	/** @return cuántos mensajes quedaron preparados en el colegio */
	public int enColegio(Long colegioId, LocalDate hoy) {
		Integer creados = EjecucionComoSistema.como(ActorSistema.MENSAJERIA, colegioId,
				() -> transaccion.execute(t -> servicio.preparar(hoy)));
		return creados == null ? 0 : creados;
	}
}
