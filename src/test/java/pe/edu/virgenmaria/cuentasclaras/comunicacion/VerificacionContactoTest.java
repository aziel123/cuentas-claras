package pe.edu.virgenmaria.cuentasclaras.comunicacion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.ApoderadoRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Parentesco;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAccesoApoderados;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioFamilias;
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
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.DespachoMensajes;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proveedor.BuzonSimulado;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.service.AlertasContactos;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.EnlacesActivacion;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.ADMINISTRACION;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Correcciones del sprint 5 (S5-A1 y S5-M5): un contacto nuevo se VERIFICA con un enlace de un solo uso enviado a ese
 * contacto (el token se genera en el envío y solo queda su SHA-256) antes de recibir avisos o enlaces; los demás
 * apoderados se enteran; pasadas 48 horas sin verificar, y si un contacto se repite en dos familias, Promotoría lo ve.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class VerificacionContactoTest {

	private static final String CELULAR_JORGE = "+51977666555";

	@Autowired private ServicioFamilias familias;
	@Autowired private ServicioAlumnos alumnos;
	@Autowired private ServicioAccesoApoderados accesos;
	@Autowired private ServicioEstructura estructura;
	@Autowired private ServicioPlanesPension planes;
	@Autowired private DespachoMensajes despacho;
	@Autowired private BuzonSimulado buzon;
	@Autowired private AlertasContactos alertas;
	@Autowired private RelojAjustable reloj;
	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;

	private EscenarioCaja.Familias f;

	@BeforeEach
	void preparar() {
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
		buzon.vaciar();
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private Long agregarJorge() {
		como(ADMINISTRACION);
		Long id = familias.agregarApoderado(f.quispe(), new ApoderadoRequest(TipoDocumento.DNI, "40000045", "Quispe",
				"Mamani", "Jorge", Parentesco.PADRE, "977 666 555", "jorge.quispe@gmail.com", ""));
		SecurityContextHolder.clearContext();
		return id;
	}

	@Test
	void elContactoNuevoSeVerificaConSuEnlaceAntesDeRecibirAvisos() throws Exception {
		Long jorge = agregarJorge();

		// Nace pendiente: un enlace de verificación a cada contacto (sin token en la base) y un aviso a Rosa.
		assertThat(jdbc.queryForList("SELECT destino FROM mensaje WHERE tipo = 'VERIFICACION_CONTACTO' AND apoderado_id = ?",
				String.class, jorge)).containsExactlyInAnyOrder(CELULAR_JORGE, "jorge.quispe@gmail.com");
		assertThat(jdbc.queryForList("SELECT destino FROM mensaje WHERE tipo = 'APODERADO_AGREGADO'", String.class))
				.as("S5-A1: se avisa a los apoderados que ya estaban").contains("+51" + EscenarioEscolar.CELULAR_ROSA);
		como(ADMINISTRACION);
		assertThatThrownBy(() -> accesos.darAcceso(jorge)).as("sin verificar no recibe el enlace del portal")
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("verificado");

		despacho.despacharColegio(1L);
		String sufijo = buzon.ultimaPara(CELULAR_JORGE).orElseThrow().sufijoBoton();
		assertThat(sufijo).startsWith("/verificar/1/");
		String token = sufijo.substring("/verificar/1/".length());
		assertThat(contar(jdbc, "mensaje WHERE parametros LIKE '%" + token + "%'")).isZero();
		assertThat(jdbc.queryForObject("SELECT hash_token FROM verificacion_contacto WHERE contacto = ?", String.class,
				CELULAR_JORGE)).isEqualTo(EnlacesActivacion.hash(token));

		mvc.perform(get(sufijo)).andExpect(status().isOk()).andExpect(content().string(
				org.hamcrest.Matchers.containsString("Confirma tu contacto")));
		mvc.perform(post(sufijo).with(csrf()).param("documento", "99999999")).andExpect(status().is3xxRedirection());
		assertThat(jdbc.queryForObject("SELECT telefono_verificado FROM apoderado WHERE id = ?", String.class, jorge))
				.as("con otro documento no se verifica").isNull();
		mvc.perform(post(sufijo).with(csrf()).param("documento", "40000045")).andExpect(status().is3xxRedirection());
		assertThat(jdbc.queryForObject("SELECT telefono_verificado FROM apoderado WHERE id = ?", String.class, jorge))
				.isEqualTo(CELULAR_JORGE);
		assertThat(EscenarioEscolar.ultimoEvento(jdbc, "CONTACTO_VERIFICADO")).containsEntry("entidad_id", jorge.toString());

		// El enlace es de un solo uso.
		mvc.perform(get(sufijo)).andExpect(content().string(org.hamcrest.Matchers.containsString("ya no sirve")));
		// Ya verificado, recibe el enlace del portal por WhatsApp.
		como(ADMINISTRACION);
		accesos.darAcceso(jorge);
		assertThat(jdbc.queryForList("SELECT destino FROM mensaje WHERE tipo = 'ACTIVACION_CUENTA'", String.class))
				.containsExactly(CELULAR_JORGE);
	}

	@Test
	void cuarentaYOchoHorasSinVerificarEsAlertaParaPromotoria() {
		agregarJorge();
		como(EscenarioCobranza.PROMOTORIA);
		assertThat(alertas.alertas()).noneMatch(a -> a.texto().contains("sin confirmar"));

		reloj.avanzar(Duration.ofHours(49));
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == AlertaRevision.Gravedad.CRITICA
				&& a.texto().contains("sin confirmar por más de 48 horas"));
	}

	@Test
	void elMismoContactoEnDosFamiliasEsAlertaAunqueSeaUnAlias() {
		como(EscenarioCobranza.PROMOTORIA);
		assertThat(alertas.alertas()).noneMatch(a -> a.texto().contains("se repiten"));

		// Otra familia con un alias del correo de Rosa (Gmail lo entrega en su mismo buzón).
		como(ADMINISTRACION);
		Long seccion = jdbc.queryForObject("SELECT seccion_id FROM matricula WHERE alumno_id = ?", Long.class, f.mateo());
		alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("72345644", "Ramos", "Vega", "Lucas", LocalDate.of(2015, 3, 3),
				"40000056", "Ramos", "Soto", "Ana", null, "Rosa.Huaman+lucas@gmail.com", seccion));

		como(EscenarioCobranza.PROMOTORIA);
		List<AlertaRevision> lista = alertas.alertas();
		assertThat(lista).anyMatch(a -> a.gravedad() == AlertaRevision.Gravedad.CRITICA && a.texto().contains("se repiten"));
	}
}
