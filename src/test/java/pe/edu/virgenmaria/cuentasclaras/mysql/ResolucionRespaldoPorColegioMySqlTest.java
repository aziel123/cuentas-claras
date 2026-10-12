package pe.edu.virgenmaria.cuentasclaras.mysql;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.operacion.service.ResolucionesRespaldo;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.ClaveFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.FirmaSesion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.mysql.ResolucionRespaldoMySqlTest.codigoAl;

/**
 * Sprint 8, tanda 3 (V30, E25 y E28) contra MySQL 8 real, en el job «respaldo» del CI, después de
 * {@code pruebas-ci.sh faltan-filas-por-colegio} (un DBA borró un pago del colegio A en una instalación con varios
 * colegios; el respaldo siguiente dice FALTAN_FILAS con {@code colegios_faltan = [A]}). Con
 * {@code CC_PRUEBA_RESOLUCION_POR_COLEGIO=true}:
 * <ul>
 *   <li>la Promotoría de OTRO colegio (B) no la resuelve: ni por el servicio («no hay ninguna alerta») ni con la clave de
 *       cc_app y la firma de su propia sesión ({@code respaldo:{id}:{B}:RESUELTO}): 1644;</li>
 *   <li>la Promotoría de A sí, con su firma, y queda una sola resolución (la de A).</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_RESOLUCION_POR_COLEGIO", matches = "true")
class ResolucionRespaldoPorColegioMySqlTest {

	private static final String MOTIVO = "Revisado con el responsable técnico: pago borrado en la prueba E25";

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ResolucionesRespaldo resoluciones;

	@Autowired
	private FirmaSesion firma;

	@Autowired
	private PlatformTransactionManager transacciones;

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void laPromotoriaDeOtroColegioNoResuelveYLaDelColegioAfectadoSi() {
		Long respaldo = jdbc.queryForObject("SELECT MAX(id) FROM respaldo WHERE comparacion = 'FALTAN_FILAS'", Long.class);
		String lista = jdbc.queryForObject("SELECT colegios_faltan FROM respaldo WHERE id = ?", String.class, respaldo);
		assertThat(lista).as("solo el colegio del pago borrado").matches("\\[[0-9]+\\]");
		long colegioA = Long.parseLong(lista.substring(1, lista.length() - 1));
		Long colegioB = jdbc.queryForObject("SELECT MIN(u.colegio_id) FROM usuario u JOIN usuario_rol r ON r.usuario_id = "
				+ "u.id JOIN colegio c ON c.id = u.colegio_id WHERE c.tipo = 'COLEGIO' AND u.colegio_id <> ? AND u.activo "
				+ "AND u.apoderado_id IS NULL AND r.rol = 'PROMOTOR'", Long.class, colegioA);
		assertThat(colegioB).as("otro colegio con Promotoría activa en la instalación").isNotNull();
		Usuario promotoraA = promotoria(colegioA);
		Usuario promotoraB = promotoria(colegioB);

		// B, por el servicio: para su colegio no hay nada que resolver (no tuvo faltas, o ya resolvió las suyas, como las del
		// recorte de la bitácora de E32 que nombraba a todos).
		UsuariosDePrueba.iniciarSesion(promotoraB);
		assertThatThrownBy(() -> resoluciones.resolver(MOTIVO)).isInstanceOf(ReglaNegocioException.class)
				.satisfies(e -> assertThat(e.getMessage()).containsAnyOf("No hay ninguna alerta", "ya se resolvió"));
		// B, con la clave de cc_app y la firma de SU sesión: la base lo rechaza (su colegio no está en colegios_faltan).
		Integer codigo = codigoAl(() -> new TransactionTemplate(transacciones).executeWithoutResult(t -> {
			firma.firmar(ClaveFirma.respaldoResuelto(respaldo, colegioB));
			jdbc.update("INSERT INTO resolucion_respaldo (respaldo_id, colegio_id, usuario_id, motivo, creado_en, creado_por) "
					+ "VALUES (?, ?, ?, ?, NOW(6), ?)", respaldo, colegioB, promotoraB.getId(), MOTIVO,
					promotoraB.getNombreUsuario());
		}));
		SecurityContextHolder.clearContext();
		assertThat(codigo).as("la Promotoría de B resuelve la falta de A").isEqualTo(1644);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resolucion_respaldo WHERE respaldo_id = ?", Long.class,
				respaldo)).isZero();

		// A sí.
		UsuariosDePrueba.iniciarSesion(promotoraA);
		resoluciones.resolver(MOTIVO);
		SecurityContextHolder.clearContext();
		assertThat(jdbc.queryForList("SELECT colegio_id FROM resolucion_respaldo WHERE respaldo_id = ?", Long.class,
				respaldo)).containsExactly(colegioA);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM firma_operacion WHERE clave = ? AND colegio_id = ?",
				Long.class, "respaldo:" + respaldo + ":" + colegioA + ":RESUELTO", colegioA)).isEqualTo(1L);
	}

	private Usuario promotoria(long colegio) {
		Long id = jdbc.queryForObject("SELECT MIN(u.id) FROM usuario u JOIN usuario_rol r ON r.usuario_id = u.id "
				+ "WHERE u.colegio_id = ? AND u.activo AND u.apoderado_id IS NULL AND r.rol = 'PROMOTOR'", Long.class, colegio);
		assertThat(id).as("una Promotoría activa en el colegio " + colegio).isNotNull();
		return ContextoColegio.en(colegio, () -> usuarios.findById(id).orElseThrow());
	}
}
