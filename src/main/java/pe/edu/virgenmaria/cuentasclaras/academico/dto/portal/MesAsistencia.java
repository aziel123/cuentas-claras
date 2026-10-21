package pe.edu.virgenmaria.cuentasclaras.academico.dto.portal;

import java.util.List;

/** Sprint 9, tanda 2 (sección 3.5): el mes de los hijos de la familia, con el anterior y el siguiente para navegar. */
public record MesAsistencia(String mes, String mesTexto, String anterior, String siguiente, int diasJustificacion,
		List<MesDeHijo> hijos) {
}
