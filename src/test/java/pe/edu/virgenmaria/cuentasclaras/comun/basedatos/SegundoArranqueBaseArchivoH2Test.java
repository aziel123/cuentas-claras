package pe.edu.virgenmaria.cuentasclaras.comun.basedatos;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.TestPropertySource;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Segundo arranque de la demo persistente: la base de archivo ya está migrada (por otro arranque que terminó) y la
 * aplicación no migra nada.
 */
@PruebaIntegracion
@TestPropertySource(properties = "spring.datasource.url=" + SegundoArranqueBaseArchivoH2Test.URL)
class SegundoArranqueBaseArchivoH2Test extends IngresoSobreBaseArchivoH2 {

	static final String URL = "jdbc:h2:file:./target/h2-archivo-prueba/segundo" + IngresoSobreBaseArchivoH2.OPCIONES;

	/** El «primer arranque»: migra la base y la cierra, como cuando se detiene la aplicación. */
	@BeforeAll
	static void baseYaMigrada() throws IOException, SQLException {
		borrarCarpeta(Path.of("target", "h2-archivo-prueba", "segundo.mv.db"));
		Flyway.configure().dataSource(URL, "sa", "").load().migrate();
		try (Connection conexion = DriverManager.getConnection(URL, "sa", ""); Statement sql = conexion.createStatement()) {
			sql.execute("SHUTDOWN");
		}
	}
}
