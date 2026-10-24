package pe.edu.virgenmaria.cuentasclaras.comunicados.inicial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

/**
 * Sprint 9, tanda 3 (sección 3.14, decisión 167): los comunicados de la demo de promotores, con datos ficticios
 * ({@link EjemploComunicados}, que comparte con el arranque de desarrollo). Dirección envía uno a todo el colegio con el
 * PDF de ejemplo (que pasa por el analizador, en su proceso aparte), Promotoría lo aprueba y Dirección envía su fe de
 * erratas; la tutora, uno a su sección. Y los códigos modulares ficticios (0000001 a 0000003) para la nómina con la marca
 * «DEMO · DATOS FICTICIOS». Si algo falla, la demo sigue sin ellos (queda en el log).
 */
@Component
@Profile("demo")
public class ComunicadosDemo {

	private static final Logger LOG = LoggerFactory.getLogger(ComunicadosDemo.class);

	private final EjemploComunicados ejemplo;

	public ComunicadosDemo(EjemploComunicados ejemplo) {
		this.ejemplo = ejemplo;
	}

	public void cargar(long colegioId, String promotoria, String direccion, String docente) {
		try {
			ejemplo.codigosModulares(colegioId, direccion);
			Long dia = ejemplo.diaDelLogro(colegioId, direccion);
			// Todo comunicado con PDF lo aprueba otra persona (correcciones del sprint 9): el de Dirección, Promotoría.
			ejemplo.aprobar(colegioId, promotoria, dia);
			ejemplo.feDeErratasDelDiaDelLogro(colegioId, direccion, dia);
			ejemplo.salidaAlMuseo(colegioId, docente);
		}
		catch (ReglaNegocioException | IllegalStateException e) {
			LOG.warn("No se cargaron los comunicados de la demo del colegio {}: {}", colegioId, e.getMessage());
		}
	}
}
