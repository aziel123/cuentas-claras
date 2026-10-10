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
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ColumnasActualizables;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.privacidad.model.AccesoDatoPersonal;
import pe.edu.virgenmaria.cuentasclaras.privacidad.repository.AccesoDatoPersonalRepository;
import pe.edu.virgenmaria.cuentasclaras.privacidad.service.AlertasPrivacidad;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 7, tanda 3 (Ley 29733, sección 8.2; H12, E27, decisión 96): queda registrado quién del personal vio datos
 * personales (fichas, búsquedas, morosos, llamada de control, importación y cambios de contacto), con su sesión y su IP,
 * ANTES de mostrar la página. Solo Promotoría lo ve (en la ficha y en /auditoria/accesos) y recibe una alerta si una
 * persona ve más de 50 fichas en un día. Las familias que ven sus datos no se registran. El registro no se edita ni se
 * borra.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AccesosDatosPersonalesTest {

	@Autowired
	private MockMvc mvc;

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
	private AlertasPrivacidad alertas;

	@Autowired
	private JdbcTemplate jdbc;

	private EscenarioCaja.Familias f;

	private Usuario promotora;

	private Usuario administracion;

	private Usuario caja;

	private Usuario directora;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		SecurityContextHolder.clearContext();
		promotora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora", UsuariosDePrueba.CLAVE, false,
				Rol.PROMOTOR);
		administracion = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "lucia.adm", UsuariosDePrueba.CLAVE, false,
				Rol.ADMINISTRACION);
		caja = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "caja.uno", UsuariosDePrueba.CLAVE, false, Rol.CAJA);
		directora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "directora", UsuariosDePrueba.CLAVE, false,
				Rol.DIRECTOR);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void abrirLaFichaDeUnaFamiliaQuedaRegistradoConLaPersonaLaSesionYLaIp() throws Exception {
		mvc.perform(get("/alumnos/familias/{id}", f.quispe()).with(UsuariosDePrueba.como(administracion))
				.with(r -> { r.setRemoteAddr("192.0.2.15"); return r; })).andExpect(status().isOk());

		List<Map<String, Object>> filas = jdbc.queryForList("SELECT * FROM acceso_dato_personal");
		assertThat(filas).hasSize(1);
		Map<String, Object> fila = filas.getFirst();
		assertThat(fila.get("tipo")).isEqualTo("FICHA_FAMILIA");
		assertThat(fila.get("familia_id")).isEqualTo(f.quispe());
		assertThat(fila.get("usuario_id")).isEqualTo(administracion.getId());
		assertThat(fila.get("colegio_id")).isEqualTo(1L);
		assertThat(fila.get("ip")).isEqualTo("192.0.2.15");
		assertThat(fila.get("sesion_id")).as("la sesión de la base de quien vio").isNotNull();
		assertThat(fila.get("creado_por")).isEqualTo("lucia.adm");
	}

	@Test
	void lasFichasLasBusquedasYLaFichaDeUnAlumnoQuedanRegistradasSinElTextoBuscado() throws Exception {
		mvc.perform(get("/alumnos/{id}", f.mateo()).with(UsuariosDePrueba.como(administracion))).andExpect(status().isOk());
		mvc.perform(get("/alumnos").param("q", "quispe").with(UsuariosDePrueba.como(administracion)))
				.andExpect(status().isOk());
		mvc.perform(get("/caja").param("q", "quispe").with(UsuariosDePrueba.como(caja))).andExpect(status().isOk());
		mvc.perform(get("/alumnos/apoderados/{id}", f.pedro()).with(UsuariosDePrueba.como(administracion)))
				.andExpect(status().isOk());

		assertThat(jdbc.queryForList("SELECT tipo, alumno_id, familia_id, cantidad FROM acceso_dato_personal ORDER BY id"))
				.containsExactly(
						Map.of("tipo", "FICHA_ALUMNO", "alumno_id", f.mateo(), "familia_id", f.quispe(), "cantidad", 1),
						fila("BUSQUEDA", null, null, 2),
						fila("BUSQUEDA", null, null, 2),
						fila("FICHA_FAMILIA", null, f.flores(), 1));
		// La tabla no guarda lo que se buscó ni los datos que se vieron.
		assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
				+ "WHERE table_name = 'acceso_dato_personal'", String.class))
				.noneMatch(c -> c.contains("texto") || c.contains("busqueda") || c.contains("dato") && !c.equals("id"));
	}

	@Test
	void unaFichaQueNoExisteOUnaRedireccionNoSeRegistran() throws Exception {
		mvc.perform(get("/alumnos/familias/999999").with(UsuariosDePrueba.como(administracion)))
				.andExpect(status().isNotFound());
		mvc.perform(get("/alumnos/importar/revision").with(UsuariosDePrueba.como(administracion)))
				.andExpect(status().is3xxRedirection());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM acceso_dato_personal", Long.class)).isZero();
	}

	@Test
	void soloPromotoriaVeQuienConsultoLosDatosDeLaFamilia() throws Exception {
		mvc.perform(get("/alumnos/familias/{id}", f.quispe()).with(UsuariosDePrueba.como(administracion)));

		mvc.perform(get("/alumnos/familias/{id}", f.quispe()).with(UsuariosDePrueba.como(promotora)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Quién consultó estos datos")))
				.andExpect(content().string(containsString("lucia.adm")));
		mvc.perform(get("/alumnos/familias/{id}", f.quispe()).with(UsuariosDePrueba.como(directora)))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("Quién consultó estos datos"))));
		mvc.perform(get("/auditoria/accesos").with(UsuariosDePrueba.como(promotora))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Ficha de la familia")))
				.andExpect(content().string(containsString("lucia.adm")));
		mvc.perform(get("/auditoria/accesos").with(UsuariosDePrueba.como(directora))).andExpect(status().isForbidden());
		mvc.perform(get("/auditoria/accesos").with(UsuariosDePrueba.como(administracion)))
				.andExpect(status().isForbidden());
	}

	@Test
	void laPromotoraDeOtroColegioNoVeLosAccesosDelColegioA() throws Exception {
		mvc.perform(get("/alumnos/familias/{id}", f.quispe()).with(UsuariosDePrueba.como(administracion)));
		long colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		Usuario promotoraB = UsuariosDePrueba.guardar(usuarios, codificador, colegioB, "promotora.b",
				UsuariosDePrueba.CLAVE, false, Rol.PROMOTOR);

		mvc.perform(get("/auditoria/accesos").with(UsuariosDePrueba.como(promotoraB))).andExpect(status().isOk())
				.andExpect(content().string(not(containsString("lucia.adm"))))
				.andExpect(content().string(not(containsString("Quispe"))));
		mvc.perform(get("/alumnos/familias/{id}", f.quispe()).with(UsuariosDePrueba.como(promotoraB)))
				.andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM acceso_dato_personal WHERE colegio_id = ?", Long.class,
				colegioB)).isZero();
	}

	@Test
	void verMasDe50FichasEnUnDiaEsUnaAlertaParaPromotoria() throws Exception {
		for (int i = 0; i < 51; i++) {
			mvc.perform(get("/alumnos/familias/{id}", i % 2 == 0 ? f.quispe() : f.flores())
					.with(UsuariosDePrueba.como(administracion))).andExpect(status().isOk());
		}
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(promotora));
		List<AlertaRevision> todas = alertas.alertas();
		assertThat(todas).anySatisfy(a -> {
			assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.ATENCION);
			assertThat(a.texto()).contains("lucia.adm").contains("51 fichas").doesNotContain("Quispe", "Flores");
			assertThat(a.enlace()).startsWith("/auditoria/accesos?usuarioId=" + administracion.getId());
		});
	}

	@Test
	void cincuentaFichasNoSonAlerta() throws Exception {
		for (int i = 0; i < 50; i++) {
			mvc.perform(get("/alumnos/familias/{id}", f.quispe()).with(UsuariosDePrueba.como(administracion)));
		}
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(promotora));
		assertThat(alertas.alertas()).noneMatch(a -> a.texto().contains("fichas"));
	}

	@Test
	void lasFamiliasQueVenSusDatosNoSeRegistran() throws Exception {
		UsuarioAutenticado rosa = new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa Huamán", null, true, false, false,
				EnumSet.of(Rol.APODERADO), f.rosa());
		mvc.perform(get("/familia/mis-datos").with(UsuariosDePrueba.como(rosa))).andExpect(status().isOk());
		mvc.perform(get("/familia/estado-de-cuenta").with(UsuariosDePrueba.como(rosa))).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM acceso_dato_personal", Long.class)).isZero();
	}

	@Test
	void elRegistroNoSeEditaNiSeBorra() {
		assertThat(ColumnasActualizables.de(AccesoDatoPersonal.class)).as("ninguna columna se actualiza")
				.containsExactlyInAnyOrder("actualizado_en", "version");
		assertThat(Arrays.stream(AccesoDatoPersonalRepository.class.getMethods()).map(Method::getName))
				.noneMatch(n -> n.startsWith("delete") || n.startsWith("remove") || n.startsWith("update"));
		AccesoDatoPersonal acceso = AccesoDatoPersonal.de(1L, null,
				pe.edu.virgenmaria.cuentasclaras.comun.privacidad.TipoAcceso.BUSQUEDA, null, null, 3, "127.0.0.1");
		assertThatThrownBy(() -> {
			Method impedir = AccesoDatoPersonal.class.getDeclaredMethod("impedirBorrado");
			impedir.setAccessible(true);
			try {
				impedir.invoke(acceso);
			}
			catch (java.lang.reflect.InvocationTargetException e) {
				throw e.getCause();
			}
		}).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> AccesoDatoPersonal.de(1L, null,
				pe.edu.virgenmaria.cuentasclaras.comun.privacidad.TipoAcceso.FICHA_FAMILIA, null, null, 1, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private static Map<String, Object> fila(String tipo, Long alumno, Long familia, int cantidad) {
		java.util.HashMap<String, Object> fila = new java.util.HashMap<>();
		fila.put("tipo", tipo);
		fila.put("alumno_id", alumno);
		fila.put("familia_id", familia);
		fila.put("cantidad", cantidad);
		return fila;
	}
}
