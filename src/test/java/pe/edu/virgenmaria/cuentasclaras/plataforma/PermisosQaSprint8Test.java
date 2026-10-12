package pe.edu.virgenmaria.cuentasclaras.plataforma;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * QA del sprint 8 (sección 11, matriz de permisos y segregación): cada rol intenta lo que no le corresponde en lo nuevo
 * del sprint (plataforma, puesta en marcha, conectores, marca y aviso de privacidad) y nada cambia en la base.
 *
 * <p>Escenarios (Dado / Cuando / Entonces): dado un rol sin el permiso, cuando llama a la ruta (con CSRF), entonces
 * responde 403 y no se crea ni se cambia nada.
 */
@PruebaIntegracion
class PermisosQaSprint8Test {

	private static final Set<Rol> ROLES_DE_COLEGIO = EnumSet.of(Rol.PROMOTOR, Rol.DIRECTOR, Rol.ADMINISTRACION, Rol.CAJA,
			Rol.DOCENTE, Rol.APODERADO);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private TokenDeSesionDePrueba tokens;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		tokens.olvidar();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	/** Pide la ruta con cada rol y devuelve los que NO recibieron 403. */
	private List<String> sin403(Set<Rol> roles, Supplier<MockHttpServletRequestBuilder> pedido) throws Exception {
		List<String> errores = new ArrayList<>();
		for (Rol rol : roles) {
			int estado = mvc.perform(pedido.get().with(UsuariosDePrueba.como(rol))).andReturn().getResponse().getStatus();
			SecurityContextHolder.clearContext();
			if (estado != 403) {
				errores.add(rol + " → " + estado);
			}
		}
		return errores;
	}

	private int comoOperador(MockHttpServletRequestBuilder pedido) throws Exception {
		RequestPostProcessor operador = UsuariosDePrueba.como(EscenarioPlataforma.operador(usuarios, codificador, colegios,
				"operador.permisos8"));
		return mvc.perform(pedido.with(operador)).andReturn().getResponse().getStatus();
	}

	@Test
	void debeResponder403ACadaRolDeColegioQueQuiereDarDeAltaUnColegio() throws Exception {
		long antes = jdbc.queryForObject("SELECT COUNT(*) FROM colegio", Long.class);

		assertThat(sin403(ROLES_DE_COLEGIO, () -> get("/plataforma"))).isEmpty();
		assertThat(sin403(ROLES_DE_COLEGIO, () -> post("/plataforma/colegios/nuevo").with(csrf()).param("nombre", "Intruso")
				.param("ruc", EscenarioPlataforma.RUC_A))).isEmpty();
		assertThat(sin403(ROLES_DE_COLEGIO, () -> post("/plataforma/colegios/1/correo-control").with(csrf())
				.param("correo", "intruso@correo.pe"))).isEmpty();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM colegio", Long.class)).isEqualTo(antes);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM configuracion_colegio", Long.class)).isZero();
	}

	@Test
	void debeResponder403AQuienNoEsPromotoriaYQuierePasarAProduccion() throws Exception {
		jdbc.update("UPDATE colegio SET estado = 'PREPARACION', produccion_desde = NULL, produccion_por = NULL WHERE id = 1");

		assertThat(sin403(EnumSet.of(Rol.DIRECTOR, Rol.ADMINISTRACION, Rol.CAJA, Rol.DOCENTE, Rol.APODERADO),
				() -> post("/puesta-en-marcha/produccion").with(csrf()))).isEmpty();
		assertThat(sin403(EnumSet.of(Rol.DIRECTOR, Rol.ADMINISTRACION, Rol.CAJA), () -> post(
				"/puesta-en-marcha/ose-simulado").with(csrf()))).isEmpty();
		assertThat(comoOperador(post("/puesta-en-marcha/produccion").with(csrf()))).isEqualTo(403);
		assertThat(jdbc.queryForObject("SELECT estado FROM colegio WHERE id = 1", String.class)).isEqualTo("PREPARACION");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM firma_operacion WHERE clave LIKE 'colegio:1:%'", Long.class))
				.isZero();
	}

	@Test
	void debeResponder403AQuienNoPuedeProponerUnCambioDeCuentaBancaria() throws Exception {
		assertThat(sin403(EnumSet.of(Rol.DIRECTOR, Rol.CAJA, Rol.DOCENTE, Rol.APODERADO), () -> post(
				"/conectores/cuenta-bancaria").with(csrf()).param("proveedor", "BCP").param("modo", "REAL")
				.param("numeroCuenta", "1912345678901").param("cci", "00219100123456789012")
				.param("motivo", "Cuenta nueva para depositar el efectivo"))).isEmpty();
		assertThat(comoOperador(post("/conectores/cuenta-bancaria").with(csrf()).param("proveedor", "BCP")
				.param("modo", "REAL").param("numeroCuenta", "1912345678901").param("cci", "00219100123456789012")
				.param("motivo", "Cuenta nueva para depositar el efectivo"))).isEqualTo(403);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conector_colegio", Long.class)).isZero();
	}

	@Test
	void debeResponder403AQuienNoEsDireccionYQuiereAprobarElAvisoDePrivacidad() throws Exception {
		assertThat(sin403(EnumSet.of(Rol.PROMOTOR, Rol.ADMINISTRACION, Rol.CAJA, Rol.DOCENTE, Rol.APODERADO),
				() -> post("/colegio/privacidad/aprobar").with(csrf()))).isEmpty();
		assertThat(comoOperador(post("/colegio/privacidad/aprobar").with(csrf()))).isEqualTo(403);
		assertThat(jdbc.queryForObject("SELECT aviso_privacidad_version FROM colegio WHERE id = 1", String.class)).isNull();
	}

	@Test
	void debeResponder403AQuienNoEsPromotoriaYQuiereCambiarLaMarcaDelColegio() throws Exception {
		assertThat(sin403(EnumSet.of(Rol.DIRECTOR, Rol.ADMINISTRACION, Rol.CAJA, Rol.DOCENTE, Rol.APODERADO),
				() -> post("/colegio/marca").with(csrf()).param("nombre", "Otro nombre").param("razonSocial", "Otra S.A.C.")
						.param("direccion", "Av. Otra 1").param("correoInstitucional", "otro@colegio.pe"))).isEmpty();
		assertThat(comoOperador(post("/colegio/marca").with(csrf()).param("nombre", "Otro nombre")
				.param("razonSocial", "Otra S.A.C.").param("direccion", "Av. Otra 1")
				.param("correoInstitucional", "otro@colegio.pe"))).isEqualTo(403);
		assertThat(jdbc.queryForObject("SELECT razon_social FROM colegio WHERE id = 1", String.class)).isNull();
	}
}
