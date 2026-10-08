package pe.edu.virgenmaria.cuentasclaras.panel.proceso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.config.ConfiguracionTiempo;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.ActorSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.EjecucionComoSistema;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;
import pe.edu.virgenmaria.cuentasclaras.panel.model.ResumenDiario;
import pe.edu.virgenmaria.cuentasclaras.panel.service.ResumenesDiarios;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Resumen diario de las 19:30 (Lima), en cada colegio y como {@code sistema.panel} (sprint 6, tanda 2; decisión 68).
 * Guarda la foto (comprobada contra los libros por el trigger en MySQL) y la envía a Promotoría en la misma transacción.
 * <p>
 * Con READ COMMITTED, un pago confirmado por otra transacción entre la lectura de las cifras y el INSERT de la foto hace
 * que el trigger la rechace (las cifras ya no son las de los libros): se reintenta con una transacción nueva hasta
 * {@value #INTENTOS} veces. Si aun así falla, queda {@code RESUMEN_DIARIO_NO_SALIO} resaltado y, a las 21:00, la alerta
 * CRÍTICA «el resumen no salió» (P5).
 */
@Component
public class ResumenDiarioTarea {

	private static final Logger LOG = LoggerFactory.getLogger(ResumenDiarioTarea.class);

	static final int INTENTOS = 3;

	private final ResumenesDiarios resumenes;

	private final RecorridoColegios colegios;

	private final Clock reloj;

	public ResumenDiarioTarea(ResumenesDiarios resumenes, RecorridoColegios colegios, Clock reloj) {
		this.resumenes = resumenes;
		this.colegios = colegios;
		this.reloj = reloj;
	}

	@Scheduled(cron = "${cuentasclaras.panel.resumen-diario:0 30 19 * * *}", zone = ConfiguracionTiempo.ZONA)
	public void ejecutar() {
		LocalDate hoy = LocalDate.now(reloj);
		for (Long colegio : colegios.activos()) {
			try {
				enColegio(colegio, hoy);
			}
			catch (RuntimeException e) {
				LOG.error("El resumen diario falló en el colegio {}: {}", colegio, e.getClass().getSimpleName());
			}
		}
	}

	/** El resumen de un día en un colegio (lo llama la tarea programada; las pruebas, directamente). */
	public Optional<ResumenDiario> enColegio(Long colegio, LocalDate fecha) {
		RuntimeException ultimo = null;
		for (int intento = 1; intento <= INTENTOS; intento++) {
			try {
				return EjecucionComoSistema.como(ActorSistema.PANEL, colegio, () -> resumenes.generar(fecha));
			}
			catch (RuntimeException e) {
				ultimo = e;
				LOG.warn("El resumen del {} del colegio {} falló (intento {} de {}): {}", fecha, colegio, intento, INTENTOS,
						e.getClass().getSimpleName());
			}
		}
		String causa = ultimo.getClass().getSimpleName();
		EjecucionComoSistema.como(ActorSistema.PANEL, colegio, () -> resumenes.registrarQueNoSalio(fecha, causa));
		throw ultimo;
	}
}
