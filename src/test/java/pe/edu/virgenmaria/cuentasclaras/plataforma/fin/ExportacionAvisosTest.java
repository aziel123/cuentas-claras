package pe.edu.virgenmaria.cuentasclaras.plataforma.fin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.exportacion.proceso.LimpiezaExportaciones;
import pe.edu.virgenmaria.cuentasclaras.exportacion.service.Exportador;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 11, tanda 2 (AP2-13): cada paso de la exportación deja rastro y avisa al correo de control: la generación (con
 * sus huellas), cada descarga, el descifrado de la clave y el borrado del archivo a los 30 días (la fila queda VENCIDA,
 * sin clave ni enlace). Un pedido escrito lo crea un operador con su firma.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ExportacionAvisosTest extends BaseFinColegio {

	@Autowired
	private LimpiezaExportaciones limpieza;

	@Autowired
	private Exportador exportador;

	@Test
	void aLos30DiasSeBorraElArchivoYAvisa() {
		long colegio = colegioEnProduccion("Colegio Borrado", EscenarioPlataforma.RUC_D, "titular.borrado", "45678994",
				"987650094");
		suspender(colegio);
		Path ruta = pe.edu.virgenmaria.cuentasclaras.exportacion.ZipDePrueba.archivo(exportador, exportacion(colegio));
		assertThat(Files.exists(ruta)).isTrue();

		reloj.mover(Duration.ofDays(29));
		assertThat(limpieza.limpiar(colegio)).isZero();
		reloj.mover(Duration.ofDays(1).plusMinutes(1));
		assertThat(limpieza.limpiar(colegio)).isEqualTo(1);
		assertThat(Files.exists(ruta)).isFalse();
		assertThat(exportacion(colegio)).containsEntry("estado", "VENCIDA");
		assertThat(exportacion(colegio).get("sha256_zip")).isNotNull();
		assertThat(exportacion(colegio).get("token_hash")).isNull();
		assertThat(exportacion(colegio).get("clave_cifrada")).isNull();
		assertThat(jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE colegio_id = ?", String.class, colegio))
				.contains("EXPORTACION_BORRADA");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE colegio_id = ? AND plantilla = "
				+ "'EXPORTACION_MOVIMIENTO' AND destinatario_tipo = 'EXTERNO'", Long.class, colegio)).isPositive();
	}

	@Test
	void unPedidoEscritoLoFirmaUnOperadorYGeneraOtra() {
		long colegio = colegioEnProduccion("Colegio Pedido", EscenarioPlataforma.RUC_A, "titular.pedido", "45678995",
				"987650095");
		como(ana);
		Long id = fin.pedirExportacion(colegio, "Carta del colegio 2027-01");
		assertThat(jdbc.queryForMap("SELECT motivo, estado, creado_por FROM exportacion_colegio WHERE id = ?", id))
				.containsEntry("motivo", "PEDIDO").containsEntry("estado", "LISTA").containsEntry("creado_por",
						"operador.ana");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM firma_operacion WHERE clave = ?", Long.class,
				"exportacion_colegio:" + colegio + ":1")).isEqualTo(1L);
		// Una a la vez: con una LISTA no se pide otra.
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> fin.pedirExportacion(colegio, "Otra carta 2027-02"))
				.hasMessageContaining("ya tiene una exportación");
	}
}
