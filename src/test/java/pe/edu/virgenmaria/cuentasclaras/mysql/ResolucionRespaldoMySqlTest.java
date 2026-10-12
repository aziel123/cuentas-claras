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
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.operacion.service.ResolucionesRespaldo;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Correcciones del sprint 7 (QA-S7-1) contra MySQL 8 real, en el job «respaldo» del CI, después de E30 y E32 (hay un
 * respaldo con FALTAN_FILAS sin resolver; {@code CC_PRUEBA_RESOLUCION_RESPALDO=true}): con la clave de cc_app nadie
 * resuelve la alerta sin la firma de Promotoría (1644), tampoco una cuenta de Dirección con su propia firma; Promotoría la
 * resuelve con motivo por el servicio y no se resuelve dos veces.
 * <p>
 * Sprint 8, tanda 3 (V30): el recorte de la bitácora (E32) afecta a TODOS los colegios de la instalación, así que la
 * alerta la resuelve la Promotoría de cada colegio afectado, cada una con su firma ({@code respaldo:{id}:{colegio}:RESUELTO}).
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
	void soloPromotoriaConSuFirmaResuelveLaAlertaDeCadaColegio() {
		Long respaldo = jdbc.queryForObject("SELECT MAX(id) FROM respaldo WHERE comparacion = 'FALTAN_FILAS'", Long.class);
		assumeTrue(respaldo != null, "no hay un respaldo con FALTAN_FILAS (corre después de E30 y E32)");
		List<Long> afectados = colegiosDe(respaldo);
		assertThat(afectados).as("el recorte de la bitácora (E32) nombra a todos los colegios").contains(1L)
				.containsAll(jdbc.queryForList("SELECT id FROM colegio WHERE tipo = 'COLEGIO'", Long.class));
		Usuario promotora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promo.resp." + sufijo,
				UsuariosDePrueba.CLAVE, false, Rol.PROMOTOR);
		Usuario directora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "dir.resp." + sufijo,
				UsuariosDePrueba.CLAVE, false, Rol.DIRECTOR);
		String insertar = "INSERT INTO resolucion_respaldo (respaldo_id, colegio_id, usuario_id, motivo, creado_en, "
				+ "creado_por) VALUES (?, ?, ?, ?, NOW(6), ?)";

		assertThat(codigoAl(() -> jdbc.update(insertar, respaldo, 1L, promotora.getId(), MOTIVO,
				promotora.getNombreUsuario()))).as("a nombre de Promotoría, sin su firma").isEqualTo(1644);
		assertThat(codigoAl(() -> jdbc.update(insertar, respaldo, 1L, directora.getId(), MOTIVO,
				directora.getNombreUsuario()))).as("Dirección").isEqualTo(1644);

		UsuariosDePrueba.iniciarSesion(promotora);
		resoluciones.resolver(MOTIVO);
		SecurityContextHolder.clearContext();

		assertThat(jdbc.queryForMap("SELECT respaldo_id, creado_por FROM resolucion_respaldo WHERE colegio_id = 1"))
				.containsEntry("respaldo_id", respaldo).containsEntry("creado_por", promotora.getNombreUsuario());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM firma_operacion WHERE clave = ?", Long.class,
				"respaldo:" + respaldo + ":1:RESUELTO")).isEqualTo(1L);
		assertThat(codigoAl(() -> jdbc.update(insertar, respaldo, 1L, promotora.getId(), MOTIVO,
				promotora.getNombreUsuario()))).as("dos veces").isIn(1062, 1644);

		// Los demás colegios afectados: cada uno con una Promotoría activa de SU colegio.
		for (Long colegio : afectados) {
			if (colegio == 1L) {
				continue;
			}
			Usuario suya = promotoriaActiva(colegio);
			UsuariosDePrueba.iniciarSesion(suya);
			resoluciones.resolver(MOTIVO);
			SecurityContextHolder.clearContext();
		}
		assertThat(jdbc.queryForList("SELECT colegio_id FROM resolucion_respaldo WHERE respaldo_id = ?", Long.class,
				respaldo)).containsExactlyInAnyOrderElementsOf(afectados);
	}

	private List<Long> colegiosDe(Long respaldo) {
		String lista = jdbc.queryForObject("SELECT colegios_faltan FROM respaldo WHERE id = ?", String.class, respaldo);
		assertThat(lista).as("colegios_faltan del respaldo " + respaldo).isNotNull();
		return Arrays.stream(lista.replace("[", "").replace("]", "").split(",")).map(Long::valueOf).toList();
	}

	private Usuario promotoriaActiva(Long colegio) {
		Long id = jdbc.queryForObject("SELECT MIN(u.id) FROM usuario u JOIN usuario_rol r ON r.usuario_id = u.id "
				+ "WHERE u.colegio_id = ? AND u.activo AND u.apoderado_id IS NULL AND r.rol = 'PROMOTOR'", Long.class, colegio);
		assertThat(id).as("una Promotoría activa en el colegio " + colegio).isNotNull();
		return ContextoColegio.en(colegio, () -> usuarios.findById(id).orElseThrow());
	}

	static Integer codigoAl(Runnable sentencia) {
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
