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
		// Sprint 8, tanda 1: V28 agrega la fila PLATAFORMA (los operadores), que no es un colegio cliente.
		assertThat(colegioRepository.findByTipoAndActivoTrueOrderByIdAsc(
				pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio.COLEGIO))
				.extracting(Colegio::getNombre)
				.containsExactly("Colegio Virgen María");
		assertThat(colegioRepository.findFirstByTipo(pe.edu.virgenmaria.cuentasclaras.colegio.model.TipoColegio.PLATAFORMA))
				.hasValueSatisfying(p -> assertThat(p.enProduccion()).isTrue());
	}
}
