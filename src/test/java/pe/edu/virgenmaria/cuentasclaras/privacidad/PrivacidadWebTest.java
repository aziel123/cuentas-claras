package pe.edu.virgenmaria.cuentasclaras.privacidad;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.privacidad.dto.DatoVencido;
import pe.edu.virgenmaria.cuentasclaras.privacidad.service.DatosConPlazoVencido;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 7, tanda 3 (Ley 29733, secciones 8.4 y 8.5): el aviso de privacidad es público y se enlaza desde el ingreso; el
 * reporte «Datos con plazo vencido» de Promotoría lista las familias que se fueron sin deuda hace más del plazo y que
 * todavía tienen su celular o correo guardados.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class PrivacidadWebTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private DatosConPlazoVencido vencidos;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void elAvisoDePrivacidadEsPublicoYSeEnlazaDesdeElIngreso() throws Exception {
		mvc.perform(get("/privacidad")).andExpect(status().isOk())
				.andExpect(content().string(allOf(containsString("Aviso de privacidad"), containsString("2027-01"),
						containsString("Tus derechos"), containsString("Meta (WhatsApp)"),
						containsString("asesor legal"), containsString("Autoridad Nacional de Protección de Datos"))));
		mvc.perform(get("/login")).andExpect(content().string(containsString("href=\"/privacidad\"")));
	}

	@Test
	void elReporteListaLasFamiliasQueSeFueronSinDeudaHaceMasDeUnAnio() throws Exception {
		EscenarioCaja.Familias f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		SecurityContextHolder.clearContext();
		// Los Quispe se retiraron en agosto de 2025, sin deuda (sin cuotas por pagar: solo en H2 se quitan así).
		jdbc.update("UPDATE alumno SET estado = 'RETIRADO', retirado_en = ?, retirado_por = 'director', motivo_retiro = "
				+ "'Se mudaron a otra ciudad' WHERE familia_id = ?", LocalDate.of(2025, 8, 15), f.quispe());
		jdbc.update("DELETE FROM cuota WHERE alumno_id IN (SELECT id FROM alumno WHERE familia_id = ?)",
				f.quispe());

		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.PROMOTORIA);
		assertThat(vencidos.familias()).singleElement().satisfies((DatoVencido d) -> {
			assertThat(d.familiaId()).isEqualTo(f.quispe());
			assertThat(d.salida()).isEqualTo(LocalDate.of(2025, 8, 15));
			assertThat(d.contactos()).isPositive();
		});
		mvc.perform(get("/auditoria/datos-vencidos").with(UsuariosDePrueba.como(EscenarioCobranza.PROMOTORIA)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Quispe")))
				.andExpect(content().string(containsString("15/08/2025")));
		mvc.perform(get("/auditoria/datos-vencidos").with(UsuariosDePrueba.como(Rol.DIRECTOR)))
				.andExpect(status().isForbidden());
	}

	@Test
	void unaFamiliaQueSeFueConDeudaOHaceMenosDeUnAnioNoAparece() {
		EscenarioCaja.Familias f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		SecurityContextHolder.clearContext();
		// Con deuda (sus cuotas siguen pendientes): no se toca, aunque se haya ido hace tiempo.
		jdbc.update("UPDATE alumno SET estado = 'RETIRADO', retirado_en = ?, retirado_por = 'director', motivo_retiro = "
				+ "'Se mudaron a otra ciudad' WHERE familia_id = ?", LocalDate.of(2025, 1, 10), f.quispe());
		// Hace menos de un año (el reloj de las pruebas está en octubre de 2026).
		jdbc.update("UPDATE alumno SET estado = 'RETIRADO', retirado_en = ?, retirado_por = 'director', motivo_retiro = "
				+ "'Se mudaron a otra ciudad' WHERE familia_id = ?", LocalDate.of(2026, 3, 1), f.flores());
		jdbc.update("DELETE FROM cuota WHERE alumno_id IN (SELECT id FROM alumno WHERE familia_id = ?)",
				f.flores());
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.PROMOTORIA);
		assertThat(vencidos.familias()).isEmpty();
	}
}
