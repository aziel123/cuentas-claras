package pe.edu.virgenmaria.cuentasclaras.academico.libreta;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.EmisorLibretas;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 10, tanda 3 (secciones 3.7 y 3.13; sección 3 del brief; decisión 191): la emisión de libretas por
 * {@code sistema.libretas}.
 */
class EmisionLibretasTest extends PruebaLibretas {

	/** C1: una por matrícula ACTIVA o RESERVADA, ninguna a la RETIRADA; un solo evento por sección (decisión 191). */
	@Test
	void unaPorMatriculaActivaOReservadaYUnEventoPorSeccion() {
		anaPoneA();
		jdbc.update("UPDATE matricula SET estado = 'RESERVADA' WHERE id = ?", valeria);
		jdbc.update("UPDATE matricula SET estado = 'RETIRADA', retirada_en = ? WHERE id = ?", java.time.LocalDate.of(2026, 4,
				30), lucas);
		cerrarPeriodo();
		EmisorLibretas.Resultado r = emitir();
		assertThat(r.alCierre()).isEqualTo(2);
		assertThat(contar("SELECT COUNT(*) FROM libreta_emitida WHERE matricula_id IN (?, ?)", mateo, valeria))
				.isEqualTo(2);
		assertThat(contar("SELECT COUNT(*) FROM libreta_emitida WHERE matricula_id = ?", lucas)).isZero();
		assertThat(jdbc.queryForList("SELECT creado_por FROM libreta_emitida", String.class)).containsOnly(
				"sistema.libretas");
		List<String> eventos = eventos("LIBRETAS_EMITIDAS");
		assertThat(eventos).hasSize(1);
		assertThat(eventos.get(0)).contains("2 emitida(s)").doesNotContain("Mateo").doesNotContain("Valeria");
		// Una pasada repetida no emite de nuevo.
		assertThat(emitir().total()).isZero();
		assertThat(contar("SELECT COUNT(*) FROM libreta_emitida")).isEqualTo(2);
	}

	/**
	 * Sección 3 del brief: una corrección aprobada ANTES de que existieran libretas (como las de la tanda 2) no se esconde
	 * en la versión 1: la primera pasada emite la 1 (lo del cierre) y la 2 (con lo que cambió), y la familia recibe UN solo
	 * aviso, el de libreta corregida.
	 */
	@Test
	void correccionDeLaTanda2DaVersion1Y2YUnSoloAviso() {
		anaPoneA();
		cerrarPeriodo();
		Long competencia = competencias("MAT").get(0);
		corregir(lucas, competencia, "AD", null);
		EmisorLibretas.Resultado r = emitir();
		assertThat(r.alCierre()).isEqualTo(3);
		assertThat(r.corregidas()).isEqualTo(1);
		List<Map<String, Object>> deLucas = jdbc.queryForList("SELECT numero_version, motivo, cambios, reemplaza_id FROM "
				+ "libreta_emitida WHERE matricula_id = ? ORDER BY numero_version", lucas);
		assertThat(deLucas).hasSize(2);
		assertThat(deLucas.get(0)).containsEntry("MOTIVO", "CIERRE").containsEntry("CAMBIOS", null);
		assertThat(deLucas.get(1)).containsEntry("MOTIVO", "CORRECCION");
		assertThat((String) deLucas.get(1).get("CAMBIOS")).contains("Matemática · ").contains("A → AD");
		// La versión 1 conserva lo informado al cierre (A) y la 2 trae la corrección (AD).
		assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'LIBRETA_CORREGIDA_EMITIDA'")).isEqualTo(1);
		assertThat(avisar()).isEqualTo(2);
		assertThat(contar("SELECT COUNT(*) FROM mensaje WHERE familia_id = ? AND tipo LIKE 'LIBRETA%'", familiaPedro))
				.as("un solo aviso a la familia de Lucas").isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT tipo FROM mensaje WHERE familia_id = ? AND tipo LIKE 'LIBRETA%'",
				String.class, familiaPedro)).isEqualTo("LIBRETA_CORREGIDA");
		// Rosa recibe UNO por sus dos hijos (los hermanos juntos).
		assertThat(contar("SELECT COUNT(*) FROM mensaje WHERE familia_id = ? AND tipo = 'LIBRETA_DISPONIBLE'", familiaRosa))
				.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT parametros FROM mensaje WHERE familia_id = ? AND tipo = 'LIBRETA_DISPONIBLE'",
				String.class, familiaRosa)).contains("I bimestre").contains("Mateo y Valeria");
		// Una pasada repetida de avisos no duplica.
		assertThat(avisar()).isZero();
	}

	/** Una corrección después de emitir: versión siguiente y un aviso nuevo de libreta corregida. */
	@Test
	void correccionDespuesDeEmitirDaLaVersionSiguienteConSuAviso() {
		cerrarYEmitir();
		avisar();
		Long competencia = competencias("MAT").get(0);
		corregir(mateo, competencia, "AD", null);
		assertThat(emitir().corregidas()).isEqualTo(1);
		assertThat(contar("SELECT MAX(numero_version) FROM libreta_emitida WHERE matricula_id = ?", mateo)).isEqualTo(2);
		assertThat(avisar()).isEqualTo(1);
		assertThat(contar("SELECT COUNT(*) FROM mensaje WHERE familia_id = ? AND tipo = 'LIBRETA_CORREGIDA'", familiaRosa))
				.isEqualTo(1);
		// Las dos versiones siguen en el portal, la 2 vigente.
		var vista = como(rosaEnLinea(), () -> portal.lista());
		var mateoEnElPortal = vista.hijos().stream().filter(h -> h.nombre().equals("Mateo")).findFirst().orElseThrow();
		assertThat(mateoEnElPortal.periodos().get(0).versiones()).extracting(v -> v.numero() + ":" + v.vigente())
				.containsExactly("2:true", "1:false");
	}

	/** Un colegio en preparación emite sus libretas pero no avisa (sección 3.12). */
	@Test
	void enPreparacionSeEmitenPeroNoSeAvisan() {
		cerrarYEmitir();
		jdbc.update("UPDATE colegio SET estado = 'PREPARACION', produccion_desde = NULL, produccion_por = NULL WHERE id = 1");
		try {
			assertThat(contar("SELECT COUNT(*) FROM libreta_emitida")).isEqualTo(3);
			assertThat(avisar()).isZero();
			assertThat(contar("SELECT COUNT(*) FROM mensaje WHERE tipo LIKE 'LIBRETA%'")).isZero();
		}
		finally {
			jdbc.update("UPDATE colegio SET estado = 'PRODUCCION', produccion_desde = CURRENT_TIMESTAMP, produccion_por = "
					+ "'prueba' WHERE id = 1");
		}
	}
}
