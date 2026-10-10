package pe.edu.virgenmaria.cuentasclaras.privacidad;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.familias.dto.AvisoRequest;
import pe.edu.virgenmaria.cuentasclaras.familias.model.DerechoDatos;
import pe.edu.virgenmaria.cuentasclaras.familias.model.TipoAvisoFamilia;
import pe.edu.virgenmaria.cuentasclaras.familias.service.ServicioAvisosFamilia;
import pe.edu.virgenmaria.cuentasclaras.privacidad.service.AlertasPrivacidad;
import pe.edu.virgenmaria.cuentasclaras.privacidad.service.DatosConPlazoVencido;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.ServicioUsuarios;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionAbierta;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.sesion.SesionesFirmadas;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QA del sprint 7: aislamiento entre colegios y entre familias en lo nuevo (pedidos sobre datos personales, alertas de la
 * Ley 29733, datos con plazo vencido, registro de accesos y sesiones de la base). El colegio A es el 1 (familias Quispe y
 * Flores); el colegio B es nuevo y tiene su propia Promotoría.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AislamientoSprint7BordesTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioAvisosFamilia avisos;

	@Autowired
	private AlertasPrivacidad alertas;

	@Autowired
	private DatosConPlazoVencido vencidos;

	@Autowired
	private ServicioUsuarios servicioUsuarios;

	@Autowired
	private SesionesFirmadas sesiones;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private EscenarioCaja.Familias f;

	private Usuario promotoraA;

	private Usuario promotoraB;

	private UsuarioAutenticado rosa;

	private UsuarioAutenticado pedro;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		SecurityContextHolder.clearContext();
		long colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		promotoraA = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora", UsuariosDePrueba.CLAVE, false,
				Rol.PROMOTOR, Rol.DOCENTE);
		promotoraB = UsuariosDePrueba.guardar(usuarios, codificador, colegioB, "promotora.b", UsuariosDePrueba.CLAVE, false,
				Rol.PROMOTOR, Rol.DOCENTE);
		rosa = apoderado(40L, f.rosa());
		pedro = apoderado(41L, f.pedro());
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void laPromotoraDeOtroColegioNoVeLosPedidosNiSusAlertas() {
		UsuariosDePrueba.iniciarSesion(rosa);
		avisos.enviar(new AvisoRequest(TipoAvisoFamilia.DATOS_PERSONALES, null, null, DerechoDatos.ACCESO,
				"Quiero saber qué datos de mis hijos tiene el colegio"));
		reloj.fijar(LocalDateTime.of(2026, 11, 30, 9, 0).atZone(ZoneId.of("America/Lima")).toInstant());

		UsuariosDePrueba.iniciarSesion(promotoraB);
		assertThat(avisos.bandeja()).isEmpty();
		assertThat(alertas.alertas()).noneSatisfy(a -> assertThat(a.texto()).contains("datos personales"));

		UsuariosDePrueba.iniciarSesion(promotoraA);
		assertThat(alertas.alertas()).anySatisfy(a -> assertThat(a.texto()).contains("datos personales"));
	}

	@Test
	void unaFamiliaNoVeLosPedidosDeOtraFamilia() {
		UsuariosDePrueba.iniciarSesion(rosa);
		avisos.enviar(new AvisoRequest(TipoAvisoFamilia.DATOS_PERSONALES, null, null, DerechoDatos.RECTIFICACION,
				"Mi apellido está mal escrito en la boleta"));

		UsuariosDePrueba.iniciarSesion(pedro);
		assertThat(avisos.misAvisos()).isEmpty();
	}

	@Test
	void misDatosDeOtraFamiliaNoMuestraLosDeLaFamiliaQuispe() throws Exception {
		mvc.perform(get("/familia/mis-datos").with(UsuariosDePrueba.como(pedro))).andExpect(status().isOk())
				.andExpect(content().string(allOf(containsString(EscenarioCaja.DNI_PEDRO),
						not(containsString(EscenarioEscolar.DNI_ROSA)), not(containsString(EscenarioEscolar.DNI_MATEO)),
						not(containsString(EscenarioEscolar.CORREO_ROSA)), not(containsString("Valeria")))));
	}

	@Test
	void laPromotoraDeOtroColegioNoVeLasFamiliasConPlazoVencido() {
		jdbc.update("UPDATE alumno SET estado = 'RETIRADO', retirado_en = ?, retirado_por = 'director', motivo_retiro = "
				+ "'Se mudaron a otra ciudad' WHERE familia_id = ?", LocalDate.of(2025, 1, 15), f.quispe());
		jdbc.update("DELETE FROM cuota WHERE alumno_id IN (SELECT id FROM alumno WHERE familia_id = ?)", f.quispe());

		UsuariosDePrueba.iniciarSesion(promotoraB);
		assertThat(vencidos.familias()).isEmpty();
		UsuariosDePrueba.iniciarSesion(promotoraA);
		assertThat(vencidos.familias()).hasSize(1);
	}

	@Test
	void lasFichasVistasEnUnColegioNoDisparanLaAlertaDelOtro() {
		Usuario administracion = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "lucia.adm", UsuariosDePrueba.CLAVE,
				false, Rol.ADMINISTRACION);
		LocalDateTime hoy = LocalDateTime.now(reloj).withHour(7);
		for (int i = 0; i < 60; i++) {
			jdbc.update("INSERT INTO acceso_dato_personal (colegio_id, usuario_id, tipo, familia_id, cantidad, ip, "
					+ "creado_en, creado_por, actualizado_en) VALUES (1, ?, 'FICHA_FAMILIA', ?, 1, '192.0.2.60', ?, "
					+ "'lucia.adm', ?)", administracion.getId(), f.quispe(), hoy.plusSeconds(i), hoy.plusSeconds(i));
		}

		UsuariosDePrueba.iniciarSesion(promotoraB);
		assertThat(alertas.alertas()).noneSatisfy(a -> assertThat(a.texto()).contains("lucia.adm"));
		UsuariosDePrueba.iniciarSesion(promotoraA);
		assertThat(alertas.alertas()).anySatisfy(a -> assertThat(a.texto()).contains("lucia.adm vio hoy 60 fichas"));
	}

	@Test
	void laPromotoraDeOtroColegioNoVeLaFichaNiElRegistroDeAccesosDeEsteColegio() throws Exception {
		mvc.perform(get("/alumnos/familias/{id}", f.quispe()).with(UsuariosDePrueba.como(promotoraA)))
				.andExpect(status().isOk());

		mvc.perform(get("/alumnos/familias/{id}", f.quispe()).with(UsuariosDePrueba.como(promotoraB)))
				.andExpect(status().isNotFound());
		mvc.perform(get("/auditoria/accesos").param("usuarioId", promotoraA.getId().toString())
				.with(UsuariosDePrueba.como(promotoraB))).andExpect(status().isOk())
				.andExpect(content().string(not(containsString("Quispe"))));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM acceso_dato_personal WHERE colegio_id <> 1", Long.class))
				.isZero();
	}

	@Test
	void desactivarAUnaPersonaNoCierraLasSesionesDeOtroColegio() {
		Usuario cajaA = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "caja.a", UsuariosDePrueba.CLAVE, false,
				Rol.CAJA);
		SesionAbierta deCajaA = sesiones.abrir(1L, cajaA.getId(), "192.0.2.70");
		SesionAbierta dePromotoraB = sesiones.abrir(promotoraB.getColegioId(), promotoraB.getId(), "192.0.2.71");

		UsuariosDePrueba.iniciarSesion(promotoraA);
		servicioUsuarios.desactivar(cajaA.getId(), "Dejó de trabajar en el colegio");

		assertThat(jdbc.queryForObject("SELECT motivo_cierre FROM sesion_usuario WHERE id = ?", String.class,
				deCajaA.sesionId())).isEqualTo("CUENTA_CAMBIADA");
		assertThat(jdbc.queryForObject("SELECT cerrada_en FROM sesion_usuario WHERE id = ?", java.sql.Timestamp.class,
				dePromotoraB.sesionId())).isNull();
	}

	private static UsuarioAutenticado apoderado(long usuarioId, Long apoderadoId) {
		return new UsuarioAutenticado(usuarioId, 1L, "familia." + usuarioId, "Apoderado " + usuarioId, null, true, false,
				false, EnumSet.of(Rol.APODERADO), apoderadoId);
	}
}
