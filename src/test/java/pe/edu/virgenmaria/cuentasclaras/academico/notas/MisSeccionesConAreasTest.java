package pe.edu.virgenmaria.cuentasclaras.academico.notas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.SeccionDocente;
import pe.edu.virgenmaria.cuentasclaras.academico.service.SeccionesDelDocente;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10, tanda 3: en «Mis secciones», la docente con una asignación del catálogo (sin el texto libre del sprint 9) ve
 * los nombres de sus áreas («Docente de área: Matemática, Ciencia y Tecnología»); el tutor sigue como «Tutor(a)».
 */
class MisSeccionesConAreasTest extends PruebaNotas {

	@Autowired
	private SeccionesDelDocente seccionesDelDocente;

	@Test
	void laDocenteVeLasAreasDelCatalogo() {
		SeccionDocente deAna = como(UsuariosDePrueba.autenticado(ana), () -> seccionesDelDocente.mias()).getFirst();
		assertThat(deAna.funcion()).startsWith("Docente de área: ").contains("Matemática").contains("Ciencia");
		SeccionDocente deTito = como(UsuariosDePrueba.autenticado(tito), () -> seccionesDelDocente.mias()).getFirst();
		assertThat(deTito.funcion()).isEqualTo("Tutor(a)");
	}
}
