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
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.PasadaPorColegios;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.AvisosLibretasFamilias;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Sprint 10, tanda 3 (sección 3.7): cada 5 minutos (07:00 a 19:55, de lunes a sábado, Lima), en cada colegio en producción
 * (uno en preparación emite libretas pero no avisa, sección 3.12), {@code sistema.libretas} crea los avisos de libretas
 * ({@link AvisosLibretasFamilias}) en su propia transacción con la conexión de {@code cc_sistema}. Lee las libretas solo por
 * {@code academico.api.LibretasPorAvisar} (ArchUnit). Los envía {@link DespachoMensajes}.
 */
@Component
public class AvisosLibretas {

	private static final Logger LOG = LoggerFactory.getLogger(AvisosLibretas.class);

	private final AvisosLibretasFamilias avisos;

	private final RecorridoColegios colegios;

	private final TransactionTemplate transaccion;

	private final Clock reloj;

	public AvisosLibretas(AvisosLibretasFamilias avisos, RecorridoColegios colegios, PlatformTransactionManager transacciones,
			Clock reloj) {
		this.avisos = avisos;
		this.colegios = colegios;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.reloj = reloj;
	}

	@Scheduled(cron = "${cuentasclaras.libretas.avisos-cada:45 */5 7-19 * * MON-SAT}", zone = ConfiguracionTiempo.ZONA)
	public void ejecutar() {
		LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
		PasadaPorColegios.recorrer(LOG, "Los avisos de libretas", colegios.enProduccion(), colegio -> enColegio(colegio, ahora));
	}

	/** La pasada de un colegio (la usan también las pruebas). @return cuántas familias recibieron aviso */
	public int enColegio(long colegioId, LocalDateTime ahora) {
		Integer avisadas = EjecucionComoSistema.como(ActorSistema.LIBRETAS, colegioId,
				() -> transaccion.execute(t -> avisos.pasada(ahora)));
		return avisadas == null ? 0 : avisadas;
	}
}
