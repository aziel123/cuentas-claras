package pe.edu.virgenmaria.cuentasclaras.academico.libreta;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.edu.virgenmaria.cuentasclaras.conectores.cripto.SecretosConectores;
import pe.edu.virgenmaria.cuentasclaras.exportacion.model.MotivoExportacion;
import pe.edu.virgenmaria.cuentasclaras.exportacion.proceso.GeneracionExportaciones;
import pe.edu.virgenmaria.cuentasclaras.exportacion.service.Exportador;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 11, tanda 2 (3.9 y AP2-13): el ZIP trae las libretas emitidas, REGENERADAS y comparadas con su huella; el
 * {@code MANIFIESTO.csv} lista la SHA-256 de cada archivo, y la SHA-256 del ZIP y del manifiesto quedan en la exportación.
 * Si una libreta no coincide con su huella, la exportación queda FALLIDA (no se entrega algo distinto de lo que vio la
 * familia) y la alerta CRÍTICA llega también a los operadores.
 */
class ExportacionTest extends PruebaLibretas {

	@Autowired
	private GeneracionExportaciones generacion;

	@Autowired
	private Exportador exportador;

	@Autowired
	private SecretosConectores secretos;

	@Test
	void elManifiestoYLasHuellasCoincidenYLasLibretasSonLasEmitidas() throws Exception {
		anaPoneA();
		cerrarPeriodo();
		emitir();
		long libretas = jdbc.queryForObject("SELECT COUNT(*) FROM libreta_emitida WHERE colegio_id = 1", Long.class);
		assertThat(libretas).isPositive();

		Long id = generacion.exportar(1L, MotivoExportacion.PEDIDO, null);
		Map<String, Object> e = jdbc.queryForMap("SELECT * FROM exportacion_colegio WHERE id = ?", id);
		assertThat(e).containsEntry("estado", "LISTA");
		Path archivo = pe.edu.virgenmaria.cuentasclaras.exportacion.ZipDePrueba.archivo(exportador, e);
		assertThat(sha256(Files.readAllBytes(archivo))).isEqualTo(e.get("sha256_zip"));
		String clave = pe.edu.virgenmaria.cuentasclaras.exportacion.ZipDePrueba.clave(secretos, e);

		try (ZipFile zip = new ZipFile(archivo.toFile(), clave.toCharArray())) {
			Map<String, String> huellas = new HashMap<>();
			for (FileHeader h : zip.getFileHeaders()) {
				try (InputStream in = zip.getInputStream(h)) {
					huellas.put(h.getFileName(), sha256(in.readAllBytes()));
				}
			}
			assertThat(huellas.keySet().stream().filter(n -> n.startsWith("libretas/")).count()).isEqualTo(libretas);
			byte[] manifiesto;
			try (InputStream in = zip.getInputStream(zip.getFileHeader("MANIFIESTO.csv"))) {
				manifiesto = in.readAllBytes();
			}
			assertThat(sha256(manifiesto)).isEqualTo(e.get("sha256_manifiesto"));
			List<String> lineas = new String(manifiesto, 3, manifiesto.length - 3, StandardCharsets.UTF_8).lines().toList();
			assertThat(lineas.getFirst()).isEqualTo("archivo,filas,bytes,sha256");
			for (String linea : lineas.subList(1, lineas.size())) {
				String[] c = linea.split(",");
				assertThat(huellas.get(c[0])).as(c[0]).isEqualTo(c[3]);
			}
			assertThat(lineas).hasSize(huellas.size() - 2 + 1);
		}
	}

	@Test
	void unaLibretaAlteradaDejaLaExportacionFallidaYAvisaALosOperadores() {
		anaPoneA();
		cerrarPeriodo();
		emitir();
		jdbc.update("UPDATE libreta_emitida SET huella_contenido = ? WHERE id = (SELECT MIN(id) FROM libreta_emitida)",
				"f".repeat(64));

		Long id = generacion.exportar(1L, MotivoExportacion.PEDIDO, null);
		Map<String, Object> e = jdbc.queryForMap("SELECT * FROM exportacion_colegio WHERE id = ?", id);
		assertThat(e).containsEntry("estado", "FALLIDA");
		assertThat((String) e.get("detalle_fallo")).contains("no coincide con su huella");
		assertThat(e.get("sha256_zip")).isNull();
		assertThat(e.get("clave_cifrada")).isNull();
		assertThat(jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE colegio_id = 1", String.class))
				.contains("EXPORTACION_FALLIDA");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria e JOIN colegio c ON c.id = e.colegio_id "
				+ "WHERE c.tipo = 'PLATAFORMA' AND e.accion = 'EXPORTACION_FALLIDA'", Long.class)).isPositive();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE colegio_id = 1 AND plantilla = "
				+ "'EXPORTACION_FALLIDA'", Long.class)).isPositive();
	}

	private static String sha256(byte[] contenido) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
	}
}
