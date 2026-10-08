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
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;
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
 * Auditoría de seguridad y antifraude del sprint 6, rama auditoria/sprint-6. Cada prueba reproducía un ataque y quedaba en
 * verde mientras el hallazgo seguía abierto. Correcciones del sprint 6: sus aserciones se INVIRTIERON y ahora dicen
 * «CORREGIDO»: cada prueba en verde significa que el ataque ya no funciona.
 * Sobre EscenarioPanel: el jueves 15/04/2027 la familia Quispe pagó en efectivo y por Yape; la familia Flores, de
 * Sebastián, tiene la matrícula y marzo vencidos y ningún pago registrado; la caja de la cajera sigue abierta.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class AuditoriaSprint6Test {

	private static final LocalDate JUEVES = LocalDate.of(2027, 4, 15);

	private static final LocalDate LUNES = LocalDate.of(2027, 4, 19);

	private static final String ALERTAS_AL_CELULAR = "SELECT COUNT(1) FROM mensaje WHERE tipo = ? AND clave LIKE ?";

	private static final String NOTA = "Dice que pagó S/ 450.00 en efectivo y no aparece";

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

	// S6-M2. Dirección registraba la llamada de control antes que Promotoría y la anulaba sin dejar ninguna alerta.

	/**
	 * CORREGIDO. Lunes 00:05, antes de que llame la promotora: la directora, de acuerdo con la cajera, abre /panel/llamadas
	 * e intenta registrar «No contesta» en TODAS las familias de la muestra sin llamar. Sin la semana delegada por
	 * Promotoría no queda nada; la promotora registra su «No confirma». Cuando Promotoría delega la semana, cada llamada
	 * de Dirección sale al celular de Promotoría, y un «No contesta» no cierra la plaza: hay que volver a llamar, y al
	 * segundo sale un aviso de ATENCIÓN. Los resultados van en el resumen de las 19:30.
	 */
	@Test
	void direccionCierraLaMuestraConNoContestaYPromotoriaYaNoPuedeLlamar() {
		a(LUNES, 0, 5);
		UsuariosDePrueba.iniciarSesion(directora);
		LlamadasSemana muestra = llamadas.deEstaSemana();
		assertThat(muestra.familias()).hasSize(2);
		assertThat(muestra.puedeRegistrar()).as("Dirección solo mira").isFalse();
		for (LlamadasSemana.Familia f : muestra.familias()) {
			assertThatThrownBy(() -> llamadas.registrar(f.familiaId(), new LlamadaRequest(ResultadoLlamada.NO_CONTESTA,
					null))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Promotoría se las delega");
		}
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM llamada_control", Long.class)).as("CORREGIDO").isZero();

		a(LUNES, 9, 0);
		UsuariosDePrueba.iniciarSesion(promotora);
		LlamadasSemana vistaPromotora = llamadas.deEstaSemana();
		assertThat(vistaPromotora.faltan()).as("la promotora ve la semana entera por hacer").isEqualTo(2);
		Long primera = muestra.familias().getFirst().familiaId();
		Long segunda = muestra.familias().get(1).familiaId();
		assertThat(llamadas.registrar(primera, new LlamadaRequest(ResultadoLlamada.NO_CONFIRMA, NOTA))).isNotNull();
		llamadas.delegarADireccion();

		a(LUNES, 9, 30);
		UsuariosDePrueba.iniciarSesion(directora);
		llamadas.registrar(segunda, new LlamadaRequest(ResultadoLlamada.NO_CONTESTA, null));
		assertThat(llamadas.deEstaSemana().familias()).filteredOn(f -> f.familiaId().equals(segunda)).singleElement()
				.satisfies(f -> assertThat(f.pendiente()).as("«No contesta» no cierra la plaza").isTrue())
				.satisfies(f -> assertThat(f.reintento()).isTrue());
		assertThatThrownBy(() -> llamadas.registrar(segunda, new LlamadaRequest(ResultadoLlamada.NO_CONTESTA, null)))
				.as("el segundo intento, una hora después").isInstanceOf(ReglaNegocioException.class);
		a(LUNES, 10, 45);
		llamadas.registrar(segunda, new LlamadaRequest(ResultadoLlamada.NO_CONTESTA, null));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM llamada_control WHERE por_delegacion", Long.class))
				.isEqualTo(2);

		SecurityContextHolder.clearContext();
		a(LUNES, 11, 0);
		avisos.enColegio(1L, LUNES);
		assertThat(jdbc.queryForList("SELECT usuario_id FROM mensaje WHERE tipo = 'ALERTA_PROMOTORIA' AND clave LIKE "
				+ "'ALERTA:LLAMADA_POR_DIRECCION:%'", Long.class)).as("cada llamada de Dirección, a Promotoría")
				.hasSize(2).containsOnly(promotora.getId());
		assertThat(alertasAlCelular("ALERTA:LLAMADA_REEMPLAZADA:%")).as("dos «No contesta»: ATENCIÓN al celular")
				.isEqualTo(1);
		assertThat(alertasAlCelular("ALERTA:LLAMADA_NO_CONFIRMA:%")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'LLAMADAS_DELEGADAS' AND "
				+ "nombre_usuario = 'promotora'", Long.class)).isEqualTo(1);

		a(LUNES, 19, 30);
		resumen.enColegio(1L, LUNES);
		String texto = jdbc.queryForObject("SELECT parametros FROM resumen_diario WHERE fecha = ?", String.class, LUNES);
		assertThat(texto.split("\n")).hasSize(12);
		assertThat(texto.split("\n")[11]).as("los resultados de las llamadas van en el resumen")
				.isEqualTo("2 de 2 hechas: 0 confirma, 1 no confirma, 2 no contesta, 0 reemplazada(s)");
	}

	// S6-A2. La muestra de la llamada de control solo veía familias con un pago en efectivo REGISTRADO.

	/**
	 * CORREGIDO. El 15/04 la familia Flores pagó en efectivo la matrícula de Sebastián, S/ 350, y la cajera se quedó con
	 * el dinero sin registrar nada: el fraude original. La familia tiene un solo apoderado y no usa el portal. Su deuda
	 * vencida la hace candidata (con una plaza reservada para la deuda): entra a la muestra cada semana y su «No
	 * confirma» queda registrado y es CRÍTICA.
	 */
	@Test
	void laFamiliaCuyoEfectivoNoSeRegistroNuncaEntraALaMuestra() {
		Long flores = datos.f().flores();
		for (int semana = 0; semana < 8; semana++) {
			LocalDate lunes = LUNES.plusWeeks(semana);
			a(lunes.plusDays(1), 10, 0);
			UsuariosDePrueba.iniciarSesion(promotora);
			assertThat(llamadas.deEstaSemana().familias()).as("semana del " + lunes)
					.filteredOn(f -> f.familiaId().equals(flores)).singleElement()
					.satisfies(f -> assertThat(f.motivo()).contains("deuda vencida"));
		}
		a(LUNES.plusDays(1), 10, 0);
		UsuariosDePrueba.iniciarSesion(promotora);
		LlamadaRequest real = new LlamadaRequest(ResultadoLlamada.NO_CONFIRMA, "Dice que pagó S/ 350.00 en efectivo el 15/04");
		Long id = llamadas.registrar(flores, real);
		assertThat(id).as("CORREGIDO").isNotNull();
		assertThat(alertasPanel.alertas()).anyMatch(x -> x.gravedad() == AlertaRevision.Gravedad.CRITICA
				&& x.aviso() != null && x.aviso().tipo() == TipoAviso.LLAMADA_NO_CONFIRMA
				&& x.aviso().referencia().equals("LC:" + id));
	}

	// S6-M3. Un cierre con faltante aprobado antes de la siguiente pasada nunca llegaba al celular de Promotoría.

	/**
	 * CORREGIDO. La cajera cierra a las 20:50 (después de la última pasada y del resumen de las 19:30) con un faltante, y
	 * Dirección lo aprueba a las 21:10. El aviso sale apenas se confirma el cierre (oyente después del commit, con la clave
	 * C:id), una sola vez aunque después corran las pasadas, y el resumen del viernes informa el cierre con diferencia
	 * ocurrido desde el resumen anterior.
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
		SecurityContextHolder.clearContext();
		Long cierre = jdbc.queryForObject("SELECT MAX(id) FROM cierre_caja", Long.class);
		String sqlDiferencia = "SELECT diferencia FROM cierre_caja WHERE id = ?";
		assertThat(jdbc.queryForObject(sqlDiferencia, BigDecimal.class, cierre)).isNegative();
		assertThat(alertasAlCelular("ALERTA:CIERRE_CON_DIFERENCIA:C:" + cierre + ":U" + promotora.getId() + ":%"))
				.as("CORREGIDO: sale apenas se cierra").isEqualTo(1);

		a(JUEVES, 21, 10);
		String comentario = "Conversé con la cajera, lo repone el lunes";
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "cierre_caja", cierre, comentario);
		SecurityContextHolder.clearContext();

		LocalDate viernes = JUEVES.plusDays(1);
		a(viernes, 7, 0);
		avisos.enColegio(1L, viernes);
		a(viernes, 19, 30);
		avisos.enColegio(1L, viernes);
		assertThat(alertasAlCelular("ALERTA:CIERRE_CON_DIFERENCIA:%")).as("una sola vez").isEqualTo(1);
		resumen.enColegio(1L, viernes);
		String cajas = jdbc.queryForObject("SELECT parametros FROM resumen_diario WHERE fecha = ?", String.class, viernes)
				.split("\n")[5];
		assertThat(cajas).as("el resumen del viernes lo informa").contains("desde el resumen anterior, 1 cierre(s) con "
				+ "diferencia (-S/ ");
	}

	// S6-B1. El aviso de que el resumen del sábado no salió nunca llegaba al celular.

	/**
	 * CORREGIDO. Con resúmenes anteriores del jueves y el viernes, el del sábado 17/04 no sale. «No salió» se revisa desde
	 * el último resumen confirmado (el del viernes): el lunes a las 07:00 la alerta del sábado sigue en el panel y sale al
	 * celular.
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
		assertThat(alertasPanel.alertas()).as("CORREGIDO: el lunes sigue").anyMatch(x -> x.aviso() != null
				&& x.aviso().referencia().equals(referencia));
		SecurityContextHolder.clearContext();
		assertThat(alertasAlCelular("ALERTA:RESUMEN_NO_SALIO:RS:" + sabado + ":%")).as("CORREGIDO: llegó al celular")
				.isEqualTo(1);
	}

	/** Control de S6-M3: si la pasada llega ANTES de la aprobación, el mismo faltante sale una sola vez al celular. */
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

	// S6-M1 (parte de la aplicación; la de la base está en AuditoriaSprint6MySqlTest).

	/**
	 * CORREGIDO. Alguien con acceso a la base planta la foto del jueves (en H2 no hay triggers: aquí se prueba lo que hace
	 * la aplicación). A las 19:30 sistema.panel la encuentra sin su RESUMEN_DIARIO_GUARDADO: deja RESUMEN_DIARIO_SUPLANTADO
	 * resaltado, la alerta es CRÍTICA (con aviso al celular) y «el resumen no salió» también salta, aunque haya un mensaje
	 * plantado ENVIADO.
	 */
	@Test
	void unaFotoPlantadaPorSqlEsCriticaYNoCuentaComoEnviada() {
		a(JUEVES, 19, 0);
		jdbc.update("INSERT INTO resumen_diario (colegio_id, fecha, cortado_en, cobrado_total, pagos_cantidad, "
				+ "cobrado_efectivo, pagos_efectivo, cobrado_mes, deuda_vencida, familias_morosas, cajas_sin_cerrar, "
				+ "cierres_con_diferencia, solicitudes_pendientes, alertas_criticas, avisos_familias, avisos_entregados, "
				+ "parametros, creado_en, creado_por, actualizado_en, version) VALUES (1, ?, ?, 800.00, 2, 350.00, 1, 800.00, "
				+ "1600.00, 2, 0, 0, 0, 0, 0, 0, 'inventado', ?, 'sistema.panel', ?, 0)", JUEVES, JUEVES.atTime(19, 0),
				JUEVES.atTime(19, 0), JUEVES.atTime(19, 0));
		Long foto = jdbc.queryForObject("SELECT id FROM resumen_diario WHERE fecha = ?", Long.class, JUEVES);
		jdbc.update("INSERT INTO mensaje (colegio_id, clave, tipo, canal, destinatario_tipo, usuario_id, destino, plantilla, "
				+ "parametros, entidad, entidad_id, estado, intentos, proveedor, proveedor_mensaje_id, enviado_en, creado_en, "
				+ "creado_por, actualizado_en, version) VALUES (1, 'plantado', 'RESUMEN_DIARIO', 'WHATSAPP', 'USUARIO', ?, ?, "
				+ "'RESUMEN_DIARIO', 'inventado', 'resumen_diario', ?, 'ENVIADO', 1, 'SIMULADO', 'SIM-plantado', ?, ?, "
				+ "'sistema.panel', ?, 0)", promotora.getId(),
				promotora.getTelefonoWhatsapp(), foto, JUEVES.atTime(19, 1), JUEVES.atTime(19, 1), JUEVES.atTime(19, 1));

		a(JUEVES, 19, 30);
		assertThat(resumen.enColegio(1L, JUEVES)).isEmpty();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'RESUMEN_DIARIO_SUPLANTADO' "
				+ "AND entidad_id = ?", Long.class, foto.toString())).as("CORREGIDO: queda resaltado").isEqualTo(1);
		resumen.enColegio(1L, JUEVES);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'RESUMEN_DIARIO_SUPLANTADO'",
				Long.class)).as("una sola vez").isEqualTo(1);

		a(JUEVES, 21, 30);
		como(EscenarioCobranza.PROMOTORIA);
		List<AlertaRevision> criticas = alertasPanel.alertas().stream()
				.filter(x -> x.gravedad() == AlertaRevision.Gravedad.CRITICA && x.aviso() != null).toList();
		assertThat(criticas).anyMatch(x -> x.aviso().tipo() == TipoAviso.RESUMEN_SUPLANTADO
				&& x.aviso().referencia().equals("RF:" + foto));
		assertThat(criticas).as("el mensaje plantado no cuenta como enviado")
				.anyMatch(x -> x.aviso().tipo() == TipoAviso.RESUMEN_NO_SALIO && x.aviso().referencia().equals("RS:" + JUEVES));
		SecurityContextHolder.clearContext();
		a(JUEVES, 20, 45);
		avisos.enColegio(1L, JUEVES);
		assertThat(alertasAlCelular("ALERTA:RESUMEN_SUPLANTADO:RF:" + foto + ":%")).isEqualTo(1);
	}
}
