package pe.edu.virgenmaria.cuentasclaras.comun.basedatos;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.TestPropertySource;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;

import java.io.IOException;
import java.nio.file.Path;

/** Primer arranque de la demo persistente: la base de archivo no existe y la aplicación la migra. */
@PruebaIntegracion
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:file:./target/h2-archivo-prueba/primero"
		+ IngresoSobreBaseArchivoH2.OPCIONES)
class PrimerArranqueBaseArchivoH2Test extends IngresoSobreBaseArchivoH2 {

	@BeforeAll
	static void baseNueva() throws IOException {
		borrarCarpeta(Path.of("target", "h2-archivo-prueba", "primero"));
		borrarCarpeta(Path.of("target", "h2-archivo-prueba", "primero.mv.db"));
	}
}
