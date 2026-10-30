package pe.edu.virgenmaria.cuentasclaras.aprobaciones.directa;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.time.Duration;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.como;

/**
 * Sprint 11, tanda 1c (A1c-2): quitarle la Promotoría a la socia, o desactivar su cuenta, espera 24 horas con freno: una
 * Promotoría robada no saca en silencio a la otra. La afectada la frena; si nadie la frena, la aplica {@code sistema.roles}.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class QuitarPromotorEsperaTest extends BaseDirecta {

	@Test
	void quitarlePromotoriaALaSociaEsperaYLaAfectadaLaFrena() {
		Long solicitud = pedirRoles(e.promotora(), e.socia(), EnumSet.of(Rol.DOCENTE));
		aplicarConCodigo(e.promotora(), solicitud);
		assertThat(roles(e.socia())).as("todavía es Promotoría").containsExactly("PROMOTOR");
		assertThat(EscenarioDirecta.avisados(jdbc, "DIRECTA_EN_ESPERA")).contains(e.socia().getId());

		como(e.socia(), () -> bandeja.frenar(solicitud, "No acepto: hablemos en la junta de socios"));
		avanzar(Duration.ofHours(25));
		assertThat(esperas.aplicar(1L)).isZero();
		assertThat(roles(e.socia())).containsExactly("PROMOTOR");
	}

	@Test
	void sinFrenoSeAplicaAlVencer() {
		Long solicitud = pedirRoles(e.promotora(), e.socia(), EnumSet.of(Rol.DOCENTE));
		aplicarConCodigo(e.promotora(), solicitud);
		avanzar(Duration.ofHours(25));
		assertThat(esperas.aplicar(1L)).isEqualTo(1);
		assertThat(roles(e.socia())).containsExactly("DOCENTE");
	}

	@Test
	void desactivarALaSociaTambienEspera() {
		como(e.promotora(), () -> servicioUsuarios.desactivar(e.socia().getId(), EscenarioDirecta.MOTIVO));
		Long solicitud = EscenarioAprobaciones.pendiente(jdbc, "usuario", e.socia().getId());
		aplicarConCodigo(e.promotora(), solicitud);
		assertThat(jdbc.queryForObject("SELECT activo FROM usuario WHERE id = ?", Boolean.class, e.socia().getId()))
				.isTrue();
		avanzar(Duration.ofHours(25));
		assertThat(esperas.aplicar(1L)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT activo FROM usuario WHERE id = ?", Boolean.class, e.socia().getId()))
				.isFalse();
	}
}
