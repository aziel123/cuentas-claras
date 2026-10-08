package pe.edu.virgenmaria.cuentasclaras.familias.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
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
import pe.edu.virgenmaria.cuentasclaras.familias.dto.AvisoRequest;
import pe.edu.virgenmaria.cuentasclaras.familias.dto.AvisoVista;
import pe.edu.virgenmaria.cuentasclaras.familias.model.TipoAvisoFamilia;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Sprint 5, tanda 2 (decisión 52; G1 y G24): «¿Algo no cuadra?» le llega solo a Promotoría y Dirección; Caja y
 * Administración no lo ven ni lo cierran. 5 avisos por día y por familia. Al atenderlo, la familia recibe un mensaje.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ServicioAvisosFamiliaTest {

	@Autowired
	private ServicioAvisosFamilia avisos;

	@Autowired
	private AlertasFamilias alertas;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private JdbcTemplate jdbc;

	private EscenarioCaja.Familias f;

	private UsuarioAutenticado rosa;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		f = EscenarioCaja.preparar(estructura, alumnos, planes, jdbc);
		SecurityContextHolder.clearContext();
		rosa = new UsuarioAutenticado(40L, 1L, "rosa.familia", "Rosa Huamán", null, true, false, false,
				EnumSet.of(Rol.APODERADO), f.rosa());
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private Long enviar(TipoAvisoFamilia tipo, String texto) {
		como(rosa);
		Long id = avisos.enviar(new AvisoRequest(tipo, null, EscenarioCaja.cuota(jdbc, f.mateo(), "PEN-2027-03"), texto));
		SecurityContextHolder.clearContext();
		return id;
	}

	@Test
	void soloPromotoriaYDireccionLoVen() {
		Long id = enviar(TipoAvisoFamilia.PAGUE_Y_NO_APARECE, "Pagué S/ 450 en caja el lunes y no aparece");
		for (UsuarioAutenticado quien : List.of(EscenarioCobranza.PROMOTORIA, EscenarioCobranza.DIRECCION)) {
			como(quien);
			assertThat(avisos.bandeja()).singleElement().satisfies(a -> {
				assertThat(a.id()).isEqualTo(id);
				assertThat(a.critico()).isTrue();
				assertThat(a.referencia()).isEqualTo("Cuota Pensión marzo 2027");
				assertThat(a.texto()).isEqualTo("Pagué S/ 450 en caja el lunes y no aparece");
			});
		}
		for (UsuarioAutenticado quien : List.of(EscenarioCobranza.CAJA, EscenarioCobranza.ADMINISTRACION)) {
			como(quien);
			assertThatThrownBy(() -> avisos.bandeja()).isInstanceOf(AccessDeniedException.class);
			assertThatThrownBy(() -> avisos.atender(id, "Lo cierro yo")).isInstanceOf(AccessDeniedException.class);
		}
		// Promotoría lo ve como alerta CRÍTICA; la bitácora no guarda el texto de la familia (Ley 29733).
		como(EscenarioCobranza.PROMOTORIA);
		assertThat(alertas.alertas()).anySatisfy(a -> assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.CRITICA));
		Map<String, Object> evento = EscenarioEscolar.ultimoEvento(jdbc, "AVISO_FAMILIA_RECIBIDO");
		assertThat(evento).containsEntry("valor_nuevo", "Pagué y no aparece").containsEntry("nombre_usuario",
				"rosa.familia");
		assertThat(EscenarioEscolar.todaLaBitacora(jdbc)).doesNotContain("el lunes y no aparece");
	}

	@Test
	void limiteDiarioPorFamilia() {
		for (int i = 1; i <= 5; i++) {
			enviar(TipoAvisoFamilia.OTRO, "Consulta número " + i + " de la familia");
		}
		assertThatThrownBy(() -> enviar(TipoAvisoFamilia.OTRO, "La sexta consulta del día"))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("5 avisos hoy");
		assertThat(contar(jdbc, "aviso_familia")).isEqualTo(5);
		// Un aviso «Otro» es ATENCIÓN, no CRÍTICA.
		como(EscenarioCobranza.PROMOTORIA);
		assertThat(alertas.alertas()).singleElement()
				.satisfies(a -> assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.ATENCION));
		// Una cuota de otra familia no se puede referir.
		como(rosa);
		Long deOtra = EscenarioCaja.cuota(jdbc, f.sebastian(), "PEN-2027-03");
		assertThatThrownBy(() -> avisos.enviar(new AvisoRequest(TipoAvisoFamilia.OTRO, null, deOtra, "Otra familia")))
				.isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void atenderAvisaALaFamilia() {
		Long id = enviar(TipoAvisoFamilia.NO_RECONOZCO_PAGO, "Hay un pago que no hice");
		como(EscenarioCobranza.DIRECCION);
		avisos.atender(id, "Revisamos la caja: fue un error de digitación y ya se anuló.");
		SecurityContextHolder.clearContext();

		assertThat(jdbc.queryForMap("SELECT estado, atendido_por FROM aviso_familia WHERE id = ?", id))
				.containsEntry("estado", "ATENDIDO").containsEntry("atendido_por", "director");
		assertThat(jdbc.queryForList("SELECT canal, apoderado_id, parametros FROM mensaje WHERE tipo = 'AVISO_ATENDIDO'"))
				.singleElement().satisfies(m -> {
					assertThat(m).containsEntry("canal", "WHATSAPP").containsEntry("apoderado_id", f.rosa());
					assertThat((String) m.get("parametros")).isEqualTo("02/10/2026");
				});
		como(rosa);
		assertThat(avisos.misAvisos()).singleElement().extracting(AvisoVista::respuesta)
				.isEqualTo("Revisamos la caja: fue un error de digitación y ya se anuló.");
		// Un aviso atendido no se vuelve a atender (en MySQL, además, el trigger).
		como(EscenarioCobranza.PROMOTORIA);
		assertThatThrownBy(() -> avisos.atender(id, "Otra respuesta distinta")).isInstanceOf(ReglaNegocioException.class);
	}
}
