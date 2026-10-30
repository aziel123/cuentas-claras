package pe.edu.virgenmaria.cuentasclaras.aprobaciones.directa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.AutoaprobacionSolicitudException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.ReglasSegregacion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.SegundaFirma;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioContactoPersonal;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.como;

/**
 * Sprint 11, tanda 1c (decisión 214): fuera de la Promotoría nada cambia. Administración, Caja, Docente y Dirección no
 * aplican directo; la Promotoría no puede ser Caja (quien cobra no aprueba).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class DemasRolesSinCambioTest extends BaseDirecta {

	@Autowired
	private SegundaFirma segundaFirma;

	@Autowired
	private ServicioContactoPersonal contactos;

	@Test
	void soloLaPromotoriaAplicaDirecto() {
		pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio.en(1L, () -> {
			assertThat(segundaFirma.aplicaDirecto("promotora.dir")).isTrue();
			for (String otro : List.of("directora.dir", "admin.dir", "docente.dir", "nadie")) {
				assertThat(segundaFirma.aplicaDirecto(otro)).as(otro).isFalse();
			}
			return null;
		});
	}

	@Test
	void administracionYDocenteNoApruebanLoSuyo() {
		for (var quien : List.of(e.administracion(), e.docente())) {
			Long solicitud = como(quien, () -> contactos.solicitar(quien.getId(), "987 650 4" + quien.getId() % 10 + "1",
					null, "Cambié de número de celular"));
			assertThatThrownBy(() -> como(quien, () -> bandeja.aprobar(solicitud, null)))
					.isInstanceOfAny(AccessDeniedException.class, AutoaprobacionSolicitudException.class);
			assertThat(estado(solicitud)).isEqualTo("PENDIENTE");
		}
	}

	@Test
	void laPromotoriaNoPuedeSerCaja() {
		assertThatThrownBy(() -> ReglasSegregacion.validarCuenta(EnumSet.of(Rol.PROMOTOR, Rol.CAJA), null))
				.isInstanceOf(ReglaNegocioException.class);
	}
}
