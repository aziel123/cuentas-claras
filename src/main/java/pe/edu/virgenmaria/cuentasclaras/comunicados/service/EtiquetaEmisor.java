package pe.edu.virgenmaria.cuentasclaras.comunicados.service;

import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.academico.api.SeccionesDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.SeccionDocente;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ColegioService;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EmisorRol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.Locale;

/**
 * Sprint 9, tanda 3 (sección 3.8, E16): la firma que ve la familia la arma el SISTEMA desde la cuenta en sesión y su rol
 * real: «Dirección del Colegio …», «Promotoría del Colegio …» o «Prof. Ana T., tutor(a) de 3.° B» / «Prof. Ana T.,
 * Matemática, 3.° B» (de su asignación vigente). Nadie la escribe.
 */
@Component
public class EtiquetaEmisor {

	static final int MAX = 120;

	private final ColegioService colegios;

	private final SeccionesDocente seccionesDocente;

	public EtiquetaEmisor(ColegioService colegios, SeccionesDocente seccionesDocente) {
		this.colegios = colegios;
		this.seccionesDocente = seccionesDocente;
	}

	/** La etiqueta para un comunicado de ese emisor (y, si es docente, de esa sección suya). */
	public String de(UsuarioAutenticado persona, EmisorRol rol, Long seccionId) {
		String colegio = colegios.nombreParaMensajes(ContextoColegio.actual()).replace('\n', ' ').strip();
		String etiqueta = switch (rol) {
			case DIRECCION -> "Dirección " + delColegio(colegio);
			case PROMOTORIA -> "Promotoría " + delColegio(colegio);
			case DOCENTE -> docente(persona, seccionId);
		};
		return etiqueta.length() > MAX ? etiqueta.substring(0, MAX - 1) + "…" : etiqueta;
	}

	private String docente(UsuarioAutenticado persona, Long seccionId) {
		SeccionDocente seccion = seccionesDocente.mias().stream().filter(s -> s.seccionId().equals(seccionId)).findFirst()
				.orElseThrow(() -> new pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException(
						"Sección no encontrada"));
		String nombre = "Prof. " + nombreCorto(persona.nombreCompleto());
		if (seccion.tutor()) {
			return nombre + ", tutor(a) de " + seccion.seccion();
		}
		String area = seccion.funcion().replaceFirst("(?i)^docente de área:\\s*", "");
		return nombre + ", " + area + ", " + seccion.seccion();
	}

	/** «del Colegio Virgen María» o «de Virgen María». */
	static String delColegio(String colegio) {
		return colegio.toLowerCase(Locale.ROOT).startsWith("colegio ") ? "del " + colegio : "de " + colegio;
	}

	/** «Ana Torres Vega» → «Ana T.» (como el «registrado por» de los avisos). */
	static String nombreCorto(String nombreCompleto) {
		String[] partes = nombreCompleto == null ? new String[0] : nombreCompleto.strip().split("\\s+");
		if (partes.length == 0 || partes[0].isEmpty()) {
			return "docente";
		}
		return partes.length == 1 ? partes[0] : partes[0] + " " + partes[1].charAt(0) + ".";
	}
}
