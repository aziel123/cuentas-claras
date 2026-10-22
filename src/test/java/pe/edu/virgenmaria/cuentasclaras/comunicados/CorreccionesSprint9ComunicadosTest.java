package pe.edu.virgenmaria.cuentasclaras.comunicados;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.BorradorComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.AlcanceComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EmisorRol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Correcciones del sprint 9: TODO comunicado con PDF espera la aprobación de otra persona, sea quien sea el emisor
 * (auditoría, 1: decisión del usuario); el PDF de un docente que ya no tiene la sección no sale (QA-S9-9); y la frase que
 * condiciona un examen a la mensualidad no sale (auditoría, 2: INDECOPI).
 */
class CorreccionesSprint9ComunicadosTest extends PruebaComunicados {

	private String estado(Long id) {
		return jdbc.queryForObject("SELECT estado FROM comunicado WHERE id = ?", String.class, id);
	}

	@Test
	void debeDejarPorAprobarTodoPdfAunqueNoPidaRevisionSeaQuienSeaElEmisor() {
		Long deDireccion = como(EscenarioCobranza.DIRECCION, () -> envio.enviar(aTodoElColegio(EmisorRol.DIRECCION,
				"Programa del aniversario"), pdf(false)));
		assertThat(estado(deDireccion)).isEqualTo("POR_APROBAR");
		Long dePromotoria = como(EscenarioCobranza.PROMOTORIA, () -> envio.enviar(aTodoElColegio(EmisorRol.PROMOTORIA,
				"Calendario del año"), pdf(false)));
		assertThat(estado(dePromotoria)).isEqualTo("POR_APROBAR");
		// Sin PDF, sale al momento.
		Long sinPdf = como(EscenarioCobranza.DIRECCION, () -> envio.enviar(aTodoElColegio(EmisorRol.DIRECCION,
				"Horario de verano"), null));
		assertThat(estado(sinPdf)).isEqualTo("ENVIADO");
		// El de Dirección lo aprueba Promotoría; el de Promotoría, Dirección.
		assertThatThrownBy(() -> EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "comunicado",
				deDireccion)).isInstanceOf(RuntimeException.class);
		EscenarioAprobaciones.aprueba(EscenarioCobranza.PROMOTORIA, bandeja, jdbc, "comunicado", deDireccion);
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "comunicado", dePromotoria);
		assertThat(estado(deDireccion)).isEqualTo("ENVIADO");
		assertThat(estado(dePromotoria)).isEqualTo("ENVIADO");
	}

	@Test
	void noDebeSalirElPdfDeUnDocenteQueYaNoTieneLaSeccion() {
		Long id = como(tutora, () -> envio.enviar(aLaSeccion(EmisorRol.DOCENTE, seccionA, "Tarea del bimestre"),
				pdf(false)));
		assertThat(estado(id)).isEqualTo("POR_APROBAR");
		// Dirección termina la asignación de la tutora (en H2, como el DBA: la prueba es del servicio que aprueba).
		jdbc.update("UPDATE asignacion_docente SET estado = 'TERMINADA', vigente = NULL, tutor_vigente = NULL, "
				+ "terminada_por = 'directora', terminada_en = CURRENT_TIMESTAMP, motivo_fin = 'Licencia por maternidad' "
				+ "WHERE usuario_id = ? AND seccion_id = ? AND estado = 'VIGENTE'", tutora.getId(), seccionA);
		assertThatThrownBy(() -> EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "comunicado",
				id)).hasMessageContaining("ya no tiene esa sección");
		assertThat(estado(id)).isEqualTo("POR_APROBAR");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comunicado_destinatario WHERE comunicado_id = ?", Long.class,
				id)).isZero();
	}

	@Test
	void debeRechazarLaFraseQueCondicionaElExamenALaMensualidad() {
		BorradorComunicado borrador = new BorradorComunicado(EmisorRol.DIRECCION, AlcanceComunicado.COLEGIO, null, null,
				null, "Exámenes bimestrales", "Estimadas familias: los alumnos que no estén al día con la mensualidad no "
						+ "podrán rendir el examen bimestral.", false, true);
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> envio.enviar(borrador, null)))
				.isInstanceOf(ReglaNegocioException.class);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comunicado WHERE titulo = 'Exámenes bimestrales'", Long.class))
				.isZero();
		BorradorComunicado enLetras = new BorradorComunicado(EmisorRol.DIRECCION, AlcanceComunicado.COLEGIO, null, null,
				null, "Paseo de fin de año", "Estimadas familias: para el paseo traigan S/ treinta en un sobre.", false, true);
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> envio.enviar(enLetras, null)))
				.isInstanceOf(ReglaNegocioException.class);
	}
}
