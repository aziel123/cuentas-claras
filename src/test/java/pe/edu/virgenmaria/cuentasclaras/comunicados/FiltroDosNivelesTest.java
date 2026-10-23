package pe.edu.virgenmaria.cuentasclaras.comunicados;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.dto.SolicitudVista;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.AvisosComunicados;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.BorradorComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.AlcanceComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EmisorRol;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Decisión del usuario sobre los falsos positivos del filtro (correcciones del sprint 9): DOS niveles. La cobranza clara se
 * rechaza; las palabras ambiguas mandan el comunicado a aprobación del otro órgano (aunque lo envíe Dirección o
 * Promotoría), que ve resaltada la palabra y el motivo, y al aprobarlo sale a las familias.
 */
class FiltroDosNivelesTest extends PruebaComunicados {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");

	@Autowired
	private AvisosComunicados avisos;

	private String estado(Long id) {
		return jdbc.queryForObject("SELECT estado FROM comunicado WHERE id = ?", String.class, id);
	}

	private static BorradorComunicado deDireccion(String titulo, String cuerpo, boolean urgente) {
		return new BorradorComunicado(EmisorRol.DIRECCION, AlcanceComunicado.COLEGIO, null, null, null, titulo, cuerpo,
				urgente, true);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"Reunión de padres | Estimadas familias: debemos cancelar la reunión del viernes por el feriado. | cancelar",
			"Tareas del bimestre | Estimadas familias: que los alumnos estén al día con las tareas del bimestre. | al día con",
			"Biohuerto escolar | Estimadas familias: traigan abono para el biohuerto el jueves, por favor. | abono" })
	void debeMandarLaAmbiguaAAprobacionYEnviarlaAlAprobarse(String titulo, String cuerpo, String palabra) {
		Long id = como(EscenarioCobranza.DIRECCION, () -> envio.enviar(deDireccion(titulo, cuerpo, false), null));
		assertThat(estado(id)).isEqualTo("POR_APROBAR");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comunicado_destinatario WHERE comunicado_id = ?", Long.class,
				id)).isZero();
		// Quien aprueba ve la palabra resaltada y el motivo.
		Long solicitud = EscenarioAprobaciones.pendiente(jdbc, "comunicado", id);
		SolicitudVista vista = como(EscenarioCobranza.PROMOTORIA, () -> bandeja.detalle(solicitud));
		assertThat(vista.advertencia()).contains("«" + palabra).contains("puede referirse a cobranza");
		assertThat(vista.detalle()).anySatisfy(l -> assertThat(l).startsWith("Palabra que lo envió a aprobación: «"
				+ palabra).contains("puede referirse a cobranza"));
		EscenarioAprobaciones.aprueba(EscenarioCobranza.PROMOTORIA, bandeja, jdbc, "comunicado", id);
		assertThat(estado(id)).isEqualTo("ENVIADO");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comunicado_destinatario WHERE comunicado_id = ?", Long.class,
				id)).isEqualTo(3);
	}

	@Test
	void debeRechazarLaCobranzaClara() {
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> envio.enviar(deDireccion("Exámenes bimestrales",
				"Estimadas familias: los alumnos que no estén al día con la mensualidad no podrán rendir el examen.",
				false), null))).isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> envio.enviar(deDireccion("Paseo de fin de año",
				"Estimadas familias: para el paseo traigan S/ 300 en un sobre cerrado.", false), null)))
				.isInstanceOf(ReglaNegocioException.class);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comunicado", Long.class)).isZero();
	}

	@Test
	void debePedirLaAprobacionDePromotoriaCuandoDireccionEscribeRegularicen() {
		Long id = como(EscenarioCobranza.DIRECCION, () -> envio.enviar(deDireccion("Inscripción al taller",
				"Estimadas familias: regularicen la inscripción al taller de música antes del lunes.", false), null));
		assertThat(estado(id)).isEqualTo("POR_APROBAR");
		assertThatThrownBy(() -> EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "comunicado",
				id)).as("Dirección no aprueba el suyo").isInstanceOf(RuntimeException.class);
		assertThat(estado(id)).isEqualTo("POR_APROBAR");
		EscenarioAprobaciones.aprueba(EscenarioCobranza.PROMOTORIA, bandeja, jdbc, "comunicado", id);
		assertThat(estado(id)).isEqualTo("ENVIADO");
	}

	/** El urgente «cancelar la reunión», ya aprobado, llega a las familias (el aviso no lo bloquea la palabra ambigua). */
	@Test
	void debeAvisarALasFamiliasElUrgenteAmbiguoYaAprobado() {
		reloj.fijar(LocalDateTime.of(2026, 10, 7, 8, 0).atZone(LIMA).toInstant());
		Long id = como(EscenarioCobranza.DIRECCION, () -> envio.enviar(deDireccion("Debemos cancelar la reunión",
				"Estimadas familias: debemos cancelar la reunión de hoy por el simulacro.", true), null));
		assertThat(estado(id)).isEqualTo("POR_APROBAR");
		EscenarioAprobaciones.aprueba(EscenarioCobranza.PROMOTORIA, bandeja, jdbc, "comunicado", id);
		assertThat(avisos.enColegio(1L, LocalDateTime.of(2026, 10, 7, 8, 5)).urgentes()).isEqualTo(3);
		assertThat(jdbc.queryForList("SELECT parametros FROM mensaje WHERE tipo = 'COMUNICADO_URGENTE'", String.class))
				.isNotEmpty().allMatch(p -> p.contains("Debemos cancelar la reunión"));
	}
}
