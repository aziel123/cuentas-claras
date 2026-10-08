package pe.edu.virgenmaria.cuentasclaras.panel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPanel;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.DespachoMensajes;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.LlamadaRequest;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.LlamadasSemana;
import pe.edu.virgenmaria.cuentasclaras.panel.model.ResultadoLlamada;
import pe.edu.virgenmaria.cuentasclaras.panel.proceso.AvisosPromotoria;
import pe.edu.virgenmaria.cuentasclaras.panel.proceso.ResumenDiarioTarea;
import pe.edu.virgenmaria.cuentasclaras.panel.service.AlertasPanel;
import pe.edu.virgenmaria.cuentasclaras.panel.service.LlamadasControl;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/**
 * Correcciones del sprint 6 que no son la inversión de un ataque de la auditoría (esas están en
 * {@code auditoriaseguridad.AuditoriaSprint6Test}): el reemplazo de una familia que no contesta (S6-M2), quién delega
 * (S6-M2), las anulaciones por aprobar en un aviso diario (S6-B2) y «el resumen no salió» de muchos días (QA-S6-5).
 * Sobre {@link EscenarioPanel} (jueves 15/04/2027).
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class CorreccionesSprint6Test {

	private static final LocalDate JUEVES = LocalDate.of(2027, 4, 15);

	private static final LocalDate LUNES = LocalDate.of(2027, 4, 19);

	@Autowired
	private LlamadasControl llamadas;

	@Autowired
	private AlertasPanel alertasPanel;

	@Autowired
	private AvisosPromotoria avisos;

	@Autowired
	private ResumenDiarioTarea resumen;

	@Autowired
	private DespachoMensajes despacho;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private ServicioCobro cobro;

	@Autowired
	private ServicioAnulacionPagos anulaciones;

	@Autowired
	private ServicioDescuentos descuentos;

	@Autowired
	private BandejaAprobaciones bandeja;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private EscenarioPanel.Datos datos;

	private Usuario promotora;

	private Usuario directora;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		datos = EscenarioPanel.preparar(estructura, alumnos, planes, cobro, anulaciones, descuentos, bandeja, reloj, jdbc);
		SecurityContextHolder.clearContext();
		promotora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora", UsuariosDePrueba.CLAVE, false,
				Rol.PROMOTOR);
		directora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "directora", UsuariosDePrueba.CLAVE, false,
				Rol.DIRECTOR);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private void a(LocalDate dia, int hora, int minuto) {
		reloj.fijar(dia.atTime(hora, minuto).atZone(reloj.getZone()).toInstant());
	}

	/** Una familia nueva (un alumno en 6.° A y un apoderado) que paga su primera cuota el jueves 15/04 en efectivo. */
	private Long familiaQuePagaEnEfectivo(String dniAlumno, String dniApoderado, String celular) {
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		var registro = alumnos.registrar(EscenarioEscolar.conApoderadoNuevo(dniAlumno, "Ríos", "Vega", "Ana",
				LocalDate.of(2016, 3, 3), dniApoderado, "Ríos", "Soto", "Luis", celular, null,
				jdbc.queryForObject("SELECT seccion_id FROM matricula WHERE alumno_id = ?", Long.class, datos.f().mateo())));
		Map<String, Object> primera = jdbc.queryForMap("SELECT id, monto FROM cuota WHERE alumno_id = ? ORDER BY "
				+ "fecha_vencimiento, id LIMIT 1", registro.alumnoId());
		String monto = ((BigDecimal) primera.get("monto")).toPlainString();
		EscenarioCobranza.como(EscenarioCaja.CAJA);
		cobro.cobrar(EscenarioCaja.efectivo(registro.familiaId(), List.of(((Number) primera.get("id")).longValue()), monto,
				monto));
		SecurityContextHolder.clearContext();
		return registro.familiaId();
	}

	/**
	 * S6-M2. Con 4 candidatas, la muestra es de 3. Una familia no contesta dos veces (una hora entre intentos): se
	 * reemplaza por la cuarta (fila REEMPLAZO de la muestra congelada), la plaza sigue esperando una llamada y sale un
	 * aviso de ATENCIÓN al celular de Promotoría, una vez.
	 */
	@Test
	void unaFamiliaQueNoContestaDosVecesSeReemplazaYAvisaAPromotoria() {
		familiaQuePagaEnEfectivo("71234581", "41234581", "923456771");
		familiaQuePagaEnEfectivo("71234582", "41234582", "923456772");
		a(LUNES, 9, 0);
		UsuariosDePrueba.iniciarSesion(promotora);
		LlamadasSemana semana = llamadas.deEstaSemana();
		assertThat(semana.familias()).hasSize(3);
		assertThat(semana.esperadas()).isEqualTo(3);
		Long noContesta = semana.familias().getLast().familiaId();
		llamadas.registrar(noContesta, new LlamadaRequest(ResultadoLlamada.NO_CONTESTA, null));
		a(LUNES, 10, 0);
		llamadas.registrar(noContesta, new LlamadaRequest(ResultadoLlamada.NO_CONTESTA, null));

		List<Map<String, Object>> reemplazos = jdbc.queryForList("SELECT familia_id, reemplaza_familia_id FROM "
				+ "muestra_llamada WHERE motivo = 'REEMPLAZO'");
		assertThat(reemplazos).singleElement().satisfies(r -> {
			assertThat(r.get("reemplaza_familia_id")).isEqualTo(noContesta);
			assertThat(semana.familias()).extracting(LlamadasSemana.Familia::familiaId).doesNotContain((Long) r.get(
					"familia_id"));
		});
		LlamadasSemana despues = llamadas.deEstaSemana();
		assertThat(despues.esperadas()).as("siguen siendo 3 plazas").isEqualTo(3);
		assertThat(despues.hechas()).isZero();
		assertThat(despues.familias()).filteredOn(f -> f.familiaId().equals(noContesta)).singleElement()
				.satisfies(f -> assertThat(f.reemplazada()).isTrue());
		assertThatThrownBy(() -> llamadas.registrar(noContesta, new LlamadaRequest(ResultadoLlamada.CONFIRMA, null)))
				.as("la reemplazada ya no se registra").isInstanceOf(
						pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException.class);
		assertThat(alertasPanel.alertas()).filteredOn(x -> x.aviso() != null
				&& x.aviso().tipo() == TipoAviso.LLAMADA_REEMPLAZADA).singleElement()
				.satisfies(x -> assertThat(x.gravedad()).isEqualTo(AlertaRevision.Gravedad.ATENCION));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'LLAMADA_CONTROL_REEMPLAZADA'",
				Long.class)).isEqualTo(1);
		SecurityContextHolder.clearContext();
		avisos.enColegio(1L, LUNES);
		avisos.enColegio(1L, LUNES);
		assertThat(jdbc.queryForList("SELECT usuario_id FROM mensaje WHERE clave LIKE 'ALERTA:LLAMADA_REEMPLAZADA:%'",
				Long.class)).containsExactly(promotora.getId());
	}

	/** S6-M2: solo Promotoría delega la semana a Dirección, una vez por semana; la semana siguiente vuelve a Promotoría. */
	@Test
	void soloPromotoriaDelegaLaSemanaUnaVez() {
		a(LUNES, 9, 0);
		UsuariosDePrueba.iniciarSesion(directora);
		assertThatThrownBy(() -> llamadas.delegarADireccion()).isInstanceOf(AuthorizationDeniedException.class);
		UsuariosDePrueba.iniciarSesion(promotora);
		llamadas.delegarADireccion();
		assertThatThrownBy(() -> llamadas.delegarADireccion()).isInstanceOf(ReglaNegocioException.class);
		UsuariosDePrueba.iniciarSesion(directora);
		assertThat(llamadas.deEstaSemana()).satisfies(s -> {
			assertThat(s.delegada()).isTrue();
			assertThat(s.puedeRegistrar()).isTrue();
			assertThat(s.puedeDelegar()).isFalse();
		});
		a(LUNES.plusWeeks(1), 9, 0);
		assertThat(llamadas.deEstaSemana().puedeRegistrar()).as("la delegación es de una semana").isFalse();
	}

	/**
	 * S6-B2 (QA-S6-7): dos anulaciones por aprobar del mismo día son UN aviso (antes, cada solicitud nueva era otro aviso y
	 * la cajera podía llenar el tope). Al día siguiente, si siguen pendientes, un aviso más.
	 */
	@Test
	void lasAnulacionesPendientesSonUnAvisoPorDia() {
		a(JUEVES, 9, 0);
		como(EscenarioCaja.CAJA);
		Long uno = cobro.cobrar(EscenarioCaja.efectivo(datos.f().flores(), List.of(cuota(jdbc, datos.f().sebastian(),
				"MAT-2027")), "350.00", "350.00"));
		anulaciones.solicitarDevolucion(uno, EscenarioAprobaciones.MOTIVO_ANULACION);
		SecurityContextHolder.clearContext();
		avisos.enColegio(1L, JUEVES);
		a(JUEVES, 9, 15);
		como(EscenarioCaja.CAJA);
		Long dos = cobro.cobrar(EscenarioCaja.efectivo(datos.f().flores(), List.of(cuota(jdbc, datos.f().sebastian(),
				"PEN-2027-03")), "450.00", "450.00"));
		anulaciones.solicitarDevolucion(dos, EscenarioAprobaciones.MOTIVO_ANULACION);
		SecurityContextHolder.clearContext();
		avisos.enColegio(1L, JUEVES);
		assertThat(jdbc.queryForList("SELECT clave FROM mensaje WHERE usuario_id = ? AND clave LIKE "
				+ "'ALERTA:ANULACION_PAGO_PENDIENTE:%'", String.class, promotora.getId()))
				.containsExactly("ALERTA:ANULACION_PAGO_PENDIENTE:S:" + JUEVES + ":U" + promotora.getId() + ":WHATSAPP");
		LocalDate viernes = JUEVES.plusDays(1);
		a(viernes, 7, 0);
		avisos.enColegio(1L, viernes);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE usuario_id = ? AND clave LIKE "
				+ "'ALERTA:ANULACION_PAGO_PENDIENTE:%'", Long.class, promotora.getId())).isEqualTo(2);
	}

	/**
	 * QA-S6-5 con S6-B1: si el resumen deja de salir 20 días, los últimos 7 días salen uno por uno y los anteriores en una
	 * sola alerta (que no se apaga sola).
	 */
	@Test
	void elResumenQueNoSaleHaceMuchoSeAgrupaYNoSeApaga() {
		a(JUEVES, 19, 30);
		resumen.enColegio(1L, JUEVES);
		despacho.despacharColegio(1L);
		LocalDate dia = JUEVES.plusDays(20);
		a(dia, 21, 30);
		como(EscenarioCobranza.PROMOTORIA);
		List<AlertaRevision> noSalio = alertasPanel.alertas().stream().filter(x -> x.aviso() != null
				&& x.aviso().tipo() == TipoAviso.RESUMEN_NO_SALIO).toList();
		assertThat(noSalio).hasSize(8).allMatch(x -> x.gravedad() == AlertaRevision.Gravedad.CRITICA);
		assertThat(noSalio).filteredOn(x -> x.aviso().referencia().startsWith("RSD:")).singleElement()
				.satisfies(x -> assertThat(x.texto()).contains("Desde el 16/04/2027"));
		assertThat(noSalio).anyMatch(x -> x.aviso().referencia().equals("RS:" + dia));
	}
}
