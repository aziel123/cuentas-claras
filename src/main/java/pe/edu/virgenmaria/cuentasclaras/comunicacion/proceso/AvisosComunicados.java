package pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.PasadaPorColegios;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.AvisosComunicadosFamilias;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Sprint 9, tanda 3 (sección 3.10): cada 5 minutos (07:00 a 19:55, Lima), en cada colegio en producción,
 * {@code sistema.comunicados} prepara los urgentes pendientes y, a la hora del colegio, el resumen diario de comunicados
 * ({@link AvisosComunicadosFamilias}), en su propia transacción con la conexión de {@code cc_sistema}. Los envía
 * {@link DespachoMensajes}. Lee los comunicados solo por {@code comunicados.api} (ArchUnit).
 */
@Component
public class AvisosComunicados {

	private static final Logger LOG = LoggerFactory.getLogger(AvisosComunicados.class);

	private final AvisosComunicadosFamilias avisos;

	private final RecorridoColegios colegios;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	private final boolean activos;

	public AvisosComunicados(AvisosComunicadosFamilias avisos, RecorridoColegios colegios,
			PlatformTransactionManager transacciones, Clock reloj,
			@Value("${cuentasclaras.comunicados.avisos-activos:true}") boolean activos) {
		this.avisos = avisos;
		this.colegios = colegios;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.reloj = reloj;
		this.activos = activos;
	}

	@Scheduled(cron = "${cuentasclaras.comunicados.revisar-avisos-cada:30 */5 7-19 * * MON-SAT}", zone = ConfiguracionTiempo.ZONA)
	public void ejecutar() {
		if (!activos) {
			return;
		}
		LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
		PasadaPorColegios.recorrer(LOG, "Los avisos de comunicados", colegios.enProduccion(),
				colegio -> enColegio(colegio, ahora));
	}

	/** La pasada de un colegio (la usan también las pruebas, con la hora que eligen). */
	public AvisosComunicadosFamilias.Resultado enColegio(long colegioId, LocalDateTime ahora) {
		return EjecucionComoSistema.como(ActorSistema.COMUNICADOS, colegioId,
				() -> transaccion.execute(t -> avisos.pasada(ahora)));
	}
}
