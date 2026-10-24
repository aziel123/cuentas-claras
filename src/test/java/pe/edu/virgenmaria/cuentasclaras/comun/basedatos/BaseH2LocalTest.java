package pe.edu.virgenmaria.cuentasclaras.comun.basedatos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaseH2LocalTest {

	@Test
	void soloLaH2EnMemoriaOEnArchivoEsLocal() {
		assertThat(BaseH2Local.es("jdbc:h2:mem:cuentasclaras;MODE=MySQL")).isTrue();
		assertThat(BaseH2Local.es("jdbc:h2:file:./.demo-db/cuentasclaras;MODE=MySQL")).isTrue();
		assertThat(BaseH2Local.es("jdbc:mysql://localhost:3306/cuentasclaras")).isFalse();
		assertThat(BaseH2Local.es("jdbc:h2:tcp://servidor/~/cc")).isFalse();
		assertThat(BaseH2Local.es("")).isFalse();
		assertThat(BaseH2Local.es(null)).isFalse();
	}

	@Test
	void elAnclaDeMigracionCubreLasMismasBases() {
		assertThat(AnclaMigracionH2.anclada("jdbc:h2:file:./.demo-db/cuentasclaras")).isTrue();
		assertThat(AnclaMigracionH2.anclada("jdbc:h2:mem:cuentasclaras")).isTrue();
		assertThat(AnclaMigracionH2.anclada("jdbc:mysql://localhost:3306/cuentasclaras")).isFalse();
	}
}
