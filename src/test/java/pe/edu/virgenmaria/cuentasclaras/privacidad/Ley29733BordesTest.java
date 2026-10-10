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
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Parentesco;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioFamilias;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Colegio;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.ColegioRepository;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.fecha.CalendarioHabil;
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
import pe.edu.virgenmaria.cuentasclaras.familias.service.ServicioAvisosFamilia;
import pe.edu.virgenmaria.cuentasclaras.privacidad.dto.DatoVencido;
import pe.edu.virgenmaria.cuentasclaras.privacidad.dto.FiltroAccesos;
import pe.edu.virgenmaria.cuentasclaras.privacidad.service.AccesosDatosPersonales;
import pe.edu.virgenmaria.cuentasclaras.privacidad.service.AlertasPrivacidad;
import pe.edu.virgenmaria.cuentasclaras.privacidad.service.DatosConPlazoVencido;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;
import pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QA del sprint 7 (Ley 29733, sección 8; E27 y E28) sobre H2 y con el reloj en hora de Lima:
 * <ul>
 *   <li>pedidos sobre datos personales con su plazo en días hábiles (fines de semana, feriados nacionales y del colegio,
 *       fin de mes y de año, la hora de Lima) y sus alertas a los 7 días hábiles y al vencer;</li>
 *   <li>qué pantallas registran quién vio datos personales y cuáles no;</li>
 *   <li>«Mis datos», los reportes de accesos y de datos con plazo vencido.</li>
 * </ul>
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class Ley29733BordesTest {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ServicioAvisosFamilia avisos;

	@Autowired
	private AlertasPrivacidad alertas;

	@Autowired
	private AccesosDatosPersonales accesos;

	@Autowired
	private DatosConPlazoVencido vencidos;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioFamilias familias;

	@Autowired
	private ServicioPlanesPension planes;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ColegioRepository colegios;

	@Autowired
	private PasswordEncoder codificador;

	@Autowired
	private CalendarioHabil calendario;

	@Autowired
	private RelojAjustable reloj;

	@Autowired
	private JdbcTemplate jdbc;

	private EscenarioCaja.Familias f;

	private UsuarioAutenticado rosa;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		calendario.invalidarTodo();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
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
		calendario.invalidarTodo();
	}

	// ------------------------------------------------------------------ plazos y alertas de los pedidos

	@Test
	void unPedidoRecibidoUnSabadoEmpiezaAContarElLunesYAvisaAlSeptimoDiaHabil() {
		reloj.fijar(lima(2026, 10, 10, 10, 0));
		pedido(DerechoDatos.RECTIFICACION);

		assertThat(venceEl()).isEqualTo(LocalDate.of(2026, 10, 23));
		reloj.fijar(lima(2026, 10, 19, 18, 0));
		assertThat(pedidos()).as("lunes 19: 6 días hábiles").isEmpty();
		reloj.fijar(lima(2026, 10, 20, 8, 0));
		assertThat(pedidos()).as("martes 20: 7 días hábiles").singleElement()
				.satisfies(a -> assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.ATENCION));
		reloj.fijar(lima(2026, 10, 23, 23, 59));
		assertThat(pedidos()).as("viernes 23 a las 23:59: último día, todavía no vence").singleElement()
				.satisfies(a -> assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.ATENCION));
		reloj.fijar(lima(2026, 10, 24, 0, 1));
		assertThat(pedidos()).as("sábado 24: venció").singleElement()
				.satisfies(a -> assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.CRITICA));
	}

	@Test
	void unPedidoDeLas2330EnLimaCuentaDesdeEseDiaAunqueEnUtcSeaElSiguiente() {
		// Jueves 15/10/2026 a las 23:30 en Lima = viernes 16 a las 04:30 UTC.
		reloj.fijar(lima(2026, 10, 15, 23, 30));
		pedido(DerechoDatos.OPOSICION);

		assertThat(venceEl()).isEqualTo(LocalDate.of(2026, 10, 29));
	}

	@Test
	void unDiaNoLaborableDelColegioCorreElPlazo() {
		diaNoLaborable(1L, LocalDate.of(2026, 10, 14));
		reloj.fijar(lima(2026, 10, 10, 10, 0));
		pedido(DerechoDatos.CANCELACION);

		assertThat(venceEl()).isEqualTo(LocalDate.of(2026, 10, 26));
	}

	@Test
	void unDiaNoLaborableDeOtroColegioNoCorreElPlazo() {
		long colegioB = colegios.save(new Colegio("Colegio de Prueba B")).getId();
		diaNoLaborable(colegioB, LocalDate.of(2026, 10, 14));
		reloj.fijar(lima(2026, 10, 10, 10, 0));
		pedido(DerechoDatos.CANCELACION);

		assertThat(venceEl()).isEqualTo(LocalDate.of(2026, 10, 23));
	}

	@Test
	void unPedidoDeAccesoCruzaNavidadYAnioNuevo() {
		reloj.fijar(lima(2026, 12, 18, 9, 0));
		pedido(DerechoDatos.ACCESO);

		// 20 días hábiles sin el 25/12 ni el 1/1.
		assertThat(venceEl()).isEqualTo(LocalDate.of(2027, 1, 19));
		reloj.fijar(lima(2027, 1, 20, 9, 0));
		assertThat(pedidos()).singleElement().satisfies(a -> {
			assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.CRITICA);
			assertThat(a.texto()).contains("venció el 19/01/2027").doesNotContain("Quispe").doesNotContain("Rosa");
		});
	}

	@Test
	void unPedidoAtendidoYaNoAlertaAunqueHayaVencido() {
		reloj.fijar(lima(2026, 10, 5, 9, 0));
		Long pedido = pedido(DerechoDatos.RECTIFICACION);
		reloj.fijar(lima(2026, 11, 30, 9, 0));
		assertThat(pedidos()).singleElement()
				.satisfies(a -> assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.CRITICA));

		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.DIRECCION);
		avisos.atender(pedido, "Corregimos el apellido en la ficha y en tus boletas");

		assertThat(pedidos()).isEmpty();
	}

	@Test
	void unAvisoQueNoEsSobreDatosPersonalesNoTienePlazoNiAlertaDeLaLey() {
		reloj.fijar(lima(2026, 10, 5, 9, 0));
		UsuariosDePrueba.iniciarSesion(rosa);
		avisos.enviar(new AvisoRequest(TipoAvisoFamilia.OTRO, null, null, "El horario de caja cambió y no nos avisaron"));
		reloj.fijar(lima(2026, 12, 30, 9, 0));

		assertThat(pedidos()).isEmpty();
		assertThat(bandeja()).singleElement().satisfies(a -> assertThat(a.venceEl()).isNull());
	}

	// ------------------------------------------------------------------ qué pantallas registran

	@Test
	void laListaDeMorososQuedaRegistradaSinNombres() throws Exception {
		Usuario promotora = personal("promotora", Rol.PROMOTOR);

		mvc.perform(get("/panel/morosos").with(UsuariosDePrueba.como(promotora))).andExpect(status().isOk());

		assertThat(jdbc.queryForList("SELECT tipo FROM acceso_dato_personal WHERE usuario_id = ?", String.class,
				promotora.getId())).containsExactly("MOROSOS");
	}

	@Test
	void editarUnAlumnoQuedaRegistradoComoFichaDelAlumno() throws Exception {
		Usuario administracion = personal("lucia.adm", Rol.ADMINISTRACION);

		mvc.perform(get("/alumnos/{id}/editar", f.sebastian()).with(UsuariosDePrueba.como(administracion)))
				.andExpect(status().isOk());

		assertThat(jdbc.queryForMap("SELECT tipo, alumno_id, familia_id FROM acceso_dato_personal"))
				.containsEntry("tipo", "FICHA_ALUMNO").containsEntry("alumno_id", f.sebastian())
				.containsEntry("familia_id", f.flores());
	}

	@Test
	void elDetalleDeUnCambioDeContactoDeApoderadoQuedaRegistradoYElDeOtraSolicitudNo() throws Exception {
		Usuario promotora = personal("promotora", Rol.PROMOTOR);
		Long contacto = solicitud("CAMBIO_CONTACTO_APODERADO", "apoderado", f.pedro());
		Long roles = solicitud("CAMBIO_ROLES", "usuario", promotora.getId());

		mvc.perform(get("/aprobaciones/{id}", contacto).with(UsuariosDePrueba.como(promotora))).andExpect(status().isOk());
		mvc.perform(get("/aprobaciones/{id}", roles).with(UsuariosDePrueba.como(promotora))).andExpect(status().isOk());

		assertThat(jdbc.queryForList("SELECT CONCAT(tipo, ' ', familia_id) FROM acceso_dato_personal", String.class))
				.containsExactly("APROBACION_CONTACTO " + f.flores());
	}

	/**
	 * QA-S7-4. Dado que Administración abre el estado de cuenta o el cronograma de un alumno (muestran su nombre y su DNI
	 * COMPLETO), cuando la página se muestra, entonces debe quedar en el registro de accesos como la ficha del alumno. Hoy
	 * no se registra: se pueden recorrer los DNI de todos los alumnos por esas pantallas sin dejar rastro y sin sumar a la
	 * alerta de más de 50 fichas en un día.
	 */
	@Test
	void elEstadoDeCuentaYElCronogramaDeUnAlumnoQuedanRegistrados() throws Exception {
		Usuario administracion = personal("lucia.adm", Rol.ADMINISTRACION);

		mvc.perform(get("/alumnos/{id}/estado-cuenta", f.mateo()).with(UsuariosDePrueba.como(administracion)))
				.andExpect(status().isOk()).andExpect(content().string(containsString(EscenarioEscolar.DNI_MATEO)));
		mvc.perform(get("/alumnos/{id}/cronograma", f.mateo()).with(UsuariosDePrueba.como(administracion)))
				.andExpect(status().isOk()).andExpect(content().string(containsString(EscenarioEscolar.DNI_MATEO)));

		assertThat(jdbc.queryForList("SELECT CONCAT(tipo, ' ', alumno_id) FROM acceso_dato_personal", String.class))
				.containsExactly("FICHA_ALUMNO " + f.mateo(), "FICHA_ALUMNO " + f.mateo());
	}

	/**
	 * Correcciones del sprint 7 (S7-B1): la pantalla de cobro de caja SÍ queda en el registro de accesos, con su propio
	 * tipo (COBRO) y la familia; antes no dejaba rastro (la tanda 3 la excluía para no disparar la alerta de 50 fichas).
	 */
	@Test
	void laPantallaDeCobroDeCajaSeRegistraComoCobro() throws Exception {
		Usuario cajera = personal("caja.uno", Rol.CAJA);

		mvc.perform(get("/caja/familias/{id}", f.quispe()).with(UsuariosDePrueba.como(cajera))).andExpect(status().isOk());

		assertThat(jdbc.queryForList("SELECT CONCAT(tipo, ' ', familia_id) FROM acceso_dato_personal WHERE usuario_id = ?",
				String.class, cajera.getId())).containsExactly("COBRO " + f.quispe());
	}

	/** S7-B1: las pantallas de cobro no cuentan para la alerta de más de 50 fichas en un día (una cajera abre decenas). */
	@Test
	void cienPantallasDeCobroEnUnDiaNoSonAlerta() {
		Usuario cajera = personal("caja.uno", Rol.CAJA);
		for (int i = 0; i < 100; i++) {
			jdbc.update("INSERT INTO acceso_dato_personal (colegio_id, usuario_id, tipo, familia_id, cantidad, ip, creado_en, "
					+ "creado_por, actualizado_en) VALUES (1, ?, 'COBRO', ?, 1, '192.0.2.51', ?, ?, ?)", cajera.getId(),
					f.quispe(), LocalDateTime.of(2026, 10, 2, 8, 0).plusMinutes(i), cajera.getNombreUsuario(),
					LocalDateTime.of(2026, 10, 2, 8, 0).plusMinutes(i));
		}
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.PROMOTORIA);

		reloj.fijar(lima(2026, 10, 2, 11, 0));
		assertThat(alertas.alertas()).noneSatisfy(a -> assertThat(a.texto()).contains("caja.uno"));
	}

	@Test
	void lasFamiliasQueVenSusDatosYSuEstadoDeCuentaNoSeRegistran() throws Exception {
		mvc.perform(get("/familia/mis-datos").with(UsuariosDePrueba.como(rosa))).andExpect(status().isOk());
		mvc.perform(get("/familia/estado-de-cuenta").with(UsuariosDePrueba.como(rosa))).andExpect(status().isOk());

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM acceso_dato_personal", Long.class)).isZero();
	}

	@Test
	void lasFichasVistasALas2330EnLimaCuentanParaEseDia() {
		Usuario administracion = personal("lucia.adm", Rol.ADMINISTRACION);
		// 51 fichas el jueves 1/10 entre las 23:00 y las 23:51 en Lima (en UTC ya es viernes).
		for (int i = 0; i < 51; i++) {
			fichaVista(administracion, LocalDateTime.of(2026, 10, 1, 23, 0).plusMinutes(i));
		}
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.PROMOTORIA);

		reloj.fijar(lima(2026, 10, 1, 23, 55));
		assertThat(alertas.alertas()).anySatisfy(a -> assertThat(a.texto()).contains("lucia.adm vio hoy 51 fichas"));
		reloj.fijar(lima(2026, 10, 2, 0, 5));
		assertThat(alertas.alertas()).noneSatisfy(a -> assertThat(a.texto()).contains("lucia.adm"));
	}

	@Test
	void cincuentaFichasRepartidasEntreDosDiasNoSonAlerta() {
		Usuario administracion = personal("lucia.adm", Rol.ADMINISTRACION);
		for (int i = 0; i < 30; i++) {
			fichaVista(administracion, LocalDateTime.of(2026, 10, 1, 23, 20).plusMinutes(i));
		}
		for (int i = 0; i < 30; i++) {
			fichaVista(administracion, LocalDateTime.of(2026, 10, 2, 0, 0).plusMinutes(i));
		}
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.PROMOTORIA);

		reloj.fijar(lima(2026, 10, 2, 9, 0));
		assertThat(alertas.alertas()).noneSatisfy(a -> assertThat(a.texto()).contains("lucia.adm"));
	}

	// ------------------------------------------------------------------ reportes

	@Test
	void laConsultaDeAccesosAdmite92DiasYRechaza93() {
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.PROMOTORIA);
		LocalDate desde = LocalDate.of(2026, 7, 1);

		assertThat(accesos.consultar(new FiltroAccesos(null, desde, desde.plusDays(92)))).isEmpty();
		assertThatThrownBy(() -> accesos.consultar(new FiltroAccesos(null, desde, desde.plusDays(93))))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("92 días");
		assertThatThrownBy(() -> accesos.consultar(new FiltroAccesos(null, desde.plusDays(1), desde)))
				.isInstanceOf(ReglaNegocioException.class);
	}

	@Test
	void soloPromotoriaVeLosReportesDeLaLey() throws Exception {
		for (Rol rol : new Rol[] { Rol.DIRECTOR, Rol.ADMINISTRACION, Rol.CAJA, Rol.DOCENTE }) {
			mvc.perform(get("/auditoria/accesos").with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
			mvc.perform(get("/auditoria/datos-vencidos").with(UsuariosDePrueba.como(rol))).andExpect(status().isForbidden());
		}
		mvc.perform(get("/auditoria/accesos").with(UsuariosDePrueba.como(rosa))).andExpect(status().isForbidden());
		mvc.perform(get("/auditoria/accesos")).andExpect(status().is3xxRedirection());
	}

	@Test
	void unaFechaMalEscritaEnElReporteDeAccesosNoEsUnError500() throws Exception {
		mvc.perform(get("/auditoria/accesos").param("desde", "2026-13-45").param("usuarioId", "abc")
				.with(UsuariosDePrueba.como(EscenarioCobranza.PROMOTORIA))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Revisa los filtros")));
	}

	@Test
	void unaFamiliaQueSeFueHaceExactamenteUnAnioTodaviaNoApareceYUnDiaDespuesSi() {
		// Hoy (reloj de las pruebas): 2/10/2026. Retiro el 2/10/2025: justo 12 meses.
		retirarSinDeuda(f.quispe(), LocalDate.of(2025, 10, 2));
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.PROMOTORIA);
		assertThat(vencidos.familias()).isEmpty();

		reloj.fijar(lima(2026, 10, 3, 9, 0));
		assertThat(vencidos.familias()).extracting(DatoVencido::familiaId).containsExactly(f.quispe());
	}

	@Test
	void unaFamiliaConUnHijoActivoNoApareceAunqueElOtroSeHayaRetirado() {
		jdbc.update("UPDATE alumno SET estado = 'RETIRADO', retirado_en = ?, retirado_por = 'director', motivo_retiro = "
				+ "'Se cambió a otro colegio' WHERE id = ?", LocalDate.of(2024, 3, 1), f.valeria());
		jdbc.update("DELETE FROM cuota WHERE alumno_id IN (SELECT id FROM alumno WHERE familia_id = ?)", f.quispe());
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.PROMOTORIA);

		assertThat(vencidos.familias()).isEmpty();
	}

	@Test
	void elReporteDeDatosVencidosNoMuestraLosContactos() throws Exception {
		retirarSinDeuda(f.quispe(), LocalDate.of(2025, 1, 15));

		mvc.perform(get("/auditoria/datos-vencidos").with(UsuariosDePrueba.como(EscenarioCobranza.PROMOTORIA)))
				.andExpect(status().isOk()).andExpect(content().string(allOf(containsString("Quispe"),
						not(containsString(EscenarioEscolar.CELULAR_ROSA)), not(containsString(EscenarioEscolar.CORREO_ROSA)),
						not(containsString(EscenarioEscolar.DNI_ROSA)))));
	}

	// ------------------------------------------------------------------ Mis datos

	/**
	 * QA-S7-8. Dada una familia con dos apoderados (Rosa y Juan, por ejemplo padres separados), cuando Rosa abre «Mis
	 * datos», entonces ve sus datos y los de sus hijos, pero NO el DNI, el celular, el correo ni el RUC de Juan: son datos
	 * personales de otra persona (la página misma dice «los datos tuyos y de tus hijos»). Hoy {@code MisDatos} muestra los
	 * datos completos de todos los apoderados activos de la familia.
	 */
	@Test
	void misDatosNoMuestraElDocumentoNiLosContactosDeOtroApoderado() throws Exception {
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.ADMINISTRACION);
		familias.agregarApoderado(f.quispe(), EscenarioEscolar.apoderado("40112233", "Quispe", "Juan", Parentesco.PADRE,
				"976540129", "juan.quispe@gmail.com", null));
		SecurityContextHolder.clearContext();

		mvc.perform(get("/familia/mis-datos").with(UsuariosDePrueba.como(rosa))).andExpect(status().isOk())
				.andExpect(content().string(allOf(containsString(EscenarioEscolar.DNI_ROSA), not(containsString("40112233")),
						not(containsString("976540129")), not(containsString("juan.quispe")))));
	}

	@Test
	void misDatosMuestraLosDatosDeLaPropiaApoderada() throws Exception {
		mvc.perform(get("/familia/mis-datos").with(UsuariosDePrueba.como(rosa))).andExpect(status().isOk())
				.andExpect(content().string(allOf(containsString(EscenarioEscolar.DNI_ROSA),
						containsString(EscenarioEscolar.CORREO_ROSA), containsString(EscenarioEscolar.DNI_MATEO),
						not(containsString(EscenarioCaja.DNI_PEDRO)), not(containsString(EscenarioCaja.DNI_SEBASTIAN)))));
	}

	@Test
	void elAvisoDePrivacidadMuestraLosPlazosConfigurados() throws Exception {
		mvc.perform(get("/privacidad")).andExpect(status().isOk())
				.andExpect(content().string(allOf(containsString(">20</span> días hábiles"),
						containsString(">10</span>"))));
	}

	// ------------------------------------------------------------------ apoyo

	private Long pedido(DerechoDatos derecho) {
		UsuariosDePrueba.iniciarSesion(rosa);
		Long id = avisos.enviar(new AvisoRequest(TipoAvisoFamilia.DATOS_PERSONALES, null, null, derecho,
				"Pedido de la familia sobre sus datos personales"));
		SecurityContextHolder.clearContext();
		return id;
	}

	private List<AvisoVista> bandeja() {
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.PROMOTORIA);
		return avisos.bandeja();
	}

	private LocalDate venceEl() {
		return bandeja().getFirst().venceEl();
	}

	private List<AlertaRevision> pedidos() {
		UsuariosDePrueba.iniciarSesion(EscenarioCobranza.PROMOTORIA);
		return alertas.alertas().stream().filter(a -> a.texto().contains("datos personales")).toList();
	}

	private void diaNoLaborable(long colegioId, LocalDate fecha) {
		jdbc.update("INSERT INTO feriado (colegio_id, fecha, descripcion, vigente, creado_en, creado_por, actualizado_en) "
				+ "VALUES (?, ?, 'Aniversario del colegio', TRUE, CURRENT_TIMESTAMP, 'directora', CURRENT_TIMESTAMP)",
				colegioId, fecha);
		calendario.invalidarTodo();
	}

	private Long solicitud(String tipo, String entidad, Long entidadId) {
		jdbc.update("INSERT INTO solicitud_cambio (colegio_id, tipo, entidad, entidad_id, resumen, datos, motivo, estado, "
				+ "pendiente, solicitado_por, creado_en, creado_por, actualizado_en) VALUES (1, ?, ?, ?, 'Cambio pedido', "
				+ "'{}', 'La familia cambió de número', 'PENDIENTE', TRUE, 'administracion', CURRENT_TIMESTAMP, "
				+ "'administracion', CURRENT_TIMESTAMP)", tipo, entidad, entidadId);
		return jdbc.queryForObject("SELECT MAX(id) FROM solicitud_cambio", Long.class);
	}

	private Usuario personal(String nombre, Rol rol) {
		return UsuariosDePrueba.guardar(usuarios, codificador, 1L, nombre, UsuariosDePrueba.CLAVE, false, rol);
	}

	private void fichaVista(Usuario quien, LocalDateTime cuando) {
		jdbc.update("INSERT INTO acceso_dato_personal (colegio_id, usuario_id, tipo, familia_id, cantidad, ip, creado_en, "
				+ "creado_por, actualizado_en) VALUES (1, ?, 'FICHA_FAMILIA', ?, 1, '192.0.2.50', ?, ?, ?)", quien.getId(),
				f.quispe(), cuando, quien.getNombreUsuario(), cuando);
	}

	private void retirarSinDeuda(Long familiaId, LocalDate retiro) {
		jdbc.update("UPDATE alumno SET estado = 'RETIRADO', retirado_en = ?, retirado_por = 'director', motivo_retiro = "
				+ "'Se mudaron a otra ciudad' WHERE familia_id = ?", retiro, familiaId);
		jdbc.update("DELETE FROM cuota WHERE alumno_id IN (SELECT id FROM alumno WHERE familia_id = ?)", familiaId);
	}

	private static Instant lima(int anio, int mes, int dia, int hora, int minuto) {
		return LocalDateTime.of(anio, mes, dia, hora, minuto).atZone(LIMA).toInstant();
	}
}
