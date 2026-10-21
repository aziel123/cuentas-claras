package pe.edu.virgenmaria.cuentasclaras.aprobaciones.service;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.EstadoSolicitud;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.model.SolicitudCambio;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.FeriadoRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Sprint 9, tanda 2 (A8): solicitudes pendientes más de 2 días hábiles: una alerta a Promotoría, al celular. */
class AlertasSolicitudesPendientesTest {

	private static SolicitudCambio creada(LocalDateTime cuando, long id) {
		SolicitudCambio s = mock(SolicitudCambio.class);
		when(s.getCreadoEn()).thenReturn(cuando);
		when(s.getId()).thenReturn(id);
		return s;
	}

	@Test
	void avisaLasDeMasDeDosDiasHabiles() {
		SolicitudCambioRepository repositorio = mock(SolicitudCambioRepository.class);
		// Hoy es martes 6/10/2026; una del miércoles anterior (4 hábiles) y una de ayer.
		SolicitudCambio vieja = creada(LocalDateTime.of(2026, 9, 30, 9, 0), 7L);
		SolicitudCambio deAyer = creada(LocalDateTime.of(2026, 10, 5, 9, 0), 8L);
		when(repositorio.findByEstadoOrderByIdAsc(EstadoSolicitud.PENDIENTE)).thenReturn(List.of(vieja, deAyer));
		CalendarioHabil calendario = new CalendarioHabil(mock(FeriadoRepository.class));
		AlertasSolicitudesPendientes alertas = new AlertasSolicitudesPendientes(repositorio, calendario,
				Clock.fixed(Instant.parse("2026-10-06T14:00:00Z"), ZoneId.of("America/Lima")));
		List<AlertaRevision> lista = alertas.alertas();
		assertThat(lista).singleElement().satisfies(a -> {
			assertThat(a.texto()).startsWith("1 solicitud(es)");
			assertThat(a.aviso().tipo()).isEqualTo(TipoAviso.SOLICITUDES_PENDIENTES);
			assertThat(a.aviso().referencia()).isEqualTo("PENDIENTES:7");
			assertThat(a.difundible()).isTrue();
		});
		assertThat(TipoAviso.SOLICITUDES_PENDIENTES.soloDireccion()).isFalse();
	}
}
