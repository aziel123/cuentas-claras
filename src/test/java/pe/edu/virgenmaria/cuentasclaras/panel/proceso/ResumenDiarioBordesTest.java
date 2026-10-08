package pe.edu.virgenmaria.cuentasclaras.panel.proceso;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioPanel;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.RelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.comunicacion.proceso.DespachoMensajes;
import pe.edu.virgenmaria.cuentasclaras.panel.model.ResumenDiario;
import pe.edu.virgenmaria.cuentasclaras.panel.service.AlertasPanel;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja.cuota;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/**
 * QA sprint 6: bordes del resumen diario (foto) y de su reverificación de 35 días.
 * <ul>
 *   <li>Dado que el resumen del sábado no salió, cuando el lunes a las 07:00 corren los avisos, entonces la promotora
 *       recibe «El resumen diario no salió» (QA-S6-1).</li>
 *   <li>Dado que el resumen del martes no salió y el miércoles y el jueves son feriados, cuando el viernes corren los
 *       avisos, entonces la promotora lo recibe (QA-S6-1).</li>
 *   <li>Dado un colegio cuyo resumen dejó de salir hace más de 35 días, cuando son las 21:30, entonces «el resumen no
 *       salió» sigue siendo CRÍTICA (QA-S6-5).</li>
 *   <li>Dado un pago del jueves borrado por SQL después del resumen del viernes, cuando corre el recálculo del sábado,
 *       entonces las dos fotos (la del día y la del mes) quedan como cambio sin explicación.</li>
 *   <li>Dada una foto de hace exactamente 35 días, cuando corre el recálculo, entonces todavía se compara.</li>
 *   <li>Dado un domingo con un cobro, cuando son las 19:30, entonces sale el resumen con ese cobro.</li>
 * </ul>
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ResumenDiarioBordesTest {

	private static final LocalDate JUEVES = LocalDate.of(2027, 4, 15);

	@Autowired
	private ResumenDiarioTarea tarea;

	@Autowired
	private RecalculoResumenes recalculo;

	@Autowired
	private AvisosPromotoria avisos;

	@Autowired
	private DespachoMensajes despacho;

	@Autowired
	private AlertasPanel alertas;

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

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		datos = EscenarioPanel.preparar(estructura, alumnos, planes, cobro, anulaciones, descuentos, bandeja, reloj, jdbc);
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora", UsuariosDePrueba.CLAVE, false, Rol.PROMOTOR);
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

	/** El resumen del día sale a las 19:30 y el despacho lo envía. */
	private void resumenEnviado(LocalDate dia) {
		a(dia, 19, 30);
		assertThat(tarea.enColegio(1L, dia)).isPresent();
		despacho.despacharColegio(1L);
	}

	private List<String> avisosDeResumenNoSalio(LocalDate dia) {
		return jdbc.queryForList("SELECT clave FROM mensaje WHERE tipo = 'ALERTA_PROMOTORIA' AND clave LIKE ?",
				String.class, "ALERTA:RESUMEN_NO_SALIO:RS:" + dia + ":%");
	}

	private boolean hayAlertaResumenNoSalio(LocalDate dia) {
		como(EscenarioCobranza.PROMOTORIA);
		try {
			return alertas.alertas().stream().anyMatch(a -> a.gravedad() == AlertaRevision.Gravedad.CRITICA
					&& a.aviso() != null && a.aviso().tipo() == TipoAviso.RESUMEN_NO_SALIO
					&& a.aviso().referencia().equals("RS:" + dia));
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	/**
	 * QA-S6-1 (P5). El sábado el resumen no sale (alguien lo suprime). El sábado no hay pasadas de avisos después de las
	 * 21:00, el domingo no salen mensajes y el lunes la alerta solo revisa «ayer» (el domingo, que sin cobros no debía
	 * salir): el aviso nunca llega al celular.
	 */
	@Test
	void debeAvisarAlCelularElLunesQueElResumenDelSabadoNoSalio() {
		LocalDate viernes = JUEVES.plusDays(1);
		LocalDate sabado = JUEVES.plusDays(2);
		LocalDate domingo = JUEVES.plusDays(3);
		LocalDate lunes = JUEVES.plusDays(4);
		resumenEnviado(viernes);
		// Sábado: el resumen de las 19:30 no corre. Pasadas de avisos de la tarde, del domingo y del lunes a las 07:00.
		a(sabado, 20, 45);
		avisos.enColegio(1L, sabado);
		a(domingo, 9, 0);
		avisos.enColegio(1L, domingo);
		a(lunes, 7, 0);
		avisos.enColegio(1L, lunes);

		assertThat(avisosDeResumenNoSalio(sabado)).as("aviso al celular del resumen del sábado").isNotEmpty();
	}

	/** QA-S6-1 (P5) con feriados: el resumen del martes 27/07/2027 no sale; el 28 y el 29 son Fiestas Patrias. */
	@Test
	void debeAvisarAlCelularQueNoSalioElResumenDelDiaPrevioALosFeriados() {
		LocalDate lunes = LocalDate.of(2027, 7, 26);
		LocalDate martes = lunes.plusDays(1);
		resumenEnviado(lunes);
		a(martes, 20, 45);
		avisos.enColegio(1L, martes);
		for (LocalDate feriado : List.of(martes.plusDays(1), martes.plusDays(2))) {
			a(feriado, 7, 0);
			avisos.enColegio(1L, feriado);
		}
		LocalDate viernes = martes.plusDays(3);
		a(viernes, 7, 0);
		avisos.enColegio(1L, viernes);

		assertThat(avisosDeResumenNoSalio(martes)).as("aviso al celular del resumen del martes").isNotEmpty();
	}

	/**
	 * QA-S6-5 (P5). El diseño dice que la alerta «se activa con el primer resumen del colegio». La implementación la
	 * activa solo si hay un resumen en la ventana del recálculo (35 días): si el resumen deja de salir 36 días, la alerta
	 * CRÍTICA desaparece sola y el colegio parece «nuevo».
	 */
	@Test
	void debeSeguirAlertandoQueElResumenNoSaleAunqueLleveMasDe35DiasSinSalir() {
		resumenEnviado(JUEVES);
		LocalDate dia35 = JUEVES.plusDays(35);
		a(dia35, 21, 30);
		assertThat(hayAlertaResumenNoSalio(dia35)).as("a los 35 días sí avisa").isTrue();

		LocalDate dia36 = JUEVES.plusDays(36);
		a(dia36, 21, 30);
		assertThat(hayAlertaResumenNoSalio(dia36)).as("a los 36 días debe seguir avisando").isTrue();
	}

	@Test
	void debeMarcarSinExplicacionLaFotoDelDiaYLaDelMesCuandoSeBorraUnPagoDeUnDiaAnterior() {
		resumenEnviado(JUEVES);
		LocalDate viernes = JUEVES.plusDays(1);
		a(viernes, 10, 0);
		como(EscenarioCaja.CAJA_2);
		cobro.cobrar(EscenarioCaja.efectivo(datos.f().flores(), List.of(cuota(jdbc, datos.f().sebastian(), "MAT-2027")),
				"350.00", "350.00"));
		SecurityContextHolder.clearContext();
		resumenEnviado(viernes);
		Map<String, Object> fotoViernes = jdbc.queryForMap("SELECT cobrado_total, cobrado_mes FROM resumen_diario WHERE "
				+ "fecha = ?", viernes);
		assertThat((BigDecimal) fotoViernes.get("cobrado_total")).isEqualByComparingTo("350.00");
		assertThat((BigDecimal) fotoViernes.get("cobrado_mes")).isEqualByComparingTo("1150.00");

		jdbc.update("DELETE FROM aplicacion_pago WHERE pago_id = ?", datos.pagoYape());
		jdbc.update("DELETE FROM pago WHERE id = ?", datos.pagoYape());
		a(viernes.plusDays(1), 6, 15);

		assertThat(recalculo.enColegio(1L)).as("la foto del jueves (día) y la del viernes (mes)").isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'RESUMEN_DIARIO_CAMBIO'",
				Long.class)).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = "
				+ "'RESUMEN_DIARIO_CAMBIO_EXPLICADO'", Long.class)).isZero();
	}

	@Test
	void debeReverificarLaFotoDeHaceExactamente35Dias() {
		resumenEnviado(JUEVES);
		jdbc.update("DELETE FROM aplicacion_pago WHERE pago_id = ?", datos.pagoYape());
		jdbc.update("DELETE FROM pago WHERE id = ?", datos.pagoYape());

		a(JUEVES.plusDays(36), 6, 15);
		assertThat(recalculo.enColegio(1L)).as("límite documentado: la foto de hace 36 días ya no se compara").isZero();
		a(JUEVES.plusDays(35), 6, 15);
		assertThat(recalculo.enColegio(1L)).as("la foto de hace 35 días sí").isEqualTo(1);
	}

	@Test
	void debeEnviarElResumenDelDomingoSoloPorqueHuboUnCobro() {
		LocalDate domingo = JUEVES.plusDays(3);
		a(domingo, 11, 0);
		como(EscenarioCaja.CAJA_2);
		cobro.cobrar(EscenarioCaja.efectivo(datos.f().flores(), List.of(cuota(jdbc, datos.f().sebastian(), "MAT-2027")),
				"350.00", "350.00"));
		SecurityContextHolder.clearContext();

		a(domingo, 19, 30);
		ResumenDiario foto = tarea.enColegio(1L, domingo).orElseThrow();
		assertThat(foto.getPagosCantidad()).isEqualTo(1);
		assertThat(foto.getCobradoTotal()).isEqualByComparingTo("350.00").hasScaleOf(2);
		assertThat(foto.getCobradoMes()).isEqualByComparingTo("1150.00");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE tipo = 'RESUMEN_DIARIO'", Long.class))
				.isEqualTo(1);
	}
}
