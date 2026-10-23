package pe.edu.virgenmaria.cuentasclaras.comunicados;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.texto.ContenidoSinCobranza;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.AvisosComunicados;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.AvisosComunicadosFamilias;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.BorradorComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.AlcanceComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EmisorRol;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 9, tanda 3 (sección 3.10, decisiones 158 a 160; E42, E44; B6): los avisos de comunicados que prepara
 * sistema.comunicados. Diez comunicados del día, un solo resumen por familia (a la hora del colegio); el urgente sale
 * entre las 07:00 y las 20:00; a las 48 horas, el recordatorio de los que siguen sin confirmar; el pie no va en los
 * parámetros (dice «pagos»); un nombre que calza con la regla de cobranza sale genérico.
 */
class AvisosComunicadosTest extends PruebaComunicados {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");

	@Autowired
	AvisosComunicados avisos;

	private AvisosComunicadosFamilias.Resultado pasada(LocalDateTime ahora) {
		reloj.fijar(ahora.atZone(LIMA).toInstant());
		return avisos.enColegio(1L, ahora);
	}

	@Test
	void diezComunicadosUnSoloResumen() {
		for (int i = 1; i <= 10; i++) {
			int n = i;
			como(EscenarioCobranza.DIRECCION, () -> envio.enviar(aLaSeccion(EmisorRol.DIRECCION, seccionA,
					"Aviso número " + n + " de la semana"), null));
		}
		assertThat(pasada(LocalDateTime.of(2026, 10, 6, 9, 0)).resumenes()).as("antes de la hora del resumen").isZero();
		AvisosComunicadosFamilias.Resultado r = pasada(LocalDateTime.of(2026, 10, 6, 18, 5));
		assertThat(r.resumenes()).isEqualTo(2);
		List<Map<String, Object>> deRosa = jdbc.queryForList("SELECT canal, parametros FROM mensaje WHERE tipo = "
				+ "'COMUNICADOS_RESUMEN' AND familia_id = ?", familiaRosa);
		assertThat(deRosa).isNotEmpty();
		assertThat(String.valueOf(deRosa.getFirst().get("parametros"))).contains("10 comunicados nuevos")
				.contains("Dirección");
		assertThat(contar("SELECT COUNT(*) FROM comunicado_destinatario WHERE familia_id = ? AND avisado_mensaje_id IS "
				+ "NULL", familiaRosa)).as("los diez quedaron avisados").isZero();
		// Otra pasada el mismo día no crea otro resumen.
		assertThat(pasada(LocalDateTime.of(2026, 10, 6, 18, 10)).resumenes()).isZero();
	}

	@Test
	void elPieNoVaEnLosParametros() {
		como(EscenarioCobranza.DIRECCION, () -> envio.enviar(aTodoElColegio(EmisorRol.DIRECCION, "Día del Logro"), null));
		pasada(LocalDateTime.of(2026, 10, 6, 18, 5));
		List<String> parametros = jdbc.queryForList("SELECT parametros FROM mensaje WHERE tipo = 'COMUNICADOS_RESUMEN'",
				String.class);
		assertThat(parametros).isNotEmpty().allSatisfy(p -> {
			assertThat(p).doesNotContain("pagos").doesNotContain("Día del Logro");
			assertThat(ContenidoSinCobranza.hablaDeCobranza(p.substring(p.indexOf('\n') + 1))).isFalse();
		});
	}

	@Test
	void urgenteALas7() {
		reloj.fijar(LocalDateTime.of(2026, 10, 6, 21, 0).atZone(LIMA).toInstant());
		como(EscenarioCobranza.DIRECCION, () -> envio.enviar(new BorradorComunicado(EmisorRol.DIRECCION,
				AlcanceComunicado.COLEGIO, null, null, null, "Suspensión de clases mañana", TEXTO, true, true), null));
		assertThat(pasada(LocalDateTime.of(2026, 10, 6, 21, 0)).urgentes()).as("después de las 20:00").isZero();
		assertThat(pasada(LocalDateTime.of(2026, 10, 7, 6, 55)).urgentes()).as("antes de las 07:00").isZero();
		AvisosComunicadosFamilias.Resultado r = pasada(LocalDateTime.of(2026, 10, 7, 7, 0));
		assertThat(r.urgentes()).isEqualTo(3);
		assertThat(jdbc.queryForList("SELECT parametros FROM mensaje WHERE tipo = 'COMUNICADO_URGENTE'", String.class))
				.isNotEmpty().allMatch(p -> p.contains("Suspensión de clases mañana") && p.contains("Dirección"));
	}

	@Test
	void recordatorioALas48Horas() {
		Long id = como(EscenarioCobranza.DIRECCION, () -> envio.enviar(aLaSeccion(EmisorRol.DIRECCION, seccionA,
				"Reunión de 5.° A"), null));
		pasada(LocalDateTime.of(2026, 10, 6, 18, 5));
		como(rosaEnLinea(), () -> {
			lectura.confirmar(id);
			return null;
		});
		assertThat(pasada(LocalDateTime.of(2026, 10, 7, 18, 5)).resumenes()).as("a las 24 horas, nada").isZero();
		AvisosComunicadosFamilias.Resultado r = pasada(LocalDateTime.of(2026, 10, 9, 18, 30));
		assertThat(r.resumenes()).as("solo la familia que no confirmó").isEqualTo(1);
		assertThat(contar("SELECT COUNT(*) FROM comunicado_destinatario WHERE comunicado_id = ? AND familia_id = ? AND "
				+ "recordado_mensaje_id IS NOT NULL", id, familiaPedro)).isEqualTo(1);
		assertThat(contar("SELECT COUNT(*) FROM comunicado_destinatario WHERE comunicado_id = ? AND familia_id = ? AND "
				+ "recordado_mensaje_id IS NOT NULL", id, familiaRosa)).isZero();
		assertThat(jdbc.queryForList("SELECT parametros FROM mensaje WHERE tipo = 'COMUNICADOS_RESUMEN' AND familia_id = ? "
				+ "ORDER BY id DESC", String.class, familiaPedro).getFirst()).contains("1 por confirmar");
	}

	/** B6: si el nombre del docente calza con la regla de cobranza, el resumen dice «su docente». */
	@Test
	void nombreQueCalzaUsaEtiquetaGenerica() {
		assertThat(AvisosComunicadosFamilias.emisorCorto("Prof. Yape T., tutor(a) de 5.° Primaria A")).isEqualTo("su docente");
		assertThat(AvisosComunicadosFamilias.emisorCorto("Prof. Ana T., tutor(a) de 5.° Primaria A")).isEqualTo("Prof. Ana T.");
		assertThat(AvisosComunicadosFamilias.emisorCorto("Dirección del Colegio Cobranza Feliz")).isEqualTo("Dirección");
	}
}
