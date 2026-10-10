package pe.edu.virgenmaria.cuentasclaras.operacion.respaldo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.CamposSellados;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.SelladorAuditoria;
import pe.edu.virgenmaria.cuentasclaras.operacion.respaldo.VerificadorRespaldo.Comprobacion;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 7, tanda 1: el modo {@code verificar-respaldo} sobre una copia restaurada (aquí H2; en el CI, MySQL real con el
 * job {@code respaldo}). Una copia sana pasa; un evento cambiado en el volcado (E31), una bitácora recortada (E32), un
 * ancla de un respaldo anterior que ya no está, filas que faltan y un libro inconsistente fallan.
 */
class VerificadorRespaldoTest {

	private static final String CLAVE = "clave-hmac-de-pruebas-no-usar-en-produccion-0001";

	private static final String CERO = "0".repeat(64);

	private final String url = "jdbc:h2:mem:respaldo-" + UUID.randomUUID()
			+ ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";

	private final SelladorAuditoria sellador = new SelladorAuditoria(CLAVE);

	private final List<String> hashes = new ArrayList<>();

	/** Abierta durante toda la prueba: H2 compila los CHECK en la sesiÃ³n que los crea. */
	private Connection abierta;

	@org.junit.jupiter.api.AfterEach
	void cerrar() throws SQLException {
		abierta.close();
	}

	@BeforeEach
	void copiaRestaurada() throws SQLException {
		abierta = DriverManager.getConnection(url, "sa", "");
		org.flywaydb.core.Flyway.configure()
				.dataSource(new org.springframework.jdbc.datasource.SingleConnectionDataSource(abierta, true))
				.locations("classpath:db/migration").load().migrate();


		try (Connection conexion = conexion()) {
			String anterior = CERO;
			for (long secuencia = 1; secuencia <= 3; secuencia++) {
				CamposSellados campos = new CamposSellados(secuencia, 1L, LocalDateTime.of(2026, 10, 8, 9, 0, 0, 123456000),
						null, "promotora", "PROMOTOR", "INTEGRIDAD_VERIFICADA", "evento_auditoria", null, null, "INTEGRA",
						"evento " + secuencia, "127.0.0.1");
				String hash = sellador.sellar(anterior, campos);
				try (PreparedStatement insercion = conexion.prepareStatement("INSERT INTO evento_auditoria (secuencia, "
						+ "colegio_id, ocurrido_en, nombre_usuario, roles, accion, entidad, valor_nuevo, detalle, ip, hash) "
						+ "VALUES (?, 1, ?, 'promotora', 'PROMOTOR', 'INTEGRIDAD_VERIFICADA', 'evento_auditoria', 'INTEGRA', ?, "
						+ "'127.0.0.1', ?)")) {
					insercion.setLong(1, secuencia);
					insercion.setObject(2, campos.ocurridoEn());
					insercion.setString(3, campos.detalle());
					insercion.setString(4, hash);
					insercion.executeUpdate();
				}
				hashes.add(hash);
				anterior = hash;
			}
			ejecutar(conexion, "UPDATE auditoria_cadena SET ultima_secuencia = 3, ultimo_hash = '" + hashes.get(2) + "'");
			ejecutar(conexion, "INSERT INTO huella_bitacora (colegio_id, fecha, secuencia, codigo, eventos_del_dia, "
					+ "creado_en, creado_por, actualizado_en) VALUES (1, '2026-10-08', 2, '" + hashes.get(1).substring(0, 16)
					+ "', 2, NOW(6), 'sistema.auditoria', NOW(6))");
		}
	}

	@Test
	void unaCopiaSanaPasaTodasLasComprobaciones() throws Exception {
		List<Comprobacion> resultado = verificar(manifiesto(2, 3), List.of(manifiesto(1, 1)), sellador);

		assertThat(resultado).allSatisfy(c -> assertThat(c.paso()).as(c.nombre() + ": " + c.detalle()).isTrue());
		assertThat(resultado).extracting(Comprobacion::nombre).contains("version_esquema", "secuencias_seguidas",
				"eslabon_en_el_ultimo_evento", "ancla_antes", "ultimo_evento_entre_anclas", "hmac", "conteos", "huellas",
				"libros_cuota_pagado_es_su_libro", "libros_series_sin_huecos");
	}

	/** E31: un evento cambiado en el volcado. Sin la clave no se nota; con la clave, «no verifica en la secuencia 2». */
	@Test
	void unEventoCambiadoEnElVolcadoNoVerificaConLaClave() throws Exception {
		try (Connection conexion = conexion()) {
			ejecutar(conexion, "UPDATE evento_auditoria SET detalle = 'cambiado' WHERE secuencia = 2");
		}

		assertThat(verificar(manifiesto(2, 3), List.of(), null)).allSatisfy(c -> assertThat(c.paso()).isTrue());
		assertThat(verificar(manifiesto(2, 3), List.of(), sellador)).filteredOn(c -> !c.paso()).singleElement()
				.satisfies(c -> assertThat(c.detalle()).contains("La cadena no verifica en la secuencia 2"));
	}

	/** E32: se recortó el final de la bitácora. El eslabón, los conteos y el ancla del respaldo anterior lo delatan. */
	@Test
	void unaBitacoraRecortadaNoPasa() throws Exception {
		try (Connection conexion = conexion()) {
			ejecutar(conexion, "DELETE FROM evento_auditoria WHERE secuencia = 3");
		}

		List<Comprobacion> resultado = verificar(manifiesto(2, 3), List.of(manifiesto(3, 3)), null);

		assertThat(resultado).filteredOn(c -> !c.paso()).extracting(Comprobacion::nombre)
				.contains("eslabon_en_el_ultimo_evento", "conteos", "ancla_de_cc-20261008-000003.sql.gz.age");
	}

	@Test
	void huecosEnLaSecuenciaHuellasQueNoCoincidenYOtraVersionFallan() throws Exception {
		try (Connection conexion = conexion()) {
			ejecutar(conexion, "DELETE FROM evento_auditoria WHERE secuencia = 2");
		}
		JsonNode otraVersion = manifiesto(1, 3, "23");

		assertThat(verificar(otraVersion, List.of(), null)).filteredOn(c -> !c.paso()).extracting(Comprobacion::nombre)
				.contains("version_esquema", "secuencias_seguidas", "huellas");
	}

	@Test
	void unLibroInconsistenteFalla() throws Exception {
		try (Connection conexion = conexion()) {
			// Una serie que dice haber emitido 5 boletas sin ninguna en el libro: faltan comprobantes.
			ejecutar(conexion, "INSERT INTO serie_comprobante (colegio_id, tipo, serie, proveedor, ultimo_numero, creado_en, "
					+ "creado_por, actualizado_en) VALUES (1, 'BOLETA', 'B001', 'SIMULADO', 5, NOW(6), 'x', NOW(6))");
		}

		assertThat(verificar(manifiesto(2, 3), List.of(), null)).filteredOn(c -> !c.paso())
				.extracting(Comprobacion::nombre).containsExactly("libros_series_sin_huecos");
	}

	@Test
	void desdeElEntornoDevuelveElCodigoYEscribeElInforme(@TempDir Path carpeta) throws Exception {
		Path manifiesto = carpeta.resolve("manifiesto.json");
		Files.writeString(manifiesto, manifiesto(2, 3).toString());
		Path anteriores = Files.createDirectory(carpeta.resolve("anteriores"));
		Files.writeString(anteriores.resolve("cc-20261007-000001.json"), manifiesto(1, 1).toString());
		Path informe = carpeta.resolve("informe.json");
		Map<String, String> entorno = Map.of("DB_URL", url, "DB_USUARIO", "sa", "DB_CLAVE", "",
				"RESPALDO_MANIFIESTO", manifiesto.toString(), "RESPALDO_MANIFIESTOS_ANTERIORES", anteriores.toString(),
				"AUDITORIA_CLAVE_HMAC", CLAVE, "RESPALDO_INFORME", informe.toString());

		assertThat(VerificadorRespaldo.ejecutarDesdeEntorno(entorno)).isZero();
		assertThat(JsonMapper.builder().build().readTree(Files.readString(informe)).get("verificado").asBoolean()).isTrue();

		try (Connection conexion = conexion()) {
			ejecutar(conexion, "UPDATE evento_auditoria SET detalle = 'cambiado' WHERE secuencia = 1");
		}
		assertThat(VerificadorRespaldo.ejecutarDesdeEntorno(entorno)).isEqualTo(1);
		assertThat(Files.readString(informe)).contains("La cadena no verifica en la secuencia 1");
		assertThat(VerificadorRespaldo.ejecutarDesdeEntorno(Map.of("DB_URL", url))).isEqualTo(2);
	}

	@Test
	void lasSentenciasDelArchivoSeSeparanSinComentarios() {
		assertThat(VerificadorRespaldo.sentencias("-- comentario\nSELECT 'a' AS control, 0 AS problemas;\n\n"
				+ "SELECT 'b', COUNT(*)\n FROM x;\n")).containsExactly("SELECT 'a' AS control, 0 AS problemas",
						"SELECT 'b', COUNT(*)\n FROM x");
	}

	private List<Comprobacion> verificar(JsonNode manifiesto, List<JsonNode> anteriores, SelladorAuditoria clave)
			throws SQLException {
		try (Connection conexion = conexion()) {
			return VerificadorRespaldo.verificar(conexion, manifiesto, anteriores, clave);
		}
	}

	private JsonNode manifiesto(long antes, long despues) {
		return manifiesto(antes, despues, "27");
	}

	private JsonNode manifiesto(long antes, long despues, String version) {
		return JsonMapper.builder().build().valueToTree(Map.of("archivo",
				"cc-20261008-00000" + despues + ".sql.gz.age", "version_esquema", version, "secuencia_antes", antes,
				"hash_antes", hashes.get((int) antes - 1), "secuencia_despues", despues, "hash_despues",
				hashes.get((int) despues - 1), "conteos", Map.of("evento_auditoria", List.of(3, 3), "pago", List.of(0, 0))));
	}

	private Connection conexion() throws SQLException {
		return DriverManager.getConnection(url, "sa", "");
	}

	private static void ejecutar(Connection conexion, String sql) throws SQLException {
		try (Statement sentencia = conexion.createStatement()) {
			sentencia.execute(sql);
		}
	}
}
