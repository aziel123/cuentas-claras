package pe.edu.virgenmaria.cuentasclaras.plataforma.fin;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 11, tanda 2 (AP2-10 y 3.11): la retención legal la pone un operador con su firma y la quita OTRO; mientras está,
 * el acta de supresión no se propone ni se aprueba. El acta la propone un operador y la aprueba otro; la verificación
 * completa de la cadena queda anotada en el acta (AP2-8).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class RetencionLegalTest extends BaseFinColegio {

	private static final String MOTIVO_RETENCION = "Oficio 245-2027 del Juzgado Civil de Lima: conservar los registros.";

	private static final String MOTIVO_ACTA = "Venció el plazo de conservación del colegio cerrado: se suprimen sus datos.";

	@Test
	void conRetencionNoHayActaYLaQuitaOtroOperador() {
		long colegio = colegioCerrado("Colegio Retencion", EscenarioPlataforma.RUC_A, "titular.retencion", "45678991",
				"987650091");
		LocalDate respaldos = LocalDate.now(reloj).plusMonths(12);

		como(ana);
		assertThatThrownBy(() -> fin.ponerRetencion(colegio, "corto")).isInstanceOf(ReglaNegocioException.class);
		fin.ponerRetencion(colegio, MOTIVO_RETENCION);
		Map<String, Object> c = jdbc.queryForMap("SELECT retencion_legal_motivo, retencion_legal_por, retencion_legal_desde "
				+ "FROM colegio WHERE id = ?", colegio);
		assertThat(c).containsEntry("retencion_legal_por", "operador.ana").containsEntry("retencion_legal_motivo",
				MOTIVO_RETENCION);
		assertThat(c.get("retencion_legal_desde")).isNotNull();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM firma_operacion WHERE clave = ?", Long.class,
				"colegio:" + colegio + ":RETENCION_LEGAL")).isEqualTo(1L);
		assertThatThrownBy(() -> fin.proponerActa(colegio, MOTIVO_ACTA, respaldos)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("retención legal");
		// Quien la puso no la quita.
		assertThatThrownBy(() -> fin.quitarRetencion(colegio)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("otro operador");

		como(beto);
		fin.quitarRetencion(colegio);
		assertThat(jdbc.queryForObject("SELECT retencion_legal_por FROM colegio WHERE id = ?", String.class, colegio))
				.isNull();
		assertThat(jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE colegio_id = ?", String.class, colegio))
				.contains("RETENCION_LEGAL_PUESTA", "RETENCION_LEGAL_QUITADA");
	}

	@Test
	void elActaLaProponeUnoLaApruebaOtroYLaCadenaQuedaAnotada() {
		long colegio = colegioCerrado("Colegio Acta", EscenarioPlataforma.RUC_B, "titular.acta", "45678992", "987650092");
		como(ana);
		// AP2-12: los respaldos con datos no pasan de 24 meses desde el inicio del plazo.
		assertThatThrownBy(() -> fin.proponerActa(colegio, MOTIVO_ACTA, LocalDate.now(reloj).plusMonths(30)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("24 meses");
		Long acta = fin.proponerActa(colegio, MOTIVO_ACTA, LocalDate.now(reloj).plusMonths(12));
		Map<String, Object> a = jdbc.queryForMap("SELECT * FROM acta_supresion WHERE id = ?", acta);
		assertThat(a).containsEntry("estado", "PROPUESTA").containsEntry("tipo", "CIERRE")
				.containsEntry("propuesto_por", "operador.ana");
		assertThat(a.get("inicio_plazo")).isNotNull();
		assertThatThrownBy(() -> fin.aprobarActa(colegio, acta)).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("otro operador");
		assertThatThrownBy(() -> fin.verificarCadena(colegio)).isInstanceOf(ReglaNegocioException.class);

		como(beto);
		fin.aprobarActa(colegio, acta);
		fin.verificarCadena(colegio);
		a = jdbc.queryForMap("SELECT * FROM acta_supresion WHERE id = ?", acta);
		assertThat(a).containsEntry("estado", "APROBADA").containsEntry("aprobado_por", "operador.beto");
		assertThat(a.get("cadena_verificada_en")).isNotNull();
		assertThat(jdbc.queryForList("SELECT clave FROM firma_operacion WHERE clave LIKE 'acta_supresion:%'", String.class))
				.contains("acta_supresion:" + colegio, "acta_supresion:" + acta + ":APROBADA");
		assertThat(jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE colegio_id = ?", String.class, colegio))
				.contains("ACTA_SUPRESION_PROPUESTA", "ACTA_SUPRESION_APROBADA", "ACTA_CADENA_VERIFICADA");
		// El acta para imprimir explica la huella.
		assertThat(fin.actaPdf(colegio)).startsWith("%PDF".getBytes());
	}

	private long colegioCerrado(String nombre, String ruc, String titular, String dni, String celular) {
		long colegio = colegioEnProduccion(nombre, ruc, titular, dni, celular);
		suspender(colegio);
		recibir(colegio, dni);
		cerrar(colegio);
		assertThat(estado(colegio)).isEqualTo("CERRADO");
		return colegio;
	}
}
