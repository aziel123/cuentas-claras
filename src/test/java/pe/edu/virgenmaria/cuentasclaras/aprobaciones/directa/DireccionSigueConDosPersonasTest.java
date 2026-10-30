package pe.edu.virgenmaria.cuentasclaras.aprobaciones.directa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.AutoaprobacionSolicitudException;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.FeriadoRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioFeriados;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioContactoPersonal;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.como;

/**
 * Sprint 11, tanda 1c (decisión 214): la directa es solo de la Promotoría. Lo que pide Dirección sigue esperando la
 * aprobación de OTRA persona, y una Dirección que creó la cuenta de un docente no aprueba lo de ese docente (A5); la
 * Promotoría sí.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class DireccionSigueConDosPersonasTest extends BaseDirecta {

	private static final LocalDate VIERNES_9 = LocalDate.of(2026, 10, 9);

	@Autowired
	private ServicioFeriados feriados;

	@Autowired
	private CalendarioHabil calendario;

	@Autowired
	private ServicioContactoPersonal contactos;

	@Test
	void loQuePideDireccionEsperaAOtraPersona() {
		Long feriado = como(e.directora(), () -> feriados.registrar(new FeriadoRequest(VIERNES_9, "Aniversario")));
		assertThat(habil(VIERNES_9)).as("sigue pendiente").isTrue();
		assertThatThrownBy(() -> como(e.directora(), () -> feriados.aprobar(feriado)))
				.isInstanceOf(ReglaNegocioException.class);
		como(e.promotora(), () -> feriados.aprobar(feriado));
		assertThat(habil(VIERNES_9)).isFalse();
		assertThat(EscenarioDirecta.eventos(jdbc, "ACCION_DIRECTA_PROMOTORIA")).isZero();
	}

	@Test
	void laDireccionQueCreoLaCuentaNoApruebaPeroLaPromotoriaSi() {
		jdbc.update("UPDATE usuario SET creado_por = ?, creado_en = ? WHERE id = ?", e.directora().getNombreUsuario(),
				LocalDateTime.now(reloj).minusDays(2), e.docente().getId());
		Long solicitud = como(e.docente(), () -> contactos.solicitar(e.docente().getId(), "987 650 555", null,
				"Cambié de número de celular"));
		assertThatThrownBy(() -> como(e.directora(), () -> bandeja.aprobar(solicitud, null)))
				.isInstanceOf(AutoaprobacionSolicitudException.class);
		assertThat(estado(solicitud)).isEqualTo("PENDIENTE");
		como(e.promotora(), () -> bandeja.aprobar(solicitud, null));
		assertThat(estado(solicitud)).isEqualTo("APROBADA");
	}

	private boolean habil(LocalDate dia) {
		return pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio.en(1L, () -> calendario.esHabil(dia));
	}
}
