package pe.edu.virgenmaria.cuentasclaras.academico.service;

import java.util.Locale;

/**
 * Sprint 9, tanda 2 (sección 11): cómo se nombra a quien registró una corrección en el aviso a la familia y en las listas
 * («Prof. Ana T.»: el primer nombre y la inicial del apellido, como el «registrado por» de los avisos de pago) y el primer
 * nombre de un alumno (minimización: nada de apellidos en un WhatsApp).
 */
final class NombreCorto {

	private NombreCorto() {
	}

	/** «Ana Torres Ruiz» → «Prof. Ana T.». */
	static String docente(String nombreCompleto) {
		if (nombreCompleto == null || nombreCompleto.isBlank()) {
			return "el colegio";
		}
		String[] partes = nombreCompleto.strip().split("\\s+");
		return "Prof. " + partes[0] + (partes.length > 1 ? " " + partes[1].substring(0, 1).toUpperCase(Locale.ROOT) + "."
				: "");
	}

	/** «Mateo Andrés» → «Mateo». */
	static String primerNombre(String nombres) {
		if (nombres == null || nombres.isBlank()) {
			return "su hijo(a)";
		}
		return nombres.strip().split("\\s+")[0];
	}
}
