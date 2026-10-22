package pe.edu.virgenmaria.cuentasclaras.comunicados;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comunicados.dto.DetalleComunicado;
import pe.edu.virgenmaria.cuentasclaras.comunicados.model.EmisorRol;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 9, tanda 3 (sección 3.10, decisión 163, B16): destinatarias, leídas y confirmadas; los pendientes con el nombre
 * del alumno (nunca el apoderado) y solo para quien tiene la asignación VIGENTE en la sección (o Dirección).
 */
class EstadisticasComunicadoTest extends PruebaComunicados {

	@Test
	void cuentaDestinatariasLeidasYConfirmadas() {
		Long id = como(tutora, () -> envio.enviar(aLaSeccion(EmisorRol.DOCENTE, seccionA, "Reunión de 5.° A"), null));
		DetalleComunicado antes = como(tutora, () -> consulta.detalle(id));
		assertThat(antes.estadisticas().destinatarias()).isEqualTo(2);
		assertThat(antes.estadisticas().confirmadas()).isZero();
		assertThat(antes.estadisticas().pendientes()).anyMatch(p -> p.startsWith("Mateo")).anyMatch(p -> p.startsWith("Lucas"))
				.noneMatch(p -> p.contains("Rosa")).noneMatch(p -> p.contains("Pedro"));
		como(rosaEnLinea(), () -> lectura.abrir(id));
		como(rosaEnLinea(), () -> {
			lectura.confirmar(id);
			return null;
		});
		DetalleComunicado despues = como(tutora, () -> consulta.detalle(id));
		assertThat(despues.estadisticas().leidas()).isEqualTo(1);
		assertThat(despues.estadisticas().confirmadas()).isEqualTo(1);
		assertThat(despues.estadisticas().pendientes()).noneMatch(p -> p.startsWith("Mateo")).anyMatch(p -> p.startsWith("Lucas"));
		assertThat(despues.estadisticas().sinPortal()).isGreaterThanOrEqualTo(0);
	}

	/** B16: un docente sin asignación vigente ya no ve los nombres de los pendientes. */
	@Test
	void sinAsignacionVigenteNoVePendientes() {
		Long id = como(tutora, () -> envio.enviar(aLaSeccion(EmisorRol.DOCENTE, seccionA, "Reunión de 5.° A"), null));
		assertThat(como(tutora, () -> consulta.detalle(id)).estadisticas().pendientesVisibles()).isTrue();
		Long asignacion = jdbc.queryForObject("SELECT id FROM asignacion_docente WHERE usuario_id = ? AND estado = "
				+ "'VIGENTE'", Long.class, tutora.getId());
		como(EscenarioCobranza.DIRECCION, () -> {
			asignaciones.terminar(asignacion, "Cambio de tutoría por licencia");
			return null;
		});
		DetalleComunicado sinAsignacion = como(tutora, () -> consulta.detalle(id));
		assertThat(sinAsignacion.estadisticas().pendientesVisibles()).isFalse();
		assertThat(sinAsignacion.estadisticas().pendientes()).isEmpty();
		assertThat(sinAsignacion.estadisticas().destinatarias()).as("las cifras, sí").isEqualTo(2);
		assertThat(como(EscenarioCobranza.DIRECCION, () -> consulta.detalle(id)).estadisticas().pendientes())
				.as("Dirección los sigue viendo").isNotEmpty();
	}
}
