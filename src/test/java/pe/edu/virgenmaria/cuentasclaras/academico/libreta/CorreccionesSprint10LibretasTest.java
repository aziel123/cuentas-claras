package pe.edu.virgenmaria.cuentasclaras.academico.libreta;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.dto.VerificacionVista;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.ManejadorReemisionLibreta;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.NotasRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.AlertasNotas;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.service.IntegridadNotas;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.CambiarSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.service.ServicioMatriculas;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.repository.SolicitudCambioRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Correcciones del sprint 10 (docs/arquitectura/sprint-10/correcciones.md): auditoría del código (hallazgos 1, 2, 3, 4, 5,
 * 7 y 8) y pendientes del QA (P-1, P-6 y P-7) en H2. Lo que solo exige la base (la conexión del lote, la aprobación de la
 * nota tardía, la sección de la corrección de un trasladado y los niveles de la reemisión) está en
 * {@code AuditoriaSprint10CorreccionesMySqlTest}.
 */
class CorreccionesSprint10LibretasTest extends PruebaLibretas {

	@Autowired
	private IntegridadNotas integridadNotas;

	@Autowired
	private AlertasNotas alertasNotas;

	@Autowired
	private ManejadorReemisionLibreta manejadorReemision;

	@Autowired
	private SolicitudCambioRepository solicitudes;

	@Autowired
	private ServicioMatriculas servicioMatriculas;

	@Autowired
	private pe.edu.virgenmaria.cuentasclaras.academico.siagie.CalificacionesSiagie siagie;

	@Autowired
	private org.springframework.transaction.PlatformTransactionManager transacciones;

	// ------------------------------------------------------------------ hallazgo 1: integridad de las notas

	/**
	 * Una nota cambiada directo en la base (H2 no tiene triggers) ya no coincide con la huella de su guardado: Promotoría
	 * recibe la alerta CRÍTICA y las libretas de la sección no se emiten. Al volver a lo guardado, salen.
	 */
	@Test
	void unaNotaAlteradaDetieneLasLibretasDeLaSeccionYAlertaAPromotoria() {
		anaPoneA();
		cerrarPeriodo();
		Long nota = vigente(mateo, competencias("MAT").get(0));
		jdbc.update("UPDATE calificacion SET nivel_logro = 'AD' WHERE id = ?", nota);
		assertThat(emitir().total()).isZero();
		assertThat(contar("SELECT COUNT(*) FROM libreta_emitida")).isZero();
		List<AlertaRevision> alertas = como(EscenarioCobranza.PROMOTORIA, () -> integridadNotas.alertas());
		assertThat(alertas).singleElement().satisfies(a -> {
			assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.CRITICA);
			assertThat(a.aviso().tipo()).isEqualTo(TipoAviso.INTEGRIDAD_NOTAS);
			assertThat(a.texto()).contains("detenidas");
		});
		// Restaurada la nota, la huella vuelve a coincidir y salen las libretas.
		jdbc.update("UPDATE calificacion SET nivel_logro = 'A' WHERE id = ?", nota);
		assertThat(como(EscenarioCobranza.PROMOTORIA, () -> integridadNotas.alertas())).isEmpty();
		assertThat(emitir().alCierre()).isEqualTo(3);
	}

	/** Una nota de menos en un lote (o una de más) tampoco coincide: la cuenta de filas del lote es parte de la huella. */
	@Test
	void unaNotaDeMenosEnUnLoteTambienDetieneLasLibretas() {
		anaPoneA();
		cerrarPeriodo();
		jdbc.update("DELETE FROM calificacion WHERE id = ?", vigente(lucas, competencias("MAT").get(0)));
		assertThat(emitir().total()).isZero();
	}

	// ------------------------------------------------------------------ hallazgo 2: niveles guardados y reemisión

	/** Toda libreta guarda sus niveles canónicos (una línea por periodo y competencia). */
	@Test
	void laLibretaGuardaSusNiveles() {
		cerrarYEmitir();
		String niveles = jdbc.queryForObject("SELECT niveles FROM libreta_emitida WHERE id = ?", String.class,
				libreta(mateo));
		assertThat(niveles).contains("1|").contains("|A\n").doesNotContain("anual|");
	}

	/**
	 * Si la base dice hoy otros niveles que los emitidos, Promotoría ve la diferencia en la bandeja y no puede aprobar la
	 * reemisión: se corrige por solicitud.
	 */
	@Test
	void laReemisionConNivelesDistintosMuestraLaDiferenciaYNoSeAprueba() throws Exception {
		cerrarYEmitir();
		Long id = libreta(mateo);
		jdbc.update("UPDATE calificacion SET nivel_logro = 'C' WHERE id = ?", vigente(mateo, competencias("MAT").get(0)));
		mvc.perform(get("/familia/libretas/" + id + ".pdf").with(UsuariosDePrueba.como(rosaEnLinea())));
		Long solicitud = como(EscenarioCobranza.DIRECCION, () -> reemision.pedir(id, "Revisión de integridad de la libreta"));
		List<String> detalle = como(EscenarioCobranza.PROMOTORIA, () -> new org.springframework.transaction.support
				.TransactionTemplate(transacciones).execute(t -> manejadorReemision.detalle(solicitudes.findById(solicitud)
						.orElseThrow())));
		assertThat(String.join("\n", detalle)).contains("emitido A, hoy C").contains("No se puede aprobar");
		assertThatThrownBy(() -> EscenarioAprobaciones.aprueba(EscenarioCobranza.PROMOTORIA, bandeja, jdbc,
				"libreta_emitida", id)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("No se puede reemitir");
		SecurityContextHolder.clearContext();
		assertThat(contar("SELECT COUNT(*) FROM libreta_emitida WHERE motivo = 'REEMISION'")).isZero();
	}

	// ------------------------------------------------------------------ hallazgo 3: A5 también a Dirección

	/** Una corrección que sube el nivel aprobada por Promotoría también avisa a Dirección (quien aprobó no la recibe). */
	@Test
	void laCorreccionQueApruebaPromotoriaTambienVaADireccion() {
		cerrarYEmitir();
		corregir(mateo, competencias("MAT").get(0), "AD", null);
		List<AlertaRevision> alertas = como(EscenarioCobranza.PROMOTORIA, () -> alertasNotas.alertas());
		assertThat(alertas).anySatisfy(a -> {
			assertThat(a.aviso().tipo()).isEqualTo(TipoAviso.CORRECCION_NOTA_POR_PROMOTORIA);
			assertThat(a.aviso().tipo().soloDireccion()).isTrue();
			assertThat(a.aviso().excluidos()).contains(EscenarioCobranza.PROMOTORIA.getUsername());
		});
		assertThat(alertas).anySatisfy(a -> assertThat(a.aviso().tipo()).isEqualTo(TipoAviso.CORRECCIONES_NOTAS));
	}

	// ------------------------------------------------------------------ hallazgo 4 (y P-2): periodos posteriores

	/**
	 * Una corrección del I bimestre después de cerrar el II emite la versión siguiente de la libreta del I y también de la
	 * del II (que muestra la columna del I), con «(I bimestre)» en lo que cambió, y la familia recibe UN solo aviso.
	 */
	@Test
	void laCorreccionDeUnPeriodoAnteriorEmiteTambienLaLibretaDelSiguienteConUnSoloAviso() {
		cerrarYEmitir();
		Long competencia = competencias("MAT").get(0);
		Long nota = vigente(mateo, competencia);
		hoyEs(LocalDate.of(2026, 5, 11));
		Long segundo = como(EscenarioCobranza.DIRECCION, () -> periodos.programar(escuela.anio2026(),
				LocalDate.of(2026, 5, 11), LocalDate.of(2026, 7, 17)));
		como(EscenarioCobranza.DIRECCION, () -> periodos.abrir(segundo));
		NotasRequest hoja = hoja("MAT", List.of(mateo, valeria, lucas), "AD", null);
		como(UsuariosDePrueba.autenticado(ana), () -> registro.guardar(seccion, area("MAT"), segundo, hoja));
		hoyEs(LocalDate.of(2026, 7, 13));
		como(EscenarioCobranza.DIRECCION, () -> avance.cerrar(segundo));
		assertThat(emitir().alCierre()).isEqualTo(3);
		avisar();
		long antes = contar("SELECT COUNT(*) FROM mensaje WHERE apoderado_id = ? AND tipo LIKE 'LIBRETA%'", rosa);
		// La corrección del I bimestre (A → AD), pedida por Ana y aprobada por Promotoría.
		hoyEs(LocalDate.of(2026, 7, 14));
		como(UsuariosDePrueba.autenticado(ana), () -> correcciones.pedir(nota, "AD", null, MOTIVO_CORRECCION));
		EscenarioAprobaciones.aprueba(EscenarioCobranza.PROMOTORIA, bandeja, jdbc, "calificacion", nota);
		SecurityContextHolder.clearContext();
		assertThat(emitir().corregidas()).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT numero_version FROM libreta_emitida WHERE matricula_id = ? AND periodo_id = ? "
				+ "ORDER BY numero_version DESC LIMIT 1", Integer.class, mateo, segundo)).isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT cambios FROM libreta_emitida WHERE matricula_id = ? AND periodo_id = ? AND "
				+ "numero_version = 2", String.class, mateo, segundo)).contains("(I bimestre)").contains("A → AD");
		assertThat(jdbc.queryForObject("SELECT cambios FROM libreta_emitida WHERE matricula_id = ? AND periodo_id = ? AND "
				+ "numero_version = 2", String.class, mateo, periodo)).contains("A → AD").doesNotContain("(I bimestre)");
		avisar();
		assertThat(contar("SELECT COUNT(*) FROM mensaje WHERE apoderado_id = ? AND tipo LIKE 'LIBRETA%'", rosa) - antes)
				.as("un solo aviso por las dos libretas corregidas").isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT parametros FROM mensaje WHERE apoderado_id = ? AND tipo = "
				+ "'LIBRETA_CORREGIDA' ORDER BY id DESC LIMIT 1", String.class, rosa)).contains("I bimestre y II bimestre");
	}

	// ------------------------------------------------------------------ hallazgo 5: «en revisión» sin parámetro

	/** INDECOPI: un enlace manipulado con ?revision= no muestra a la familia que su libreta está retenida. */
	@Test
	void unEnlaceManipuladoNoMuestraLaLibretaEnRevision() throws Exception {
		cerrarYEmitir();
		String pagina = mvc.perform(get("/familia/libretas?revision=2026-05-11").with(UsuariosDePrueba.como(
				rosaEnLinea()))).andReturn().getResponse().getContentAsString();
		assertThat(pagina).contains("Libretas").doesNotContain("en revisión").doesNotContain("11/05/2026");
	}

	// ------------------------------------------------------------------ hallazgo 7: verificar sin niveles

	/** Con la huella distinta, la verificación no muestra los niveles (los de la base ya no son los del papel). */
	@Test
	void laVerificacionNoMuestraNivelesSiLaHuellaNoCoincide() {
		cerrarYEmitir();
		Long id = libreta(mateo);
		String codigo = jdbc.queryForObject("SELECT CONCAT('LIB-', id, '-', SUBSTRING(huella_contenido, 1, 16)) FROM "
				+ "libreta_emitida WHERE id = ?", String.class, id);
		assertThat(como(EscenarioCobranza.DIRECCION, () -> verificacion.verificar(codigo)).niveles()).isNotEmpty();
		jdbc.update("UPDATE calificacion SET nivel_logro = 'C' WHERE id = ?", vigente(mateo, competencias("MAT").get(0)));
		VerificacionVista vista = como(EscenarioCobranza.DIRECCION, () -> verificacion.verificar(codigo));
		assertThat(vista.coincide()).isFalse();
		assertThat(vista.niveles()).isEmpty();
	}

	// ------------------------------------------------------------------ hallazgo 8, P-1, P-6 y P-7: al cierre

	/**
	 * Hallazgo 8: Lucas estaba ACTIVO al cierre y se retiró antes de la pasada de emisión: recibe su libreta. La historia
	 * de la matrícula la escribe la base (en H2 la inserta la prueba).
	 */
	@Test
	void quienSeRetiraDespuesDelCierreRecibeSuLibreta() {
		anaPoneA();
		cerrarPeriodo();
		jdbc.update("UPDATE matricula SET estado = 'RETIRADA', retirada_en = ? WHERE id = ?", LocalDate.of(2026, 5, 4), lucas);
		historia(lucas, seccion, seccion, "ACTIVA", "RETIRADA", LocalDateTime.of(2026, 5, 4, 8, 3));
		assertThat(emitir().alCierre()).isEqualTo(3);
		assertThat(contar("SELECT COUNT(*) FROM libreta_emitida WHERE matricula_id = ?", lucas)).isEqualTo(1);
		assertThat(como(EscenarioCobranza.PROMOTORIA, () -> integridad.alertas())).noneSatisfy(a -> assertThat(a.texto())
				.contains("no tienen la libreta"));
	}

	/** P-6: una matrícula creada después del cierre no tiene libreta de ese periodo, y la integridad no la exige. */
	@Test
	void unaMatriculaPosteriorAlCierreNoTieneLibretaDeEsePeriodo() {
		anaPoneA();
		cerrarPeriodo();
		hoyEs(LocalDate.of(2026, 5, 6));
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		Long nuevo = matricula(alumnos.registrar(EscenarioEscolar.conApoderadoNuevo("71234590", "Nueva", "Llegada", "Iris",
				LocalDate.of(2015, 6, 1), "41234590", "Nueva", "Madre", "Elsa", "987111290", "elsa.nueva@correo.pe",
				seccion)).alumnoId());
		SecurityContextHolder.clearContext();
		hoyEs(LocalDate.of(2026, 5, 7));
		assertThat(emitir().alCierre()).isEqualTo(3);
		assertThat(contar("SELECT COUNT(*) FROM libreta_emitida WHERE matricula_id = ?", nuevo)).isZero();
		assertThat(como(EscenarioCobranza.PROMOTORIA, () -> integridad.alertas())).noneSatisfy(a -> assertThat(a.texto())
				.contains("no tienen la libreta"));
		// Y no frena la exportación para el SIAGIE (sale con los 3 que estaban al cierre).
		assertThat(como(EscenarioCobranza.DIRECCION, () -> siagie.exportar(seccion, periodo)).filas()).isEqualTo(3);
	}

	/**
	 * P-1 y P-7: Valeria estaba en 5.° A al cierre y se trasladó a 5.° B antes de la pasada: su libreta es de 5.° A; en
	 * «Libretas» de 5.° B no figura como faltante. Su corrección posterior se emite en 5.° A.
	 */
	@Test
	void laLibretaDelTrasladadoEsDeLaSeccionDelCierre() {
		anaPoneA();
		cerrarPeriodo();
		Long quintoB = escuela.primaria5B2026();
		como(EscenarioCobranza.ADMINISTRACION, () -> servicioMatriculas.cambiarSeccion(valeria,
				new CambiarSeccionRequest(quintoB, "Cambio de sección pedido por la familia")));
		historia(valeria, seccion, quintoB, "ACTIVA", "ACTIVA", LocalDateTime.of(2026, 5, 4, 8, 2));
		SecurityContextHolder.clearContext();
		assertThat(emitir().alCierre()).isEqualTo(3);
		assertThat(jdbc.queryForObject("SELECT seccion_id FROM libreta_emitida WHERE matricula_id = ?", Long.class, valeria))
				.isEqualTo(seccion);
		assertThat(como(EscenarioCobranza.DIRECCION, () -> libretasColegio.inicio(quintoB, periodo)).faltan()).isZero();
		assertThat(como(EscenarioCobranza.DIRECCION, () -> libretasColegio.inicio(seccion, periodo)).faltan()).isZero();
		corregir(valeria, competencias("MAT").get(0), "AD", null);
		assertThat(emitir().corregidas()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT seccion_id FROM libreta_emitida WHERE matricula_id = ? AND numero_version = 2",
				Long.class, valeria)).isEqualTo(seccion);
	}

	private void historia(Long matricula, Long anterior, Long nueva, String estadoAnterior, String estadoNuevo,
			LocalDateTime cuando) {
		jdbc.update("INSERT INTO matricula_historial (colegio_id, matricula_id, seccion_anterior_id, seccion_nueva_id, "
				+ "estado_anterior, estado_nuevo, cambiado_bd, creado_en, creado_por, actualizado_en) VALUES (1, ?, ?, ?, ?, ?, "
				+ "?, ?, 'base', ?)", matricula, anterior, nueva, estadoAnterior, estadoNuevo, cuando, cuando, cuando);
	}
}
