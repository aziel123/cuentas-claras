package pe.edu.virgenmaria.cuentasclaras.mysql;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.operacion.service.ResolucionesRespaldo;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Correcciones del sprint 7 (QA-S7-1) contra MySQL 8 real, en el job «respaldo» del CI, después de E30 y E32 (hay un
 * respaldo con FALTAN_FILAS sin resolver; {@code CC_PRUEBA_RESOLUCION_RESPALDO=true}): con la clave de cc_app nadie
 * resuelve la alerta sin la firma de Promotoría (1644), tampoco una cuenta de Dirección con su propia firma; Promotoría la
 * resuelve con motivo por el servicio y no se resuelve dos veces.
 */
@SpringBootTest
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_RESOLUCION_RESPALDO", matches = "true")
class ResolucionRespaldoMySqlTest {

	private static final String MOTIVO = "Revisado con el responsable técnico: pago borrado en la prueba E30";

	private final String sufijo = Long.toString(System.nanoTime(), 36);

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private ResolucionesRespaldo resoluciones;

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void soloPromotoriaConSuFirmaResuelveLaAlerta() {
		Long respaldo = jdbc.queryForObject("SELECT MAX(id) FROM respaldo WHERE comparacion = 'FALTAN_FILAS'", Long.class);
		assumeTrue(respaldo != null, "no hay un respaldo con FALTAN_FILAS (corre después de E30 y E32)");
		Usuario promotora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promo.resp." + sufijo,
				UsuariosDePrueba.CLAVE, false, Rol.PROMOTOR);
		Usuario directora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "dir.resp." + sufijo,
				UsuariosDePrueba.CLAVE, false, Rol.DIRECTOR);
		String insertar = "INSERT INTO resolucion_respaldo (respaldo_id, colegio_id, usuario_id, motivo, creado_en, "
				+ "creado_por) VALUES (?, 1, ?, ?, NOW(6), ?)";

		assertThat(codigoAl(() -> jdbc.update(insertar, respaldo, promotora.getId(), MOTIVO,
				promotora.getNombreUsuario()))).as("a nombre de Promotoría, sin su firma").isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update(insertar, respaldo, directora.getId(), MOTIVO,
				directora.getNombreUsuario()))).as("Dirección").isEqualTo(1644);

		UsuariosDePrueba.iniciarSesion(promotora);
		resoluciones.resolver(MOTIVO);

		assertThat(jdbc.queryForMap("SELECT respaldo_id, creado_por FROM resolucion_respaldo"))
				.containsEntry("respaldo_id", respaldo).containsEntry("creado_por", promotora.getNombreUsuario());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM firma_operacion WHERE clave = ?", Long.class,
				"respaldo:" + respaldo + ":RESUELTO")).isEqualTo(1L);
		assertThat(codigoAl(() -> jdbc.update(insertar, respaldo, promotora.getId(), MOTIVO,
				promotora.getNombreUsuario()))).as("dos veces").isIn(1062, 1644);
	}

	private static Integer codigoAl(Runnable sentencia) {
		try {
			sentencia.run();
			return null;
		}
		catch (DataAccessException e) {
			for (Throwable t = e; t != null; t = t.getCause()) {
				if (t instanceof SQLException sql) {
					return sql.getErrorCode();
				}
			}
			return -1;
		}
	}
}
