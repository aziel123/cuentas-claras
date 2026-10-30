package pe.edu.virgenmaria.cuentasclaras.mysql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import pe.edu.virgenmaria.cuentasclaras.comun.basedatos.FuenteDatosEnrutada;
import pe.edu.virgenmaria.cuentasclaras.comun.cripto.Sha256;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.DbaMySql;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 11, tanda 2 (AP-3 y AP2-6): el reloj de la base es el real (UTC − 5 h, sin depender de la zona ni del
 * {@code SET timestamp} de la conexión) en TODOS los usos de 03 ({@code reloj-pendiente.txt} en 0 y ningún
 * {@code INTERVAL 5 HOUR}), y cada ventana se prueba un segundo antes y uno después de su límite: los 5 minutos de las
 * firmas, las 48 horas de la nota tardía, la hora de la llamada de control y de la verificación de la cadena, y las 12
 * horas (y los 5 minutos de reloj) con que nace una sesión.
 */
@SpringBootTest
@ActiveProfiles({ "test", "mysql" })
@EnabledIfEnvironmentVariable(named = "CC_PRUEBA_MYSQL", matches = "true")
class RelojEnSuLimiteMySqlTest {

	@Autowired
	private javax.sql.DataSource fuenteDatos;

	@Test
	void elRelojEsElRealYNoQuedaNingunUsoDelReloj5Horas() throws Exception {
		JdbcTemplate dba = DbaMySql.exigir();
		assertThat(dba.queryForObject("SELECT ABS(TIMESTAMPDIFF(MICROSECOND, cc_ahora_lima_real(), UTC_TIMESTAMP(6) - "
				+ "INTERVAL 5 HOUR)) < 2000000", Boolean.class)).isTrue();
		assertThat(dba.queryForObject("SELECT cc_hoy_lima() = DATE(cc_ahora_lima_real())", Boolean.class)).isTrue();
		String triggers = Files.readString(Path.of("scripts", "mysql", "03-triggers.sql"), StandardCharsets.UTF_8);
		String sinComentarios = triggers.lines().filter(l -> !l.strip().startsWith("--")).reduce("", (a, b) -> a + b + "\n");
		// Solo la definición de cc_ahora_lima_real usa la expresión completa.
		assertThat(sinComentarios.split("INTERVAL 5 HOUR", -1).length - 1).isLessThanOrEqualTo(1);
		assertThat(Files.readAllLines(Path.of("scripts", "mysql", "reloj-pendiente.txt")).stream()
				.filter(l -> !l.startsWith("#") && !l.isBlank()).map(String::strip)).containsExactly("0");
	}

	/** cc_firma_valida (5 minutos), la nota tardía (48 horas) y la hora de la llamada de control, en su límite. */
	@Test
	void lasVentanasDeLasFirmasEnSuLimite() {
		JdbcTemplate dba = DbaMySql.exigir();
		Map<String, Object> firma = dba.queryForMap("SELECT f.id, f.colegio_id, f.clave, f.firmada_bd, u.nombre_usuario "
				+ "FROM firma_operacion f JOIN usuario u ON u.id = f.usuario_id AND u.colegio_id = f.colegio_id "
				+ "ORDER BY f.id DESC LIMIT 1");
		Object id = firma.get("id");
		try {
			assertThat(firmaValida(dba, firma, "- INTERVAL 5 MINUTE + INTERVAL 1 SECOND")).isTrue();
			assertThat(firmaValida(dba, firma, "- INTERVAL 5 MINUTE - INTERVAL 1 SECOND")).isFalse();
			assertThat(ventana(dba, id, "- INTERVAL 48 HOUR + INTERVAL 1 SECOND", "- INTERVAL 48 HOUR")).isTrue();
			assertThat(ventana(dba, id, "- INTERVAL 48 HOUR - INTERVAL 1 SECOND", "- INTERVAL 48 HOUR")).isFalse();
			assertThat(ventana(dba, id, "- INTERVAL 1 HOUR + INTERVAL 1 SECOND", "- INTERVAL 1 HOUR")).isTrue();
			assertThat(ventana(dba, id, "- INTERVAL 1 HOUR - INTERVAL 1 SECOND", "- INTERVAL 1 HOUR")).isFalse();
		}
		finally {
			dba.update("UPDATE firma_operacion SET firmada_bd = ? WHERE id = ?", firma.get("firmada_bd"), id);
		}
	}

	/** trg_sesion_usuario_nace: vence a las 12 horas como máximo y nace «ahora» (5 minutos de reloj), en su límite. */
	@Test
	void laSesionNaceEnSusLimites() {
		Map<String, Object> cuenta = DbaMySql.exigir().queryForMap("SELECT u.id, u.colegio_id FROM usuario u JOIN colegio c ON "
				+ "c.id = u.colegio_id WHERE u.activo AND u.apoderado_id IS NULL AND c.tipo = 'COLEGIO' AND c.estado IN "
				+ "('PREPARACION', 'PRODUCCION') ORDER BY u.id LIMIT 1");
		// Vence a las 12 horas de abierta, no un segundo más.
		assertThat(sesion(cuenta, 0, 12 * 3600)).isZero();
		assertThat(sesion(cuenta, 0, 12 * 3600 + 1)).isEqualTo(1644);
		// Nace «ahora» según el reloj real: hasta 300 segundos de diferencia.
		assertThat(sesion(cuenta, -298, 3600)).isZero();
		assertThat(sesion(cuenta, -302, 3600)).isEqualTo(1644);
	}

	private static boolean firmaValida(JdbcTemplate dba, Map<String, Object> firma, String desplazamiento) {
		dba.update("UPDATE firma_operacion SET firmada_bd = cc_ahora_lima_real() " + desplazamiento + " WHERE id = ?",
				firma.get("id"));
		return Boolean.TRUE.equals(dba.queryForObject("SELECT cc_firma_valida(?, ?, ?)", Boolean.class,
				firma.get("colegio_id"), firma.get("clave"), firma.get("nombre_usuario")));
	}

	/** La misma comparación que hacen los triggers con esa ventana. */
	private static boolean ventana(JdbcTemplate dba, Object id, String desplazamiento, String limite) {
		dba.update("UPDATE firma_operacion SET firmada_bd = cc_ahora_lima_real() " + desplazamiento + " WHERE id = ?", id);
		return Boolean.TRUE.equals(dba.queryForObject("SELECT firmada_bd >= cc_ahora_lima_real() " + limite
				+ " FROM firma_operacion WHERE id = ?", Boolean.class, id));
	}

	/** Abierta {@code desdeAhora} segundos respecto del reloj real de la base; vence {@code vence} segundos después. */
	private int sesion(Map<String, Object> cuenta, long desdeAhora, long vence) {
		JdbcTemplate sistema = new JdbcTemplate(((FuenteDatosEnrutada) fuenteDatos).sistema());
		java.time.LocalDateTime ahora = DbaMySql.exigir().queryForObject("SELECT cc_ahora_lima_real()",
				java.sql.Timestamp.class).toLocalDateTime();
		java.time.LocalDateTime abierta = ahora.plusSeconds(desdeAhora);
		try {
			sistema.update("INSERT INTO sesion_usuario (colegio_id, usuario_id, hash_token, abierta_en, vence_en, creado_en, "
					+ "creado_por, actualizado_en) VALUES (?, ?, ?, ?, ?, NOW(6), 'sistema.identidad', NOW(6))",
					cuenta.get("colegio_id"), cuenta.get("id"), Sha256.hex("reloj" + System.nanoTime()), abierta,
					abierta.plusSeconds(vence));
			return 0;
		}
		catch (DataAccessException e) {
			Throwable causa = e;
			while (causa != null && !(causa instanceof SQLException)) {
				causa = causa.getCause();
			}
			return causa instanceof SQLException sql ? sql.getErrorCode() : -1;
		}
	}
}
