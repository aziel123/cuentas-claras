package pe.edu.virgenmaria.cuentasclaras.puestaenmarcha;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPlataforma;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.TokenDeSesionDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comun.sistema.RecorridoColegios;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.ListaVerificacion;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.EstadoPuestaEnMarcha;
import pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.service.PasoAProduccion;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 8, tandas 1 y 2 (secciones 3.4 y 3.5; E9, E10, E30 y E31): un colegio nace en preparación y no cobra; solo su
 * Promotoría lo pasa a producción, con su firma, con Dirección y Caja activas y (tanda 2) con la lista de verificación
 * COMPLETA, sin vuelta atrás; toda la Promotoría y el correo externo de control reciben el aviso. Los dos «aceptos» se
 * firman. Los procesos de cobro no recorren un colegio en preparación ni la plataforma.
 */
@PruebaIntegracion
class PasoAProduccionTest {

	@Autowired
	private PasoAProduccion paso;

	@Autowired
	private EstadoPuestaEnMarcha estado;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private RecorridoColegios recorrido;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TokenDeSesionDePrueba tokens;

	@Autowired
	private MockMvc mvc;

	private long colegio;

	private Usuario promotor;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		tokens.olvidar();
		colegio = colegios.saveAndFlush(Colegio.enPreparacion("Colegio Nuevo", "Colegio Nuevo S.A.C.",
				EscenarioPlataforma.RUC_A, "Av. 1", "info@nuevo.edu.pe", EnumSet.of(Nivel.PRIMARIA), "Titular", "45678920",
				null)).getId();
		promotor = UsuariosDePrueba.guardar(usuarios, codificador, colegio, "promotor.nuevo", UsuariosDePrueba.CLAVE, false,
				Rol.PROMOTOR);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		tokens.olvidar();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void sinDireccionNiCajaNoPasaAProduccion() {
		UsuariosDePrueba.iniciarSesion(promotor);

		assertThatThrownBy(paso::pasar).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Dirección")
				.hasMessageContaining("Caja");
		assertThat(estadoDelColegio()).isEqualTo("PREPARACION");
	}

	@Test
	void sinListaCompletaNoPasa() {
		UsuariosDePrueba.guardar(usuarios, codificador, colegio, "director.nuevo", UsuariosDePrueba.CLAVE, false, Rol.DIRECTOR);
		UsuariosDePrueba.guardar(usuarios, codificador, colegio, "caja.nueva", UsuariosDePrueba.CLAVE, false, Rol.CAJA);
		UsuariosDePrueba.iniciarSesion(promotor);

		// Con el equipo y los datos (lo que bastaba en la tanda 1), ya no alcanza.
		assertThatThrownBy(paso::pasar).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("completa la lista de verificación").hasMessageContaining("pensiones")
				.hasMessageContaining("cuenta bancaria").hasMessageContaining("respaldo");
		assertThat(estadoDelColegio()).isEqualTo("PREPARACION");

		// Con todo menos los dos aceptos, tampoco.
		EscenarioPuestaEnMarcha.datos(jdbc, colegio, EnumSet.of(Nivel.PRIMARIA), promotor.getId());
		UsuariosDePrueba.iniciarSesion(promotor);
		assertThatThrownBy(paso::pasar).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("saldo inicial")
				.hasMessageContaining("comprobantes");
		paso.sinSaldoInicial();
		UsuariosDePrueba.iniciarSesion(promotor);
		assertThatThrownBy(paso::pasar).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("comprobantes")
				.hasMessageNotContaining("saldo inicial");
		assertThat(estadoDelColegio()).isEqualTo("PREPARACION");
	}

	@Test
	void losAceptosLosFirmaSoloLaPromotoriaUnaVezYQuedanResaltados() {
		Usuario director = UsuariosDePrueba.guardar(usuarios, codificador, colegio, "director.acepto", UsuariosDePrueba.CLAVE,
				false, Rol.DIRECTOR);
		UsuariosDePrueba.iniciarSesion(director);
		assertThatThrownBy(paso::sinSaldoInicial).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(paso::aceptarOseSimulado).isInstanceOf(AccessDeniedException.class);

		UsuariosDePrueba.iniciarSesion(promotor);
		paso.sinSaldoInicial();
		UsuariosDePrueba.iniciarSesion(promotor);
		paso.aceptarOseSimulado();

		assertThat(jdbc.queryForList("SELECT clave FROM firma_operacion WHERE colegio_id = ? ORDER BY id", String.class,
				colegio)).containsExactly("colegio:" + colegio + ":SIN_SALDO_INICIAL", "colegio:" + colegio + ":OSE_SIMULADO");
		assertThat(jdbc.queryForList("SELECT accion FROM evento_auditoria WHERE colegio_id = ? ORDER BY secuencia",
				String.class, colegio)).contains("COLEGIO_SIN_SALDO_INICIAL", "COLEGIO_OSE_SIMULADO_ACEPTADO");
		ListaVerificacion lista = estado.paraOperador(colegio);
		assertThat(punto(lista, EstadoPuestaEnMarcha.SALDO_INICIAL).hecho()).isTrue();
		assertThat(punto(lista, EstadoPuestaEnMarcha.OSE).hecho()).isTrue();
		// Una sola vez.
		UsuariosDePrueba.iniciarSesion(promotor);
		assertThatThrownBy(paso::sinSaldoInicial).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Ya se firmó");
	}

	@Test
	void laPromotoriaLoPasaConSuFirmaYQuedaEnLaBitacora() {
		UsuariosDePrueba.guardar(usuarios, codificador, colegio, "director.nuevo", UsuariosDePrueba.CLAVE, false, Rol.DIRECTOR);
		UsuariosDePrueba.guardar(usuarios, codificador, colegio, "caja.nueva", UsuariosDePrueba.CLAVE, false, Rol.CAJA);
		EscenarioPuestaEnMarcha.datos(jdbc, colegio, EnumSet.of(Nivel.PRIMARIA), promotor.getId());
		UsuariosDePrueba.iniciarSesion(promotor);
		paso.sinSaldoInicial();
		UsuariosDePrueba.iniciarSesion(promotor);
		paso.aceptarOseSimulado();
		assertThat(estado.paraOperador(colegio).completa()).isTrue();
		UsuariosDePrueba.iniciarSesion(promotor);

		paso.pasar();

		assertThat(estadoDelColegio()).isEqualTo("PRODUCCION");
		assertThat(jdbc.queryForObject("SELECT produccion_por FROM colegio WHERE id = ?", String.class, colegio))
				.isEqualTo("promotor.nuevo");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM firma_operacion WHERE colegio_id = ? AND clave = ?", Long.class,
				colegio, "colegio:" + colegio + ":PRODUCCION")).isEqualTo(1L);
		assertThat(jdbc.queryForMap("SELECT valor_anterior, valor_nuevo FROM evento_auditoria WHERE colegio_id = ? AND accion = ?",
				colegio, "COLEGIO_EN_PRODUCCION")).containsEntry("valor_anterior", "PREPARACION")
				.containsEntry("valor_nuevo", "PRODUCCION");
		// Toda la Promotoría y el correo externo de control reciben el aviso, en la misma transacción.
		assertThat(jdbc.queryForList("SELECT destinatario_tipo FROM mensaje WHERE colegio_id = ? AND tipo = ?", String.class,
				colegio, "COLEGIO_EN_PRODUCCION")).containsExactlyInAnyOrder("USUARIO", "EXTERNO");
		// Sin vuelta atrás.
		UsuariosDePrueba.iniciarSesion(promotor);
		assertThatThrownBy(paso::pasar).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya está en producción");
	}

	@Test
	void soloLaPromotoriaPasaAProduccion() {
		Usuario director = UsuariosDePrueba.guardar(usuarios, codificador, colegio, "director.solo", UsuariosDePrueba.CLAVE,
				false, Rol.DIRECTOR);
		UsuariosDePrueba.iniciarSesion(director);

		assertThatThrownBy(paso::pasar).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void enPreparacionLaCajaNoCobra() {
		Usuario caja = UsuariosDePrueba.guardar(usuarios, codificador, colegio, "caja.preparacion", UsuariosDePrueba.CLAVE,
				false, Rol.CAJA);
		UsuariosDePrueba.iniciarSesion(caja);

		assertThatThrownBy(() -> cobro.cobrar(EscenarioCaja.digital(1L, List.of(1L), MedioPago.YAPE, "OP-PREP-1", "450.00")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("todavía no está en producción");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pago WHERE colegio_id = ?", Long.class, colegio)).isZero();
	}

	@Test
	void laListaSeCalculaDeLosDatosYLaPantallaLaMuestra() throws Exception {
		ListaVerificacion lista = estado.paraOperador(colegio);
		assertThat(lista.enPreparacion()).isTrue();
		// Tanda 2: los 14 pasos de la sección 3.5, en su orden; los dos últimos, opcionales.
		assertThat(lista.puntos()).extracting(p -> p.clave()).containsExactlyElementsOf(EstadoPuestaEnMarcha.PASOS);
		assertThat(lista.puntos()).hasSize(15);
		assertThat(lista.puntos().stream().filter(p -> !p.obligatorio()).map(p -> p.clave()).toList())
				.containsExactly(EstadoPuestaEnMarcha.PASARELA, EstadoPuestaEnMarcha.RECAUDACION);
		assertThat(lista.puntos()).allMatch(p -> p.enlace() == null);
		assertThat(lista.puntos().stream().filter(p -> p.clave().equals(EstadoPuestaEnMarcha.DATOS)).findFirst().orElseThrow()
				.hecho()).isTrue();
		assertThat(lista.completa()).isFalse();

		mvc.perform(get("/puesta-en-marcha").with(UsuariosDePrueba.como(promotor))).andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("en preparación")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Pasar a producción")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("paso(s) obligatorio(s)")))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
						"Pasar el colegio a producción"))));
		mvc.perform(post("/puesta-en-marcha/produccion").with(csrf()).with(UsuariosDePrueba.como(promotor)))
				.andExpect(redirectedUrl("/puesta-en-marcha"));
		assertThat(estadoDelColegio()).isEqualTo("PREPARACION");
	}

	@Test
	void losProcesosDeCobroNoRecorrenLaPlataformaNiUnColegioEnPreparacion() {
		long plataforma = EscenarioPlataforma.plataformaId(colegios);

		assertThat(recorrido.activos()).contains(1L, colegio).doesNotContain(plataforma);
		assertThat(recorrido.enProduccion()).contains(1L).doesNotContain(colegio, plataforma);
		assertThat(recorrido.conPlataforma()).contains(1L, colegio, plataforma);
	}

	private static pe.edu.virgenmaria.cuentasclaras.puestaenmarcha.dto.PuntoLista punto(ListaVerificacion lista,
			String clave) {
		return lista.puntos().stream().filter(p -> p.clave().equals(clave)).findFirst().orElseThrow();
	}

	private String estadoDelColegio() {
		return jdbc.queryForObject("SELECT estado FROM colegio WHERE id = ?", String.class, colegio);
	}
}
