package pe.edu.virgenmaria.cuentasclaras.familias;

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
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.familias.dto.AvisoRequest;
import pe.edu.virgenmaria.cuentasclaras.familias.dto.AvisoVista;
import pe.edu.virgenmaria.cuentasclaras.familias.model.DerechoDatos;
import pe.edu.virgenmaria.cuentasclaras.familias.model.TipoAvisoFamilia;
import pe.edu.virgenmaria.cuentasclaras.familias.service.AutoatencionAvisoException;
import pe.edu.virgenmaria.cuentasclaras.familias.service.ServicioAvisosFamilia;
import pe.edu.virgenmaria.cuentasclaras.privacidad.service.AlertasPrivacidad;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * Sprint 7, tanda 3 (Ley 29733, sección 8.3; H14, E28): la familia pide algo sobre sus datos personales por «¿Algo no
 * cuadra?» con su derecho (acceso, rectificación, cancelación u oposición). El plazo es de 20 días hábiles para el acceso y
 * 10 para lo demás (a confirmar por el asesor legal): a los 7 días hábiles sin respuesta es ATENCIÓN y vencido el plazo,
 * CRÍTICA. No lo atiende quien pidió o aprobó un cambio del contacto de esa familia.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class PedidosDatosPersonalesTest {

	/** Lunes 5 de octubre de 2026, 09:00 en Lima. */
	private static final Instant LUNES = Instant.parse("2026-10-05T14:00:00Z");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioAvisosFamilia avisos;

	@Autowired
	private AlertasPrivacidad alertas;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private EscenarioCaja.Familias f;

	private UsuarioAutenticado rosa;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		reloj.fijar(LUNES);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		SecurityContextHolder.clearContext();
		rosa = new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa Huamán", null, true, false, false,
				EnumSet.of(Rol.APODERADO), f.rosa());
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void laFamiliaPideAccesoYVeElPlazo() throws Exception {
		mvc.perform(post("/familia/algo-no-cuadra").with(csrf()).with(UsuariosDePrueba.como(rosa))
				.param("tipo", "DATOS_PERSONALES").param("derecho", "ACCESO")
				.param("texto", "Quiero saber qué datos de mis hijos tiene el colegio"))
				.andExpect(redirectedUrl("/familia/algo-no-cuadra"));

		assertThat(jdbc.queryForMap("SELECT tipo, derecho, pago_id, cuota_id FROM aviso_familia"))
				.containsEntry("tipo", "DATOS_PERSONALES").containsEntry("derecho", "ACCESO");
		// 20 días hábiles desde el lunes 5/10/2026 (8/10 es feriado nacional: Combate de Angamos).
		LocalDate vence = LocalDate.of(2026, 11, 3);
		mvc.perform(get("/familia/algo-no-cuadra").with(UsuariosDePrueba.como(rosa)))
				.andExpect(content().string(containsString("Pediste: <span>Acceso</span>")))
				.andExpect(content().string(containsString("03/11/2026")));
		assertThat(jdbc.queryForObject("SELECT detalle FROM evento_auditoria WHERE accion = 'AVISO_FAMILIA_RECIBIDO'",
				String.class)).contains("Acceso").contains(vence.format(java.time.format.DateTimeFormatter
						.ofPattern("dd/MM/yyyy"))).doesNotContain("Quiero saber");
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.PROMOTORIA);
		List<AvisoVista> bandeja = avisos.bandeja();
		assertThat(bandeja).singleElement().satisfies(a -> {
			assertThat(a.derecho()).isEqualTo("Acceso");
			assertThat(a.venceEl()).isEqualTo(vence);
			assertThat(a.vencido()).isFalse();
		});
	}

	@Test
	void unPedidoSobreDatosLlevaSuDerechoYNoUnPago() {
		UsuariosDePrueba.iniciarSesion(rosa);
		assertThatThrownBy(() -> avisos.enviar(new AvisoRequest(TipoAvisoFamilia.DATOS_PERSONALES, null, null, null,
				"Quiero corregir mi apellido"))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("datos personales");
		Long cuota = EscenarioCaja.cuota(jdbc, f.mateo(), "PEN-2027-03");
		assertThatThrownBy(() -> avisos.enviar(new AvisoRequest(TipoAvisoFamilia.DATOS_PERSONALES, null, cuota,
				DerechoDatos.RECTIFICACION, "Quiero corregir mi apellido"))).isInstanceOf(ReglaNegocioException.class);
		assertThatThrownBy(() -> avisos.enviar(new AvisoRequest(TipoAvisoFamilia.OTRO, null, null,
				DerechoDatos.CANCELACION, "Quiero que borren mis datos"))).isInstanceOf(ReglaNegocioException.class);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM aviso_familia", Long.class)).isZero();
	}

	@Test
	void elPlazoVencidoEsCritico() {
		UsuariosDePrueba.iniciarSesion(rosa);
		avisos.enviar(new AvisoRequest(TipoAvisoFamilia.DATOS_PERSONALES, null, null, DerechoDatos.RECTIFICACION,
				"Mi apellido está mal escrito en la boleta"));
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.PROMOTORIA);
		assertThat(pedidos()).isEmpty();

		// 7 días hábiles después (8/10 es feriado): jueves 15/10 → ATENCIÓN.
		reloj.fijar(lima(LocalDate.of(2026, 10, 15)));
		assertThat(pedidos()).singleElement().satisfies(a -> {
			assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.ATENCION);
			assertThat(a.texto()).contains("rectificación").contains("Vence el").doesNotContain("Quispe", "apellido");
		});
		// 10 días hábiles vencen el 20/10; el 21/10 está vencido → CRÍTICA, y sale al celular de Promotoría.
		reloj.fijar(lima(LocalDate.of(2026, 10, 21)));
		assertThat(pedidos()).singleElement().satisfies(a -> {
			assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.CRITICA);
			assertThat(a.texto()).contains("venció el 20/10/2026");
			assertThat(a.difundible()).isTrue();
		});
		assertThat(avisos.bandeja()).singleElement().satisfies(a -> assertThat(a.vencido()).isTrue());
	}

	@Test
	void quienIntervinoEnElContactoDeLaFamiliaNoAtiendeSuPedido() {
		jdbc.update("INSERT INTO solicitud_cambio (colegio_id, tipo, entidad, entidad_id, resumen, datos, motivo, estado, "
				+ "solicitado_por, resuelto_por, resuelto_en, creado_en, creado_por, actualizado_en) VALUES (1, "
				+ "'CAMBIO_CONTACTO_APODERADO', 'apoderado', ?, 'Cambio de celular', '{}', 'La familia cambió de número', "
				+ "'APROBADA', 'administracion', 'promotor', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'administracion', "
				+ "CURRENT_TIMESTAMP)", f.rosa());
		UsuariosDePrueba.iniciarSesion(rosa);
		Long pedido = avisos.enviar(new AvisoRequest(TipoAvisoFamilia.DATOS_PERSONALES, null, null,
				DerechoDatos.RECTIFICACION, "Ese celular no es mío: corríjanlo"));

		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.PROMOTORIA);
		assertThatThrownBy(() -> avisos.atender(pedido, "Ya lo corregimos"))
				.isInstanceOf(AutoatencionAvisoException.class);
		assertThat(jdbc.queryForObject("SELECT estado FROM aviso_familia WHERE id = ?", String.class, pedido))
				.isEqualTo("ABIERTO");

		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.DIRECCION);
		avisos.atender(pedido, "Revisamos tu pedido y corregimos el celular");
		assertThat(jdbc.queryForObject("SELECT estado FROM aviso_familia WHERE id = ?", String.class, pedido))
				.isEqualTo("ATENDIDO");
	}

	@Test
	void elDerechoNoCambiaDespuesDeEnviado() {
		assertThat(pe.edu.virgenmaria.cuentasclaras.comun.prueba.ColumnasActualizables
				.de(pe.edu.virgenmaria.cuentasclaras.familias.model.AvisoFamilia.class)).doesNotContain("derecho");
		assertThat(EscenarioEscolar.DNI_ROSA).isNotBlank();
	}

	private List<AlertaRevision> pedidos() {
		return alertas.alertas().stream().filter(a -> a.texto().contains("datos personales")).toList();
	}

	private static Instant lima(LocalDate dia) {
		return dia.atTime(9, 0).atZone(ZoneId.of("America/Lima")).toInstant();
	}
}
