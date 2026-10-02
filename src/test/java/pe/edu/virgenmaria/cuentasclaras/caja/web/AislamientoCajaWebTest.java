package pe.edu.virgenmaria.cuentasclaras.caja.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.Familias;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.EnumSet;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/** La cajera del colegio B (con el mismo nombre de usuario) recibe 404 en las familias, pagos y boletas del A. */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AislamientoCajaWebTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private JdbcTemplate jdbc;

	private Familias a;

	private Long pagoA;

	private UsuarioAutenticado cajaB;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		a = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		como(EscenarioCaja.CAJA);
		pagoA = cobro.cobrar(EscenarioCaja.efectivo(a.quispe(), List.of(cuota(jdbc, a.mateo(), "PEN-2027-03")), "450.00",
				"450.00"));
		SecurityContextHolder.clearContext();
		long colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		cajaB = UsuariosDePrueba.autenticado(colegioB, 92L, "caja", "Caja B", false, EnumSet.of(Rol.CAJA));
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void cajaDelColegioBRecibe404AlAbrirPagoDelA() throws Exception {
		for (String ruta : new String[] { "/caja/pagos/" + pagoA, "/caja/pagos/" + pagoA + "/comprobante",
				"/caja/familias/" + a.quispe() }) {
			mvc.perform(get(ruta).with(UsuariosDePrueba.como(cajaB))).andExpect(status().isNotFound());
		}
		mvc.perform(post("/caja/familias/{id}/revisar", a.quispe()).with(csrf()).with(UsuariosDePrueba.como(cajaB))
				.param("cuotaIds", cuota(jdbc, a.mateo(), "PEN-2027-04").toString()).param("medio", "YAPE"))
				.andExpect(status().isNotFound());
		mvc.perform(get("/caja").param("q", "quispe").with(UsuariosDePrueba.como(cajaB)))
				.andExpect(status().isOk()).andExpect(content().string(not(containsString("Quispe"))))
				.andExpect(content().string(containsString("No encontramos a nadie")));
		mvc.perform(get("/caja/hoy").with(UsuariosDePrueba.como(cajaB)))
				.andExpect(status().isOk()).andExpect(content().string(not(containsString("B001-00000001"))));
	}
}
