package pe.edu.virgenmaria.cuentasclaras.aprobaciones.directa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.plataforma.service.AltaColegio;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioContactoPersonal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.CORREO_CONTROL;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioDirecta.como;

/**
 * Sprint 11, tanda 1c (A1c-8): el correo externo de control es el testigo independiente de lo que la Promotoría aplica
 * directo: nunca es el correo de una Promotoría activa del colegio, ni al registrarlo ni al cambiar el contacto de ella.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class CorreoDeControlNoEsDePromotoriaTest extends BaseDirecta {

	@Autowired
	private AltaColegio alta;

	@Autowired
	private ServicioContactoPersonal contactos;

	@Test
	void elOperadorNoRegistraElCorreoDeUnaPromotoria() {
		jdbc.update("UPDATE usuario SET correo = 'duena@gmail.com' WHERE id = ?", e.promotora().getId());
		UsuariosDePrueba.iniciarSesion(EscenarioPlataforma.operador(usuarios, codificador, colegios, "operador.control"));
		try {
			assertThatThrownBy(() -> alta.registrarCorreoControl(1L, "Duena@Gmail.com", "Cambió de contador el colegio"))
					.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Promotoría");
		}
		finally {
			SecurityContextHolder.clearContext();
		}
		assertThat(jdbc.queryForObject("SELECT valor FROM configuracion_colegio WHERE colegio_id = 1 AND clave = "
				+ "'huella_correo_externo' ORDER BY id DESC LIMIT 1", String.class)).isEqualTo(CORREO_CONTROL);
	}

	@Test
	void unaPromotoriaNoPoneEnSuCuentaElCorreoDeControl() {
		assertThatThrownBy(() -> como(e.promotora(), () -> contactos.solicitar(e.promotora().getId(), null, CORREO_CONTROL,
				"Quiero recibir los avisos en este correo"))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("correo externo de control");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM solicitud_cambio", Long.class)).isZero();
	}
}
