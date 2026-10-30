package pe.edu.virgenmaria.cuentasclaras.aprobaciones.directa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.SegundaFirma;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.como;

/**
 * Sprint 11, tanda 1c (A1c-2, «socio de paja»): una Promotoría recién nombrada no frena ni salta las esperas. Cuenta la
 * antigüedad EN EL ROL ({@code usuario_rol.desde}), no la de la cuenta: 30 días o más.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class PromotoriaAntiguaTest extends BaseDirecta {

	@Autowired
	private SegundaFirma segundaFirma;

	@Test
	void cuentaLaAntiguedadEnElRol() {
		assertThat(antigua("socia.dir")).isTrue();
		EscenarioDirecta.rolDesde(jdbc, e.socia(), Rol.PROMOTOR, LocalDateTime.now(reloj).minusDays(29));
		assertThat(antigua("socia.dir")).isFalse();
		avanzar(Duration.ofDays(2));
		assertThat(antigua("socia.dir")).isTrue();
		assertThat(antigua("directora.dir")).isFalse();
	}

	@Test
	void elSocioDePajaNoFrenaPeroLaAfectadaSi() {
		// La Promotoría robada nombra a un cómplice y lo usa para frenar lo que no le conviene: no puede.
		EscenarioDirecta.rolDesde(jdbc, e.socia(), Rol.PROMOTOR, LocalDateTime.now(reloj).minusDays(3));
		Long solicitud = pedirRoles(e.promotora(), e.docente(), EnumSet.of(Rol.PROMOTOR));
		aplicarConCodigo(e.promotora(), solicitud);
		assertThatThrownBy(() -> como(e.socia(), () -> bandeja.frenar(solicitud, "Lo freno porque sí, sin más")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("30 días");
		assertThat(jdbc.queryForObject("SELECT frenado_por FROM solicitud_cambio WHERE id = ?", String.class, solicitud))
				.isNull();
		como(e.docente(), () -> bandeja.frenar(solicitud, "Yo no pedí ser Promotoría del colegio"));
		assertThat(jdbc.queryForObject("SELECT frenado_por FROM solicitud_cambio WHERE id = ?", String.class, solicitud))
				.isEqualTo("docente.dir");
	}

	private boolean antigua(String nombre) {
		return pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio.en(1L,
				() -> segundaFirma.promotoriaAntigua(nombre));
	}
}
