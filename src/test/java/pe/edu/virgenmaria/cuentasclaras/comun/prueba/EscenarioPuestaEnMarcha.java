package pe.edu.virgenmaria.cuentasclaras.comun.prueba;

import org.springframework.jdbc.core.JdbcTemplate;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

/**
 * Sprint 8, tanda 2 (sección 3.5): deja en H2 (sin triggers) los datos que piden los pasos de la lista de verificación de
 * la puesta en marcha que no son firmas: año en curso con una sección por nivel, un plan aprobado por nivel, un alumno
 * matriculado, la cuenta bancaria vigente, un mensaje ya salido a la Promotoría, el correo externo de control, el aviso de
 * privacidad aprobado y un respaldo al día. Los dos «aceptos» (sin saldo inicial y comprobante simulado) los firma la
 * Promotoría con {@code PasoAProduccion} en cada prueba. Solo H2: en MySQL real los triggers no lo dejarían.
 */
public final class EscenarioPuestaEnMarcha {

	private EscenarioPuestaEnMarcha() {
	}

	/** Todo lo que no es firma. {@code promotorId}: la cuenta de Promotoría que «ya recibió» un mensaje. */
	public static void datos(JdbcTemplate jdbc, long colegioId, Set<Nivel> niveles, long promotorId) {
		LocalDateTime ahora = LocalDateTime.now(ZoneId.of("America/Lima"));
		String sufijo = Long.toString(colegioId);
		jdbc.update("INSERT INTO anio_escolar (colegio_id, anio, estado, vigente, inicio_clases, fin_clases, creado_en, "
				+ "creado_por, actualizado_en) VALUES (?, 2026, 'EN_CURSO', TRUE, '2026-03-02', '2026-12-18', ?, 'prueba', ?)",
				colegioId, ahora, ahora);
		Long anio = jdbc.queryForObject("SELECT id FROM anio_escolar WHERE colegio_id = ? AND anio = 2026", Long.class,
				colegioId);
		Long seccion = null;
		for (Nivel nivel : niveles) {
			String grado = switch (nivel) {
				case INICIAL -> "INICIAL_3";
				case PRIMARIA -> "PRIMARIA_1";
				case SECUNDARIA -> "SECUNDARIA_1";
			};
			jdbc.update("INSERT INTO seccion (colegio_id, anio_escolar_id, grado, nombre, creado_en, creado_por, "
					+ "actualizado_en) VALUES (?, ?, ?, 'A', ?, 'prueba', ?)", colegioId, anio, grado, ahora, ahora);
			seccion = jdbc.queryForObject("SELECT MAX(id) FROM seccion WHERE colegio_id = ?", Long.class, colegioId);
			jdbc.update("INSERT INTO plan_pension (colegio_id, anio_escolar_id, nivel, numero_version, estado, vigente, "
					+ "monto_matricula, vencimiento_matricula, monto_pension, vencimientos_pension, editado_por, editores, "
					+ "aprobado_por, aprobado_en, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, 1, 'APROBADO', "
					+ "TRUE, 300.00, '2026-03-01', 450.00, '2026-03-31', 'administracion.pm', ',administracion.pm,', "
					+ "'direccion.pm', ?, ?, 'administracion.pm', ?)", colegioId, anio, nivel.name(), ahora, ahora, ahora);
		}
		jdbc.update("INSERT INTO familia (colegio_id, nombre, creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, "
				+ "'prueba', ?)", colegioId, "Familia Puesta " + sufijo, ahora, ahora);
		Long familia = jdbc.queryForObject("SELECT MAX(id) FROM familia WHERE colegio_id = ?", Long.class, colegioId);
		jdbc.update("INSERT INTO apoderado (colegio_id, familia_id, tipo_documento, numero_documento, apellido_paterno, "
				+ "nombres, parentesco, nombre_busqueda, activo, correo, creado_en, creado_por, actualizado_en) VALUES (?, ?, 'DNI', ?, "
				+ "'Puesta', 'Ana', 'MADRE', 'puesta ana', TRUE, 'ana.puesta@correo.pe', ?, 'prueba', ?)", colegioId, familia,
				String.format("7%07d", colegioId % 10_000_000), ahora, ahora);
		Long apoderado = jdbc.queryForObject("SELECT MAX(id) FROM apoderado WHERE colegio_id = ?", Long.class, colegioId);
		jdbc.update("INSERT INTO alumno (colegio_id, familia_id, responsable_pago_id, tipo_documento, numero_documento, "
				+ "apellido_paterno, nombres, fecha_nacimiento, estado, nombre_busqueda, creado_en, creado_por, actualizado_en) "
				+ "VALUES (?, ?, ?, 'DNI', ?, 'Puesta', 'Luis', '2019-05-01', 'ACTIVO', 'puesta luis', ?, 'prueba', ?)",
				colegioId, familia, apoderado, String.format("6%07d", colegioId % 10_000_000), ahora, ahora);
		Long alumno = jdbc.queryForObject("SELECT MAX(id) FROM alumno WHERE colegio_id = ?", Long.class, colegioId);
		jdbc.update("INSERT INTO matricula (colegio_id, alumno_id, anio_escolar_id, seccion_id, fecha_matricula, estado, "
				+ "creado_en, creado_por, actualizado_en) VALUES (?, ?, ?, ?, '2026-03-01', 'ACTIVA', ?, 'prueba', ?)",
				colegioId, alumno, anio, seccion, ahora, ahora);
		cuentaBancariaVigente(jdbc, colegioId, "promotor.pm", "direccion.pm");
		jdbc.update("INSERT INTO mensaje (colegio_id, clave, tipo, canal, destinatario_tipo, usuario_id, destino, plantilla, "
				+ "parametros, estado, intentos, proveedor, proveedor_mensaje_id, enviado_en, creado_en, creado_por, "
				+ "actualizado_en) VALUES (?, ?, 'ACTIVACION_CUENTA', 'CORREO', 'USUARIO', ?, 'promotoria@colegio.pe', "
				+ "'ACTIVACION', '', 'ENVIADO', 1, 'SIMULADO', ?, ?, ?, 'prueba', ?)", colegioId, "puesta-" + sufijo,
				promotorId, "SIM-" + sufijo, ahora, ahora, ahora);
		jdbc.update("INSERT INTO configuracion_colegio (colegio_id, clave, valor, creado_en, creado_por) VALUES (?, "
				+ "'huella_correo_externo', 'contador@estudio.pe', ?, 'operador.pruebas')", colegioId, ahora);
		jdbc.update("UPDATE colegio SET aviso_privacidad_version = '2026-1', aviso_privacidad_aprobado_por = 'direccion.pm', "
				+ "aviso_privacidad_aprobado_en = ? WHERE id = ?", ahora, colegioId);
		respaldoAlDia(jdbc);
	}

	/** Una cuenta bancaria VIGENTE (como si otra persona la hubiera aprobado). */
	public static void cuentaBancariaVigente(JdbcTemplate jdbc, long colegioId, String propuso, String aprobo) {
		LocalDateTime ahora = LocalDateTime.now(ZoneId.of("America/Lima"));
		jdbc.update("INSERT INTO conector_colegio (colegio_id, tipo, proveedor, modo, configuracion, clave_publica, estado, "
				+ "vigente, propuesto_por, resuelto_por, resuelto_en, prueba_ok, prueba_en, creado_en, creado_por, "
				+ "actualizado_en) VALUES (?, 'CUENTA_BANCARIA', 'BCP', 'REAL', ?, ?, 'VIGENTE', TRUE, ?, ?, ?, TRUE, ?, ?, ?, ?)",
				colegioId, "{\"numero\":\"1912345678901\",\"cci\":\"00219100123456789012\",\"moneda\":\"PEN\"}",
				UUID.randomUUID().toString(), propuso, aprobo, ahora, ahora, ahora, propuso, ahora);
	}

	/** Un respaldo de la instalación de hace una hora (al día). */
	public static void respaldoAlDia(JdbcTemplate jdbc) {
		LocalDateTime ahora = LocalDateTime.now(ZoneId.of("America/Lima"));
		String archivo = "cc-" + ahora.minusHours(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
				+ ".sql.gz.age";
		jdbc.update("INSERT INTO respaldo (inicio, fin, archivo, sha256, bytes, version_esquema, secuencia_antes, hash_antes, "
				+ "secuencia_despues, hash_despues, conteos, destino, comparacion, creado_en, creado_por) VALUES (?, ?, ?, ?, 1, "
				+ "'29', 0, ?, 0, ?, '{}', 'remoto', 'PRIMERO', ?, 'cc_respaldo')", ahora.minusHours(1).minusMinutes(1),
				ahora.minusHours(1), archivo, "a".repeat(64), "0".repeat(64), "0".repeat(64), ahora);
	}
}
