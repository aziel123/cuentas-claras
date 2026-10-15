package pe.edu.virgenmaria.cuentasclaras.academico.dto;

import java.util.List;

/**
 * Sprint 9, tanda 1: el inicio de {@code /academico}. El docente ve sus secciones ({@code mias}); Dirección, además, las
 * secciones del año ({@code colegio}, con su tutor) y cuántas no tienen tutor. Sin nada de cobranza.
 */
public record InicioAcademico(boolean direccion, List<SeccionDocente> mias, Integer anio,
		List<SeccionDelColegio> colegio) {

	public InicioAcademico {
		mias = List.copyOf(mias);
		colegio = List.copyOf(colegio);
	}

	public long sinTutor() {
		return colegio.stream().filter(s -> s.activa() && s.tutor() == null).count();
	}
}
