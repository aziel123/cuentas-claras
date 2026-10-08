package pe.edu.virgenmaria.cuentasclaras.auditoriaseguridad;

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
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ConteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.dto.ReconteoRequest;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCierreCaja;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCaja;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
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
import pe.edu.virgenmaria.cuentasclaras.panel.service.PanelPromotoria;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;

/**
 * Auditoría de seguridad y antifraude del sprint 6, rama auditoria/sprint-6. Cada prueba REPRODUCE un ataque y queda en
 * verde mientras el hallazgo siga abierto: cuando se corrija, la prueba fallará y habrá que invertir sus aserciones.
 * Sobre EscenarioPanel: el jueves 15/04/2027 la familia Quispe pagó en efectivo y por Yape; la familia Flores, de
 * Sebastián, tiene la matrícula y marzo vencidos y ningún pago registrado; la caja de la cajera sigue abierta.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AuditoriaSprint6Test {

	private static final LocalDate JUEVES = LocalDate.of(2027, 4, 15);

	private static final LocalDate LUNES = LocalDate.of(2027, 4, 19);

	private static final String ALERTAS_AL_CELULAR = "SELECT COUNT(1) FROM mensaje WHERE tipo = ? AND clave LIKE ?";

	@Autowired
	private LlamadasControl llamadas;

	@Autowired
	private AlertasPanel alertasPanel;

	@Autowired
	private PanelPromotoria panel;

	@Autowired
	private AvisosPromotoria avisos;

	@Autowired
	private ResumenDiarioTarea resumen;

	@Autowired
	private DespachoMensajes despacho;

	@Autowired
	private ServicioCierreCaja cierres;

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
		promotora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora", UsuariosDePrueba.CLAVE, false, Rol.PROMOTOR);
		directora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "directora", UsuariosDePrueba.CLAVE, false, Rol.DIRECTOR);
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

	private long alertasAlCelular(String clave) {
		return jdbc.queryForObject(ALERTAS_AL_CELULAR, Long.class, "ALERTA_PROMOTORIA", clave);
	}

	// S6-M2. Dirección registra la llamada de control antes que Promotoría y la anula sin dejar ninguna alerta.

	/**
	 * Lunes 00:05, antes de que llame la promotora: la directora, de acuerdo con la cajera, abre /panel/llamadas y
	 * registra «No contesta» o «Confirma» en TODAS las familias de la muestra sin llamar. La muestra queda completa, la
	 * promotora ya no puede registrar nada, el panel dice que no falta ninguna, el sábado no hay recordatorio y no sale
	 * ninguna alerta al celular: el único control contra el efectivo no registrado quedó anulado.
	 */
	@Test
	void direccionCierraLaMuestraConNoContestaYPromotoriaYaNoPuedeLlamar() {
		a(LUNES, 0, 5);
		UsuariosDePrueba.iniciarSesion(directora);
		LlamadasSemana muestra = llamadas.deEstaSemana();
		assertThat(muestra.familias()).isNotEmpty();
		for (LlamadasSemana.Familia f : muestra.familias()) {
			llamadas.registrar(f.familiaId(), new LlamadaRequest(ResultadoLlamada.NO_CONTESTA, null));
		}

		a(LUNES, 9, 0);
		UsuariosDePrueba.iniciarSesion(promotora);
		LlamadasSemana vistaPromotora = llamadas.deEstaSemana();
		assertThat(vistaPromotora.faltan()).as("la promotora ve la semana ya hecha").isZero();
		assertThat(vistaPromotora.familias()).noneMatch(LlamadasSemana.Familia::pendiente);
		Long primera = muestra.familias().getFirst().familiaId();
		LlamadaRequest real = new LlamadaRequest(ResultadoLlamada.NO_CONFIRMA, "Dice que pagó S/ 450.00 en efectivo y no aparece");
		assertThatThrownBy(() -> llamadas.registrar(primera, real)).isInstanceOf(ReglaNegocioException.class);
		assertThat(panel.ver().llamadas().faltan()).isZero();

		a(LUNES.plusDays(5), 10, 0);
		assertThat(alertasPanel.alertas()).as("sábado: ni recordatorio ni nada CRÍTICO")
				.noneMatch(x -> x.texto().contains("llamadas de control"))
				.noneMatch(x -> x.aviso() != null && x.aviso().tipo() == TipoAviso.LLAMADA_NO_CONFIRMA);
		SecurityContextHolder.clearContext();
		avisos.enColegio(1L, LUNES.plusDays(5));
		assertThat(alertasAlCelular("ALERTA:LLAMADA%")).isZero();
		String registradas = "SELECT COUNT(1) FROM evento_auditoria WHERE accion = ? AND nombre_usuario = ?";
		Long eventos = jdbc.queryForObject(registradas, Long.class, "LLAMADA_CONTROL_REGISTRADA", "directora");
		assertThat(eventos).as("solo eventos SIN resaltar").isEqualTo(muestra.familias().size());
	}

	// S6-A2. La muestra de la llamada de control solo ve familias con un pago en efectivo REGISTRADO.

	/**
	 * El 15/04 la familia Flores pagó en efectivo la matrícula de Sebastián, S/ 350, y la cajera se quedó con el dinero sin
	 * registrar nada: el fraude original. La familia tiene un solo apoderado y no usa el portal, justo el caso que la
	 * llamada de control debía cubrir según P17. Pero la muestra se arma SOLO con familias que tienen un pago en efectivo
	 * registrado: Flores no entra en ninguna de las 8 semanas siguientes, con ninguna semilla, y registrar su llamada da
	 * 404. Solo aparece como morosa, mezclada con las demás.
	 */
	@Test
	void laFamiliaCuyoEfectivoNoSeRegistroNuncaEntraALaMuestra() {
		Long flores = datos.f().flores();
		for (int semana = 0; semana < 8; semana++) {
			LocalDate lunes = LUNES.plusWeeks(semana);
			a(lunes.plusDays(1), 10, 0);
			UsuariosDePrueba.iniciarSesion(promotora);
			assertThat(llamadas.deEstaSemana().familias()).as("semana del " + lunes).extracting(LlamadasSemana.Familia::familiaId).doesNotContain(flores);
		}
		a(LUNES.plusDays(1), 10, 0);
		UsuariosDePrueba.iniciarSesion(promotora);
		LlamadaRequest real = new LlamadaRequest(ResultadoLlamada.NO_CONFIRMA, "Dice que pagó S/ 350.00 en efectivo el 15/04");
		assertThatThrownBy(() -> llamadas.registrar(flores, real)).isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(panel.ver().deuda().familias()).as("solo se ve como una morosa más").isGreaterThanOrEqualTo(1);
	}

	// S6-M3. Un cierre con faltante aprobado antes de la siguiente pasada nunca llega al celular de Promotoría.

	/**
	 * La cajera no cierra a la hora límite, y sale el aviso de caja no cerrada, que parece un olvido. Cierra a las 20:50,
	 * después de la última pasada de las 20:45 y del resumen de las 19:30, con un faltante, y Dirección lo aprueba a las
	 * 21:10 desde el celular. Como la alerta CRÍTICA solo existe mientras el cierre está POR_REVISAR, la pasada de las
	 * 07:00 ya no la encuentra: el faltante no sale nunca al celular ni en ningún resumen.
	 */
	@Test
	void cierreConFaltanteAprobadoDeNocheNoLlegaNuncaAlCelular() {
		a(JUEVES, 19, 30);
		resumen.enColegio(1L, JUEVES);
		String sqlFoto = "SELECT cierres_con_diferencia FROM resumen_diario WHERE fecha = ?";
		assertThat(jdbc.queryForObject(sqlFoto, Integer.class, JUEVES)).as("a las 19:30 la caja seguía abierta").isZero();

		a(JUEVES, 20, 50);
		como(EscenarioCaja.CAJA);
		cierres.contar(new ConteoRequest(new BigDecimal("1.00"), null));
		cierres.recontar(new ReconteoRequest(new BigDecimal("1.00"), null, "Me faltó el efectivo de la tarde"));
		Long cierre = jdbc.queryForObject("SELECT MAX(id) FROM cierre_caja", Long.class);
		String sqlDiferencia = "SELECT diferencia FROM cierre_caja WHERE id = ?";
		assertThat(jdbc.queryForObject(sqlDiferencia, BigDecimal.class, cierre)).isNegative();

		a(JUEVES, 21, 10);
		String comentario = "Conversé con la cajera, lo repone el lunes";
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "cierre_caja", cierre, comentario);
		SecurityContextHolder.clearContext();

		LocalDate viernes = JUEVES.plusDays(1);
		a(viernes, 7, 0);
		avisos.enColegio(1L, viernes);
		a(viernes, 19, 30);
		avisos.enColegio(1L, viernes);
		assertThat(alertasAlCelular("ALERTA:CIERRE_CON_DIFERENCIA:%")).as("el faltante nunca sale al celular").isZero();
		como(EscenarioCobranza.PROMOTORIA);
		assertThat(alertasPanel.alertas()).noneMatch(x -> x.aviso() != null && x.aviso().tipo() == TipoAviso.CIERRE_CON_DIFERENCIA);
	}

	// S6-B1. El aviso de que el resumen del sábado no salió nunca llega al celular.

	/**
	 * Con resúmenes anteriores del jueves y el viernes, el del sábado 17/04 no sale. El sábado la alerta aparece recién a
	 * las 21:00, después de la última pasada de las 20:45; el domingo no se envía nada; y el lunes «ayer» es el domingo,
	 * que no debía tener resumen: la alerta del sábado desaparece del panel sin haber llegado nunca al celular.
	 */
	@Test
	void elResumenDelSabadoQueNoSalioNuncaSeAvisa() {
		LocalDate viernes = JUEVES.plusDays(1);
		LocalDate sabado = JUEVES.plusDays(2);
		LocalDate domingo = JUEVES.plusDays(3);
		for (LocalDate dia : List.of(JUEVES, viernes)) {
			a(dia, 19, 30);
			resumen.enColegio(1L, dia);
			despacho.despacharColegio(1L);
		}
		a(sabado, 20, 45);
		avisos.enColegio(1L, sabado);
		a(domingo, 9, 0);
		como(EscenarioCobranza.PROMOTORIA);
		String referencia = "RS:" + sabado;
		assertThat(alertasPanel.alertas()).as("el domingo SÍ está en el panel").anyMatch(x -> x.aviso() != null && x.aviso().referencia().equals(referencia));
		SecurityContextHolder.clearContext();
		avisos.enColegio(1L, domingo);
		a(LUNES, 7, 0);
		avisos.enColegio(1L, LUNES);
		como(EscenarioCobranza.PROMOTORIA);
		assertThat(alertasPanel.alertas()).as("el lunes ya no").noneMatch(x -> x.aviso() != null && x.aviso().tipo() == TipoAviso.RESUMEN_NO_SALIO);
		assertThat(alertasAlCelular("ALERTA:RESUMEN_NO_SALIO:%")).as("nunca llegó al celular").isZero();
	}

	/** Control de S6-M3: si la pasada llega ANTES de la aprobación, el mismo faltante sí sale al celular. */
	@Test
	void controlElMismoFaltanteSinAprobarSiSaleAlCelular() {
		a(JUEVES, 20, 40);
		como(EscenarioCaja.CAJA);
		cierres.contar(new ConteoRequest(new BigDecimal("1.00"), null));
		cierres.recontar(new ReconteoRequest(new BigDecimal("1.00"), null, "Me faltó el efectivo de la tarde"));
		SecurityContextHolder.clearContext();
		a(JUEVES, 20, 45);
		avisos.enColegio(1L, JUEVES);
		assertThat(alertasAlCelular("ALERTA:CIERRE_CON_DIFERENCIA:%")).isEqualTo(1);
	}
}
