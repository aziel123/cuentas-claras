package pe.edu.virgenmaria.cuentasclaras.panel.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.FamiliaParaLlamada;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.FamiliasParaLlamada;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.caja.model.MedioPago;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPanel;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.LlamadaRequest;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.LlamadasSemana;
import pe.edu.virgenmaria.cuentasclaras.panel.model.ResultadoLlamada;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;

/**
 * QA sprint 6: bordes de la llamada de control semanal (decisión 77, P17): la muestra, la semana, el efectivo y
 * «No confirma».
 * <ul>
 *   <li>Dada la muestra que la promotora vio el martes, cuando el miércoles una de esas familias ya entró al portal,
 *       entonces la familia sigue en la muestra y la promotora puede registrar «No confirma» (QA-S6-2).</li>
 *   <li>Dada una familia que solo pagó con Yape, cuando se arma la muestra, entonces no está.</li>
 *   <li>Dado el domingo a las 23:30 de Lima, cuando se registra la llamada, entonces es de la semana que empezó el lunes
 *       anterior.</li>
 *   <li>Dada una nota de solo espacios, cuando se registra «No confirma», entonces se rechaza y no queda nada.</li>
 * </ul>
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class LlamadasControlBordesTest {

	private static final LocalDate LUNES = LocalDate.of(2027, 4, 19);

	private static final String NOTA = "Dice que pagó S/ 450.00 en efectivo el 15/04 y no aparece";

	@Autowired
	private LlamadasControl llamadas;

	@Autowired
	private FamiliasParaLlamada perfiles;

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

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		datos = EscenarioPanel.preparar(estructura, alumnos, planes, cobro, anulaciones, descuentos, bandeja, reloj, jdbc);
		EscenarioCobranza.como(EscenarioCaja.CAJA);
		cobro.cobrar(EscenarioCaja.efectivo(datos.f().flores(), List.of(cuota(jdbc, datos.f().sebastian(), "MAT-2027")),
				"350.00", "350.00"));
		SecurityContextHolder.clearContext();
		promotora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora", UsuariosDePrueba.CLAVE, false,
				Rol.PROMOTOR);
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

	/** Una familia nueva (un alumno en 6.° A y un apoderado) que paga su primera cuota el jueves 15/04 con ese medio. */
	private Long familiaQuePaga(String dniAlumno, String dniApoderado, String celular, MedioPago medio) {
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		var registro = alumnos.registrar(EscenarioEscolar.conApoderadoNuevo(dniAlumno, "Ríos", "Vega", "Ana",
				LocalDate.of(2016, 3, 3), dniApoderado, "Ríos", "Soto", "Luis", celular, null,
				jdbc.queryForObject("SELECT seccion_id FROM matricula WHERE alumno_id = ?", Long.class, datos.f().mateo())));
		var primera = jdbc.queryForMap("SELECT id, monto FROM cuota WHERE alumno_id = ? ORDER BY fecha_vencimiento, id "
				+ "LIMIT 1", registro.alumnoId());
		String monto = ((BigDecimal) primera.get("monto")).toPlainString();
		EscenarioCobranza.como(EscenarioCaja.CAJA);
		List<Long> cuotas = List.of(((Number) primera.get("id")).longValue());
		cobro.cobrar(medio == MedioPago.EFECTIVO ? EscenarioCaja.efectivo(registro.familiaId(), cuotas, monto, monto)
				: EscenarioCaja.digital(registro.familiaId(), cuotas, medio, "YP" + dniAlumno, monto));
		SecurityContextHolder.clearContext();
		return registro.familiaId();
	}

	/** La familia {@code x} pasa a usar el portal: su apoderado tiene cuenta en línea activa y ya entró. */
	private void entraAlPortal(Long familiaId) {
		Long apoderado = jdbc.queryForObject("SELECT MIN(id) FROM apoderado WHERE familia_id = ?", Long.class, familiaId);
		String dni = jdbc.queryForObject("SELECT numero_documento FROM apoderado WHERE id = ?", String.class, apoderado);
		Usuario cuenta = ContextoColegio.en(1L, () -> usuarios.save(Usuario.deApoderado(dni, "Apoderado " + dni, null,
				codificador.encode(UsuariosDePrueba.CLAVE), apoderado)));
		jdbc.update("UPDATE usuario SET ultimo_ingreso_en = ? WHERE id = ?", LocalDateTime.now(reloj), cuenta.getId());
	}

	private static FamiliaParaLlamada conPortal(FamiliaParaLlamada f) {
		return new FamiliaParaLlamada(f.familiaId(), f.nombre(), f.apoderadosActivos(), true, f.contactos());
	}

	private static boolean contiene(List<FamiliaParaLlamada> muestra, Long familiaId) {
		return muestra.stream().anyMatch(f -> f.familiaId().equals(familiaId));
	}

	/**
	 * QA-S6-2. La muestra se recalcula en cada consulta con los perfiles de HOY: si una familia que la promotora ya vio
	 * baja de prioridad (activa su portal, o Administración le agrega un apoderado), puede salir de la muestra a mitad de
	 * semana y {@code registrar} responde 404. La familia que no confirma no se puede registrar.
	 * <p>
	 * Para que la prueba sea determinista se busca una semilla con la que esa familia sale de la muestra (con la misma
	 * función {@link MuestraLlamadas#elegir}) y se deja como la semilla secreta de la semana.
	 */
	@Disabled("QA-S6-2: la muestra de la llamada de control cambia a mitad de semana si una familia mostrada cambia de "
			+ "prioridad; registrar su resultado da 404 (LlamadasControl.muestra / registrar)")
	@Test
	void debeDejarRegistrarLaLlamadaAUnaFamiliaYaMostradaAunqueActiveSuPortalAMitadDeSemana() {
		familiaQuePaga("71234591", "41234591", "923456791", MedioPago.EFECTIVO);
		// Una de las cuatro ya usa el portal desde antes (menor prioridad): así la plaza al azar puede caer en ella.
		entraAlPortal(familiaQuePaga("71234592", "41234592", "923456792", MedioPago.EFECTIVO));
		List<Long> ids = jdbc.queryForList("SELECT DISTINCT familia_id FROM pago WHERE medio = 'EFECTIVO' ORDER BY "
				+ "familia_id", Long.class);
		assertThat(ids).hasSize(4);
		UsuariosDePrueba.iniciarSesion(promotora);
		List<FamiliaParaLlamada> antes = perfiles.de(ids);
		SecurityContextHolder.clearContext();
		long semilla = 0;
		Long sale = null;
		for (long s = 1; s < 10_000 && sale == null; s++) {
			for (FamiliaParaLlamada x : MuestraLlamadas.elegir(s, antes, 3)) {
				List<FamiliaParaLlamada> despues = new ArrayList<>();
				antes.forEach(f -> despues.add(f.familiaId().equals(x.familiaId()) ? conPortal(f) : f));
				if (!contiene(MuestraLlamadas.elegir(s, despues, 3), x.familiaId())) {
					semilla = s;
					sale = x.familiaId();
					break;
				}
			}
		}
		assertThat(sale).as("hay una semilla con la que la familia sale de la muestra").isNotNull();
		jdbc.update("INSERT INTO semilla_muestreo (colegio_id, ambito, fecha, semilla, creado_en, creado_por, "
				+ "actualizado_en, version) VALUES (1, 'LLAMADA_CONTROL', ?, ?, ?, 'sistema.panel', ?, 0)", LUNES, semilla,
				LUNES.atStartOfDay(), LUNES.atStartOfDay());

		a(LUNES.plusDays(1), 10, 0);
		UsuariosDePrueba.iniciarSesion(promotora);
		Long familia = sale;
		assertThat(llamadas.deEstaSemana().familias()).extracting(LlamadasSemana.Familia::familiaId).contains(familia);
		SecurityContextHolder.clearContext();

		a(LUNES.plusDays(1), 18, 0);
		entraAlPortal(familia);

		a(LUNES.plusDays(2), 10, 0);
		UsuariosDePrueba.iniciarSesion(promotora);
		// Hoy: RecursoNoEncontradoException («Esa familia no está en la llamada de control de esta semana», 404).
		Long llamada = llamadas.registrar(familia, new LlamadaRequest(ResultadoLlamada.NO_CONFIRMA, NOTA));
		assertThat(llamada).isNotNull();
		assertThat(llamadas.deEstaSemana().familias()).as("la muestra que vio el martes no cambia el miércoles")
				.extracting(LlamadasSemana.Familia::familiaId).contains(familia);
	}

	@Test
	void noDebeIncluirEnLaMuestraAUnaFamiliaQueSoloPagoConYape() {
		Long soloYape = familiaQuePaga("71234593", "41234593", "923456793", MedioPago.YAPE);
		a(LUNES.plusDays(1), 10, 0);
		UsuariosDePrueba.iniciarSesion(promotora);
		LlamadasSemana semana = llamadas.deEstaSemana();
		assertThat(semana.familias()).extracting(LlamadasSemana.Familia::familiaId)
				.containsExactlyInAnyOrder(datos.f().quispe(), datos.f().flores()).doesNotContain(soloYape);
		assertThatThrownBy(() -> llamadas.registrar(soloYape, new LlamadaRequest(ResultadoLlamada.CONFIRMA, null)))
				.isInstanceOf(pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException.class);
	}

	@Test
	void debeRegistrarEnLaSemanaDeLimaUnaLlamadaDelDomingoALas2330() {
		LocalDate domingo = LUNES.plusDays(6);
		a(domingo, 23, 30);
		UsuariosDePrueba.iniciarSesion(promotora);
		llamadas.registrar(datos.f().quispe(), new LlamadaRequest(ResultadoLlamada.CONFIRMA, null));

		assertThat(jdbc.queryForObject("SELECT semana FROM llamada_control", LocalDate.class)).isEqualTo(LUNES);
		assertThat(llamadas.avance().hechas()).isEqualTo(1);
		a(domingo.plusDays(1), 0, 30);
		assertThat(llamadas.avance().semana()).isEqualTo(LUNES.plusWeeks(1));
		assertThat(llamadas.avance().hechas()).as("la semana nueva empieza sin llamadas").isZero();
	}

	@Test
	void noDebeAceptarNoConfirmaConUnaNotaDeSoloEspacios() {
		a(LUNES.plusDays(1), 10, 0);
		UsuariosDePrueba.iniciarSesion(promotora);
		assertThatThrownBy(() -> llamadas.registrar(datos.f().quispe(), new LlamadaRequest(ResultadoLlamada.NO_CONFIRMA,
				"            "))).isInstanceOf(ReglaNegocioException.class);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM llamada_control", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion LIKE 'LLAMADA_CONTROL%'",
				Long.class)).isZero();
	}
}
