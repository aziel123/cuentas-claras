package pe.edu.virgenmaria.cuentasclaras.auditoria.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.HuellasDiarias;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.VerificadorIntegridadAuditoria;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cada día a las 06:00 (Lima), en cada colegio y como {@code sistema.auditoria} (sprint 5, G13):
 * <ol>
 *   <li>reverifica la cadena completa y las huellas guardadas de los últimos {@code reverificar-dias} días;</li>
 *   <li>guarda la huella de ayer y la envía por mensaje a Promotoría (y al correo externo del contador), con el
 *       resultado de la reverificación («Bitácora verificada: sí / NO»).</li>
 * </ol>
 * Si alguien recorta la bitácora con acceso a la base, Promotoría ya tiene en su celular la huella que no coincide.
 */
@Component
public class HuellaDiaria {

	private static final Logger LOG = LoggerFactory.getLogger(HuellaDiaria.class);

	private final HuellasDiarias huellas;

	private final VerificadorIntegridadAuditoria verificador;

	private final RecorridoColegios colegios;

	private final Clock reloj;

	private final int diasReverificar;

	public HuellaDiaria(HuellasDiarias huellas, VerificadorIntegridadAuditoria verificador, RecorridoColegios colegios,
			Clock reloj, @Value("${cuentasclaras.huella.reverificar-dias:400}") int diasReverificar) {
		this.huellas = huellas;
		this.verificador = verificador;
		this.colegios = colegios;
		this.reloj = reloj;
		this.diasReverificar = diasReverificar;
	}

	@Scheduled(cron = "${cuentasclaras.huella.cron:0 0 6 * * *}", zone = ConfiguracionTiempo.ZONA)
	public void ejecutar() {
		LocalDate hoy = LocalDate.now(reloj);
		for (Long colegio : colegios.activos()) {
			try {
				enColegio(colegio, hoy);
			}
			catch (RuntimeException e) {
				LOG.error("La huella diaria falló en el colegio {}: {}", colegio, e.getClass().getSimpleName());
			}
		}
	}

	/**
	 * S5-M4: la huella de la hora en horario de caja (lunes a sábado, de 08:00 a 19:00, hora de Lima). Un recorte del
	 * mismo día ya no pasa desapercibido: la huella del día siguiente no puede quedar por debajo de esta.
	 */
	@Scheduled(cron = "${cuentasclaras.huella.cron-hora:0 0 8-19 * * MON-SAT}", zone = ConfiguracionTiempo.ZONA)
	public void cadaHora() {
		LocalDateTime ahora = LocalDateTime.now(reloj);
		for (Long colegio : colegios.activos()) {
			try {
				horaEnColegio(colegio, ahora);
			}
			catch (RuntimeException e) {
				LOG.error("La huella de la hora falló en el colegio {}: {}", colegio, e.getClass().getSimpleName());
			}
		}
	}

	/** La huella de la hora de un colegio (lo llama la tarea programada; las pruebas, directamente). */
	public void horaEnColegio(Long colegio, LocalDateTime momento) {
		EjecucionComoSistema.como(ActorSistema.AUDITORIA, colegio, () -> huellas.registrarHora(momento));
	}

	/** La huella de ayer de un colegio (lo llama la tarea programada; las pruebas, directamente). */
	public void enColegio(Long colegio, LocalDate hoy) {
		boolean ok = EjecucionComoSistema.como(ActorSistema.AUDITORIA, colegio, () -> {
			boolean cadena = verificador.verificarComoSistema().integra();
			boolean guardadas = huellas.reverificarHuellas(hoy.minusDays(diasReverificar)).isEmpty();
			return cadena && guardadas;
		});
		EjecucionComoSistema.como(ActorSistema.AUDITORIA, colegio, () -> huellas.registrar(hoy.minusDays(1), ok));
	}
}
