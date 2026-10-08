package pe.edu.virgenmaria.cuentasclaras.panel.proceso;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioAlumnos;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.proceso.HuellaDiaria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioAnulacionPagos;
import pe.edu.virgenmaria.cuentasclaras.caja.service.ServicioCobro;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioDescuentos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.ServicioPlanesPension;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
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
import pe.edu.virgenmaria.cuentasclaras.panel.dto.CifrasResumen;
import pe.edu.virgenmaria.cuentasclaras.panel.dto.VistaPanel;
import pe.edu.virgenmaria.cuentasclaras.panel.model.ResumenDiario;
import pe.edu.virgenmaria.cuentasclaras.panel.service.AlertasPanel;
import pe.edu.virgenmaria.cuentasclaras.panel.service.CifrasDelDia;
import pe.edu.virgenmaria.cuentasclaras.panel.service.PanelPromotoria;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;
import pe.edu.virgenmaria.cuentasclaras.seguridad.repository.UsuarioRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza.como;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Sprint 6, tanda 2: el resumen diario de las 19:30 con su foto (P3, P5, P7, P18), el recálculo de los días ya
 * informados (P4) y las alertas del panel. Sobre {@link EscenarioPanel}: el jueves 15/04/2027 se cobraron S/ 800.00
 * vigentes (efectivo S/ 350.00 y Yape S/ 450.00), se anuló un pago de S/ 350.00 y quedan S/ 1,600.00 vencidos de 2
 * familias; la caja de la cajera sigue abierta.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ResumenDiarioTest {

	private static final LocalDate JUEVES = LocalDate.of(2027, 4, 15);

	@Autowired
	private ResumenDiarioTarea tarea;

	@Autowired
	private RecalculoResumenes recalculo;

	@Autowired
	private HuellaDiaria huella;

	@Autowired
	private DespachoMensajes despacho;

	@Autowired
	private CifrasDelDia cifras;

	@Autowired
	private AlertasPanel alertas;

	@Autowired
	private PanelPromotoria panel;

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
	private AuditoriaService auditoria;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private JdbcTemplate jdbc;

	private EscenarioPanel.Datos datos;

	private Usuario promotora;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		datos = EscenarioPanel.preparar(estructura, alumnos, planes, cobro, anulaciones, descuentos, bandeja, reloj, jdbc);
		promotora = UsuariosDePrueba.guardar(usuarios, codificador, 1L, "promotora", UsuariosDePrueba.CLAVE, false,
				Rol.PROMOTOR);
		UsuariosDePrueba.guardar(usuarios, codificador, 1L, "cajera.lucia", UsuariosDePrueba.CLAVE, false, Rol.CAJA);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		reloj.fijar(ConfiguracionRelojAjustable.INICIO);
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	private void a(int hora, int minuto) {
		a(JUEVES, hora, minuto);
	}

	private void a(LocalDate dia, int hora, int minuto) {
		reloj.fijar(dia.atTime(hora, minuto).atZone(reloj.getZone()).toInstant());
	}

	private Optional<ResumenDiario> resumenDe(LocalDate dia) {
		return tarea.enColegio(1L, dia);
	}

	private Map<String, Object> foto() {
		return jdbc.queryForMap("SELECT * FROM resumen_diario WHERE fecha = ?", JUEVES);
	}

	/**
	 * P3 y P18: la foto lleva EXACTAMENTE las cifras de los libros (las mismas del panel) y la huella de las 19:00; sale a
	 * cada persona de Promotoría activa (nunca a la cajera) con solo cifras y queda en la bitácora.
	 */
	@Test
	void elResumenGuardaLaFotoComprobadaYLaEnviaAPromotoria() {
		a(19, 0);
		huella.horaEnColegio(1L, JUEVES.atTime(19, 0));
		Map<String, Object> deLaHora = jdbc.queryForMap("SELECT secuencia, codigo FROM huella_hora");
		a(19, 30);

		assertThat(resumenDe(JUEVES)).isPresent();

		Map<String, Object> f = foto();
		assertThat(f).containsEntry("creado_por", "sistema.panel").containsEntry("pagos_cantidad", 2)
				.containsEntry("pagos_efectivo", 1).containsEntry("familias_morosas", 2).containsEntry("cajas_sin_cerrar", 1)
				.containsEntry("huella_secuencia", deLaHora.get("secuencia"))
				.containsEntry("huella_codigo", deLaHora.get("codigo"));
		assertThat((BigDecimal) f.get("cobrado_total")).isEqualByComparingTo("800.00");
		assertThat((BigDecimal) f.get("cobrado_efectivo")).isEqualByComparingTo("350.00");
		assertThat((BigDecimal) f.get("cobrado_mes")).isEqualByComparingTo("800.00");
		assertThat((BigDecimal) f.get("deuda_vencida")).isEqualByComparingTo("1600.00");
		// Las mismas sumas, directo de las tablas (en MySQL las compara el trigger).
		assertThat((BigDecimal) f.get("cobrado_total")).isEqualByComparingTo(jdbc.queryForObject(
				"SELECT SUM(total) FROM pago WHERE estado = 'VIGENTE' AND fecha = ?", BigDecimal.class, JUEVES));
		assertThat((BigDecimal) f.get("deuda_vencida")).isEqualByComparingTo(jdbc.queryForObject(
				"SELECT SUM(monto - monto_pagado - monto_descuento) FROM cuota WHERE estado IN ('PENDIENTE', 'PARCIAL') "
						+ "AND fecha_vencimiento < ?", BigDecimal.class, JUEVES));

		List<Map<String, Object>> mensajes = jdbc.queryForList("SELECT usuario_id, canal, destino, parametros, "
				+ "creado_por, entidad, entidad_id FROM mensaje WHERE tipo = 'RESUMEN_DIARIO'");
		assertThat(mensajes).singleElement().satisfies(m -> {
			assertThat(m).containsEntry("usuario_id", promotora.getId()).containsEntry("canal", "WHATSAPP")
					.containsEntry("destino", promotora.getTelefonoWhatsapp()).containsEntry("creado_por", "sistema.panel")
					.containsEntry("entidad", "resumen_diario").containsEntry("entidad_id", f.get("id"));
			assertThat((String) m.get("parametros")).contains("15/04/2027", "S/ 800.00", "S/ 350.00",
					"1 sin cerrar", "S/ 1,600.00 de 2 familia(s)", "evento " + deLaHora.get("secuencia"),
					(String) deLaHora.get("codigo"), "(19:00)")
					// Solo cifras: ni familias ni cajeras.
					.doesNotContain("Quispe", "Flores", "caja", "Lucía");
		});
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'RESUMEN_DIARIO_GUARDADO' "
				+ "AND entidad_id = ?", Long.class, f.get("id").toString())).isEqualTo(1);
	}

	/** P3: el panel y el resumen dicen lo mismo (los dos usan CifrasDelDia). */
	@Test
	void panelYResumenDicenLoMismo() {
		a(19, 30);
		resumenDe(JUEVES);
		como(EscenarioCobranza.PROMOTORIA);
		CifrasResumen c = cifras.calcular(JUEVES);
		Map<String, Object> f = foto();
		assertThat((BigDecimal) f.get("cobrado_total")).isEqualByComparingTo(c.dia().total());
		assertThat(((Number) f.get("pagos_cantidad")).longValue()).isEqualTo(c.dia().cantidad());
		assertThat((BigDecimal) f.get("cobrado_efectivo")).isEqualByComparingTo(c.dia().efectivo());
		assertThat((BigDecimal) f.get("cobrado_mes")).isEqualByComparingTo(c.mes().total());
		assertThat((BigDecimal) f.get("deuda_vencida")).isEqualByComparingTo(c.deuda().monto());
		assertThat(((Number) f.get("familias_morosas")).longValue()).isEqualTo(c.deuda().familias());
		VistaPanel p = panel.ver();
		assertThat(p.hoy().cobrado()).isEqualTo("S/ 800.00");
		assertThat(p.resumen().estado()).startsWith("Pendiente");
	}

	/** Idempotente: la segunda corrida del mismo día no crea otra foto ni otro mensaje. */
	@Test
	void esIdempotente() {
		a(19, 30);
		assertThat(resumenDe(JUEVES)).isPresent();
		assertThat(resumenDe(JUEVES)).isEmpty();
		assertThat(contar(jdbc, "resumen_diario")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE tipo = 'RESUMEN_DIARIO'", Long.class))
				.isEqualTo(1);
	}

	/** Decisión 69: si el DBA dejó el correo del contador, también le llega (EXTERNO, solo con su fila). */
	@Test
	void alCorreoExternoSoloSiElDbaLoConfiguro() {
		jdbc.update("INSERT INTO configuracion_bd (clave, valor, creado_en) VALUES ('resumen_correo_externo', "
				+ "'contador@estudio.pe', CURRENT_TIMESTAMP)");
		a(19, 30);
		resumenDe(JUEVES);
		assertThat(jdbc.queryForList("SELECT destinatario_tipo, canal, destino FROM mensaje WHERE tipo = 'RESUMEN_DIARIO' "
				+ "ORDER BY id")).extracting(m -> m.get("destinatario_tipo") + ":" + m.get("destino"))
				.containsExactly("USUARIO:" + promotora.getTelefonoWhatsapp(), "EXTERNO:contador@estudio.pe");
	}

	/** Sin mensaje no hay foto: si nadie de Promotoría puede recibirlo, no queda nada y la bitácora lo resalta. */
	@Test
	void sinPromotoriaQueLoRecibaNoHayFotoYQuedaResaltado() {
		jdbc.update("UPDATE usuario SET activo = FALSE WHERE id = ?", promotora.getId());
		a(19, 30);
		assertThatThrownBy(() -> resumenDe(JUEVES)).isInstanceOf(IllegalStateException.class);
		assertThat(contar(jdbc, "resumen_diario")).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensaje WHERE tipo = 'RESUMEN_DIARIO'", Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'RESUMEN_DIARIO_NO_SALIO'",
				Long.class)).isEqualTo(1);
	}

	/** Decisión 68: el domingo sin cobros no hay resumen. */
	@Test
	void domingoSinCobrosNoHayResumen() {
		LocalDate domingo = LocalDate.of(2027, 4, 18);
		a(domingo, 19, 30);
		assertThat(resumenDe(domingo)).isEmpty();
		assertThat(contar(jdbc, "resumen_diario")).isZero();
	}

	/** P4: un pago borrado por SQL después del envío es CRÍTICA (con aviso al celular) y queda resaltado una sola vez. */
	@Test
	void unPagoBorradoPorSqlEsCritico() {
		a(19, 30);
		Long resumen = resumenDe(JUEVES).orElseThrow().getId();
		jdbc.update("DELETE FROM aplicacion_pago WHERE pago_id = ?", datos.pagoYape());
		jdbc.update("DELETE FROM pago WHERE id = ?", datos.pagoYape());
		a(JUEVES.plusDays(1), 6, 15);

		assertThat(recalculo.enColegio(1L)).isEqualTo(1);
		assertThat(recalculo.enColegio(1L)).as("no repite el mismo hallazgo").isZero();
		Map<String, Object> evento = jdbc.queryForMap("SELECT valor_anterior, valor_nuevo, detalle FROM evento_auditoria "
				+ "WHERE accion = 'RESUMEN_DIARIO_CAMBIO'");
		assertThat((String) evento.get("valor_anterior")).contains("S/ 800.00 en 2 pago(s)");
		assertThat((String) evento.get("valor_nuevo")).contains("S/ 350.00 en 1 pago(s)");
		assertThat((String) evento.get("detalle")).contains("sin explicación");
		como(EscenarioCobranza.PROMOTORIA);
		assertThat(alertas.alertas()).filteredOn(a -> a.gravedad() == AlertaRevision.Gravedad.CRITICA)
				.filteredOn(a -> a.aviso() != null && a.aviso().tipo() == TipoAviso.CIFRAS_CAMBIARON).singleElement()
				.satisfies(a -> assertThat(a.aviso().referencia()).isEqualTo("RD:" + resumen));
		assertThat(panel.ver().resumen().cambiosSinExplicar()).isEqualTo(1);
	}

	/** P4: una anulación APROBADA después del envío explica la diferencia: solo PARA SABER. */
	@Test
	void unaAnulacionAprobadaSeExplica() {
		a(19, 30);
		resumenDe(JUEVES);
		a(19, 40);
		como(EscenarioCaja.CAJA);
		anulaciones.solicitarDevolucion(datos.pagoEfectivo(), EscenarioAprobaciones.MOTIVO_ANULACION);
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "pago", datos.pagoEfectivo());
		SecurityContextHolder.clearContext();
		a(JUEVES.plusDays(1), 6, 15);

		assertThat(recalculo.enColegio(1L)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'RESUMEN_DIARIO_CAMBIO'",
				Long.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT detalle FROM evento_auditoria WHERE accion = "
				+ "'RESUMEN_DIARIO_CAMBIO_EXPLICADO'", String.class)).contains("se anularon 1 por S/ 350.00");
		como(EscenarioCobranza.PROMOTORIA);
		assertThat(alertas.alertas()).noneMatch(a -> a.aviso() != null && a.aviso().tipo() == TipoAviso.CIFRAS_CAMBIARON)
				.anyMatch(a -> a.gravedad() == AlertaRevision.Gravedad.INFORMATIVA && a.texto().contains("15/04/2027"));
	}

	/** P4: un pago registrado después del corte del mismo día (llegó tarde) también se explica. */
	@Test
	void unPagoTardioSeExplica() {
		a(19, 30);
		resumenDe(JUEVES);
		a(19, 45);
		como(EscenarioCaja.CAJA);
		cobro.cobrar(EscenarioCaja.efectivo(datos.f().flores(), List.of(EscenarioCaja.cuota(jdbc, datos.f().sebastian(),
				"MAT-2027")), "350.00", "350.00"));
		SecurityContextHolder.clearContext();
		a(JUEVES.plusDays(1), 6, 15);
		assertThat(recalculo.enColegio(1L)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT detalle FROM evento_auditoria WHERE accion = "
				+ "'RESUMEN_DIARIO_CAMBIO_EXPLICADO'", String.class)).contains("se registraron 1 pago(s) por S/ 350.00");
	}

	/**
	 * P5: con resúmenes anteriores, si a las 21:00 el de hoy no salió a Promotoría es CRÍTICA (y al día siguiente sigue,
	 * para que el aviso salga a las 07:00). Antes de la hora, nada; si salió (enviado), nada.
	 */
	@Test
	void sinResumenALas21EsCritico() {
		LocalDate miercoles = JUEVES.minusDays(1);
		a(miercoles, 19, 30);
		resumenDe(miercoles);
		despacho.despacharColegio(1L);
		a(20, 0);
		como(EscenarioCobranza.PROMOTORIA);
		assertThat(alertas.alertas()).noneMatch(a -> a.aviso() != null && a.aviso().tipo() == TipoAviso.RESUMEN_NO_SALIO);
		a(21, 5);
		assertThat(alertas.alertas()).filteredOn(a -> a.aviso() != null && a.aviso().tipo() == TipoAviso.RESUMEN_NO_SALIO)
				.singleElement().satisfies(a -> {
					assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.CRITICA);
					assertThat(a.aviso().referencia()).isEqualTo("RS:" + JUEVES);
				});
		// El de hoy sale (tarde) y se envía: ya no hay alerta.
		SecurityContextHolder.clearContext();
		resumenDe(JUEVES);
		despacho.despacharColegio(1L);
		como(EscenarioCobranza.PROMOTORIA);
		assertThat(alertas.alertas()).noneMatch(a -> a.aviso() != null && a.aviso().tipo() == TipoAviso.RESUMEN_NO_SALIO);
		assertThat(panel.ver().resumen().estado()).startsWith("Enviado ").contains("Enviado");
	}

	/** Sin ningún resumen todavía (colegio nuevo), la alerta no se activa: no hay nada que suprimir. */
	@Test
	void sinResumenesAnterioresNoHayAlerta() {
		a(21, 30);
		como(EscenarioCobranza.PROMOTORIA);
		assertThat(alertas.alertas()).noneMatch(a -> a.aviso() != null && a.aviso().tipo() == TipoAviso.RESUMEN_NO_SALIO);
	}

	/** Más de 5 descargas de Excel de una persona en el día: ATENCIÓN; y las de hoy, PARA SABER. */
	@Test
	void muchasDescargasDeExcelEsAtencion() {
		a(10, 0);
		ContextoColegio.en(1L, () -> new TransactionTemplate(transacciones).executeWithoutResult(t -> {
			for (int i = 0; i < 6; i++) {
				auditoria.registrar(auditoria.actorPara(1L, null, "administracion", "ADMINISTRACION"),
						AccionAuditoria.REPORTE_EXPORTADO, "reporte", "codigo-" + i, null, null, "tipo=INGRESOS");
			}
		}));
		a(11, 0);
		como(EscenarioCobranza.PROMOTORIA);
		assertThat(alertas.alertas()).anyMatch(a -> a.gravedad() == AlertaRevision.Gravedad.ATENCION
				&& a.texto().contains("administracion descargó 6 reportes"))
				.anyMatch(a -> a.gravedad() == AlertaRevision.Gravedad.INFORMATIVA
						&& a.texto().contains("Descargas de Excel hoy: administracion (6)"));
	}
}
