package pe.edu.virgenmaria.cuentasclaras.comunicados.inicial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.seguridad.inicial.DatosDemoDev;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.PersonaDemo;

/**
 * Los comunicados del arranque de desarrollo (con {@link EjemploComunicados}, lo mismo que la demo pública), después de
 * lo académico ({@code AcademicoDemoDev}: la tutora ya tiene su sección): Dirección registra los códigos modulares
 * ficticios del SIAGIE, la tutora de 3.° Primaria A envía la «Salida al museo» (sin PDF: sale de inmediato) y Dirección
 * envía el «Día del Logro» con el PDF de ejemplo, que queda por aprobar en la bandeja de Promotoría. Solo con H2 en
 * memoria; si algo falla, el arranque sigue sin ellos (queda en el log).
 */
@Component
@Profile("dev")
@Order(22)
public class ComunicadosDemoDev implements ApplicationRunner {

	private static final Logger LOG = LoggerFactory.getLogger(ComunicadosDemoDev.class);

	private final EjemploComunicados ejemplo;

	private final PersonaDemo personas;

	private final String urlBaseDatos;

	private final boolean habilitado;

	public ComunicadosDemoDev(EjemploComunicados ejemplo, PersonaDemo personas,
			@Value("${spring.datasource.url:}") String urlBaseDatos,
			@Value("${cuentasclaras.demo.datos-colegio:true}") boolean habilitado) {
		this.ejemplo = ejemplo;
		this.personas = personas;
		this.urlBaseDatos = urlBaseDatos;
		this.habilitado = habilitado;
	}

	@Override
	public void run(ApplicationArguments argumentos) {
		crearSiCorresponde();
	}

	/** @return {@code true} si quedaron los dos comunicados */
	public boolean crearSiCorresponde() {
		long colegio = DatosDemoDev.COLEGIO_PRINCIPAL;
		if (!habilitado || urlBaseDatos == null || !urlBaseDatos.startsWith("jdbc:h2:mem:")
				|| !personas.existen(colegio, "director", "docente")) {
			return false;
		}
		try {
			ejemplo.codigosModulares(colegio, "director");
			Long salida = ejemplo.salidaAlMuseo(colegio, "docente");
			ejemplo.diaDelLogro(colegio, "director");
			LOG.info("Comunicados de demostración: {} y el «Día del Logro» de «director», con PDF, por aprobar en la "
					+ "bandeja de Promotoría.", salida == null ? "la tutora no tiene sección"
							: "la «Salida al museo» de «docente» enviada a su sección");
			return salida != null;
		}
		catch (ReglaNegocioException | IllegalStateException e) {
			LOG.warn("No se cargaron los comunicados de demostración: {}", e.getMessage());
			return false;
		}
	}
}
