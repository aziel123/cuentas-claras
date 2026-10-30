package pe.edu.virgenmaria.cuentasclaras.plataforma.fin;

import net.lingala.zip4j.ZipFile;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 11, tanda 2 (3.9 y AP2-4): al aplicarse la suspensión, {@code sistema.exportacion} genera la exportación y la
 * entrega al titular: el enlace SOLO por correo, el DNI del contrato, el código SOLO por WhatsApp (atado a esa
 * exportación), la clave UNA vez ({@code EXPORTACION_CLAVE_DESCIFRADA}) y hasta 3 descargas.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class EntregaExportacionTest extends BaseFinColegio {

	private static final String DNI = "45678941";

	@Test
	void laSuspensionGeneraLaExportacionYLaEntregaPorDosCanales() throws Exception {
		long colegio = colegioEnProduccion("Colegio Entrega", EscenarioPlataforma.RUC_A, "titular.entrega", DNI,
				"987650041");
		suspender(colegio);

		Map<String, Object> e = exportacion(colegio);
		assertThat(e).containsEntry("motivo", "SUSPENSION").containsEntry("estado", "LISTA");
		assertThat((String) e.get("sha256_zip")).hasSize(64);
		assertThat((String) e.get("sha256_manifiesto")).hasSize(64);
		assertThat(e.get("clave_cifrada")).isNotNull();
		assertThat(e.get("token_hash")).isNotNull();
		// El enlace, SOLO por correo y al titular; las huellas, al correo de control (nunca el enlace ni la clave).
		assertThat(jdbc.queryForList("SELECT canal FROM mensaje WHERE colegio_id = ? AND tipo = 'EXPORTACION_ENLACE'",
				String.class, colegio)).containsExactly("CORREO");
		assertThat(jdbc.queryForList("SELECT parametros FROM mensaje WHERE colegio_id = ? AND plantilla = 'EXPORTACION_LISTA'",
				String.class, colegio)).isNotEmpty().allSatisfy(p -> assertThat(p).contains((String) e.get("sha256_zip")));

		String token = token(colegio);
		// Sin DNI no hay código; con otro DNI, un intento fallido.
		assertThatThrownBy(() -> entrega.confirmarCodigo(token, "123456")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("DNI");
		assertThatThrownBy(() -> entrega.confirmarDni(token, "11111111")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("Te quedan 4");
		entrega.confirmarDni(token, DNI);
		assertThat(jdbc.queryForList("SELECT canal FROM mensaje WHERE colegio_id = ? AND tipo = 'CODIGO_EXPORTACION'",
				String.class, colegio)).containsExactly("WHATSAPP");
		String codigo = codigo(colegio);

		String clave = entrega.confirmarCodigo(token, codigo);
		assertThat(clave).matches("([A-Za-z2-9]{4}-){5}[A-Za-z2-9]{4}");
		// Una sola vez: se borró de la base y quedó en la bitácora.
		assertThat(entrega.confirmarCodigo(token, codigo)).isNull();
		assertThat(exportacion(colegio).get("clave_cifrada")).isNull();
		assertThat(jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE colegio_id = ?", String.class, colegio))
				.contains("EXPORTACION_COLEGIO_GENERADA", "EXPORTACION_ENTREGA_INICIADA", "EXPORTACION_CODIGO_ENVIADO",
						"EXPORTACION_CLAVE_DESCIFRADA", "EXPORTACION_INTENTO_FALLIDO");

		// El ZIP se abre con la clave y trae su manifiesto y su constancia.
		Path archivo = entrega.descargar(token, codigo, "203.0.113.7");
		try (ZipFile zip = new ZipFile(archivo.toFile(), clave.replace("-", "").toCharArray())) {
			assertThat(zip.isEncrypted()).isTrue();
			assertThat(zip.getFileHeaders()).extracting(h -> h.getFileName()).contains("MANIFIESTO.csv", "LEEME.pdf",
					"datos/alumno.csv", "datos/evento_auditoria.csv");
		}
		assertThat(exportacion(colegio)).containsEntry("estado", "ENTREGADA").containsEntry("descargas", 1);
		entrega.descargar(token, codigo, "203.0.113.7");
		entrega.descargar(token, codigo, "203.0.113.7");
		assertThatThrownBy(() -> entrega.descargar(token, codigo, "203.0.113.7")).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("3 veces");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM descarga_exportacion WHERE colegio_id = ?", Long.class,
				colegio)).isEqualTo(3L);
		// AP2-13: cada descarga y el descifrado avisan al correo de control.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE colegio_id = ? AND plantilla = "
				+ "'EXPORTACION_MOVIMIENTO' AND destinatario_tipo = 'EXTERNO'", Long.class, colegio)).isGreaterThanOrEqualTo(4L);
	}

	@Test
	void elCodigoEstaAtadoASuExportacionYVence() {
		long colegio = colegioEnProduccion("Colegio Codigo", EscenarioPlataforma.RUC_B, "titular.codigo", DNI, "987650042");
		suspender(colegio);
		String token = token(colegio);
		entrega.confirmarDni(token, DNI);
		String codigo = codigo(colegio);
		// A los 10 minutos el código venció: hay que volver a escribir el DNI.
		reloj.mover(java.time.Duration.ofMinutes(11));
		assertThatThrownBy(() -> entrega.confirmarCodigo(token, codigo)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("venció");
		// Un enlace que no existe no sirve (y no revela de qué colegio sería).
		assertThatThrownBy(() -> entrega.ver("x".repeat(43))).hasMessageContaining("no existe");
	}
}
