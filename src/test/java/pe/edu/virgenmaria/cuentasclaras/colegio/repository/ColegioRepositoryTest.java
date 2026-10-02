package pe.edu.virgenmaria.cuentasclaras.colegio.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaJpa;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que las migraciones Flyway crean el esquema que esperan las entidades
 * (ddl-auto=validate) y que el colegio inicial existe.
 */
@PruebaJpa
class ColegioRepositoryTest {

	@Autowired
	private ColegioRepository colegioRepository;

	@Test
	void laMigracionInicialDebeCrearAlColegioVirgenMariaActivo() {
		assertThat(colegioRepository.findByActivoTrueOrderByIdAsc())
				.extracting(Colegio::getNombre)
				.containsExactly("Colegio Virgen María");
	}
}
