package pe.edu.virgenmaria.cuentasclaras.academico;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import pe.edu.virgenmaria.cuentasclaras.academico.model.ConfiguracionAcademica;
import pe.edu.virgenmaria.cuentasclaras.academico.service.ConfiguracionesAcademicas;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;

import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sprint 9, tanda 2 (A6, E15, E55): las horas las cambia SOLO Dirección, con su firma, y el cambio queda resaltado. */
class ConfiguracionAcademicaTest extends PruebaAsistencia {

	@Autowired
	private ConfiguracionesAcademicas configuraciones;

	private static ConfiguracionAcademica.Valores conAviso(LocalTime aviso) {
		return new ConfiguracionAcademica.Valores(LocalTime.of(9, 0), aviso, LocalTime.of(15, 0), 5, 5,
				LocalTime.of(18, 0), 4);
	}

	@Test
	void cambiarHorasQuedaResaltado() {
		como(direccion(), () -> configuraciones.cambiar(conAviso(LocalTime.of(11, 0))));
		Map<String, Object> evento = EscenarioEscolar.ultimoEvento(jdbc, "CONFIGURACION_ACADEMICA_CAMBIADA");
		assertThat(evento.get("VALOR_ANTERIOR").toString()).contains("aviso 10:30");
		assertThat(evento.get("VALOR_NUEVO").toString()).contains("aviso 11:00");
		assertThat(AccionAuditoria.CONFIGURACION_ACADEMICA_CAMBIADA.requiereAtencion()).isTrue();
		assertThat(como(direccion(), () -> configuraciones.vigente().getHoraAviso())).isEqualTo(LocalTime.of(11, 0));
		// Otro cambio: la fila se actualiza (versión 1) con otra firma.
		como(direccion(), () -> configuraciones.cambiar(conAviso(LocalTime.of(12, 0))));
		assertThat(jdbc.queryForList("SELECT clave FROM firma_operacion WHERE clave LIKE 'configuracion_academica:%' "
				+ "ORDER BY id", String.class)).containsExactly("configuracion_academica:1:CAMBIADA:0",
						"configuracion_academica:1:CAMBIADA:1");
	}

	@Test
	void soloDireccionConFirma() {
		assertThatThrownBy(() -> como(EscenarioCobranza.ADMINISTRACION, () -> configuraciones.cambiar(
				conAviso(LocalTime.of(11, 0))))).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> como(EscenarioCobranza.PROMOTORIA, () -> configuraciones.cambiar(
				conAviso(LocalTime.of(11, 0))))).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> como(tutora(), () -> configuraciones.cambiar(conAviso(LocalTime.of(11, 0)))))
				.isInstanceOf(AccessDeniedException.class);
		assertThat(EscenarioEscolar.contar(jdbc, "configuracion_academica")).isZero();
	}

	@Test
	void elAvisoNoSeCorreALaNoche() {
		assertThatThrownBy(() -> como(direccion(), () -> configuraciones.cambiar(conAviso(LocalTime.of(19, 0)))))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("09:00 y las 13:00");
		assertThat(EscenarioEscolar.contar(jdbc, "configuracion_academica")).isZero();
	}
}
