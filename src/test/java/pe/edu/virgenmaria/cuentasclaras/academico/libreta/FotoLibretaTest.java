package pe.edu.virgenmaria.cuentasclaras.academico.libreta;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.dto.Descarga;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.LibretaEmitida;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.repository.LibretaEmitidaRepository;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.ContenidoLibreta;
import pe.edu.virgenmaria.cuentasclaras.academico.libreta.service.FotoLibreta;
import pe.edu.virgenmaria.cuentasclaras.academico.notas.dto.ExonerarRequest;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.AlertaRevision;
import pe.edu.virgenmaria.cuentasclaras.comun.alertas.TipoAviso;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.multicolegio.ContextoColegio;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Sprint 10, tanda 3 (secciones 3.7 y 3.13; decisión 170, A3, E40, decisión 188): la foto de la libreta. La cabecera está en
 * la foto y en la huella; una base alterada después de emitir no entrega ese PDF: la familia ve «en revisión» con la fecha,
 * Promotoría recibe la alerta, Dirección pide la reemisión, Promotoría la aprueba y sistema.libretas emite la versión
 * siguiente. Nunca «sin libreta». Una exoneración rige desde su periodo.
 */
class FotoLibretaTest extends PruebaLibretas {

	@Autowired
	private LibretaEmitidaRepository libretas;

	@Autowired
	private PlatformTransactionManager transacciones;

	private <T> T enElColegio(Supplier<T> lectura) {
		return ContextoColegio.en(1L, () -> new TransactionTemplate(transacciones).execute(t -> lectura.get()));
	}

	/** A3: la cabecera congelada entra en la huella (si alguien cambia el nombre guardado, la huella ya no coincide). */
	@Test
	void cabeceraEnLaHuella() {
		cerrarYEmitir();
		Long id = libreta(mateo);
		LibretaEmitida l = enElColegio(() -> libretas.findById(id).orElseThrow());
		ContenidoLibreta contenido = enElColegio(() -> foto.armar(libretas.findById(id).orElseThrow()));
		assertThat(FotoLibreta.huella(contenido)).isEqualTo(l.getHuellaContenido());
		assertThat(contenido.cabecera().alumnoNombre()).isEqualTo("Quispe Huamán, Mateo");
		assertThat(contenido.cabecera().tutorNombre()).isNotBlank();
		LibretaEmitida.Cabecera otra = new LibretaEmitida.Cabecera("Quispe Huamán, Matías", contenido.cabecera()
				.alumnoCodigo(), contenido.cabecera().tipoCodigo(), contenido.cabecera().codigoModular(),
				contenido.cabecera().dre(), contenido.cabecera().ugel(), contenido.cabecera().tutorNombre(),
				contenido.cabecera().directorNombre());
		ContenidoLibreta cambiada = new ContenidoLibreta(contenido.colegio(), contenido.nivel(), contenido.grado(),
				contenido.seccion(), contenido.anio(), contenido.alumnoId(), contenido.periodoId(), contenido.periodoNumero(),
				contenido.periodoNombre(), contenido.ultimoPeriodo(), otra, contenido.periodos(), contenido.areas(),
				contenido.asistencia(), contenido.escala());
		assertThat(FotoLibreta.huella(cambiada)).isNotEqualTo(l.getHuellaContenido());
		// Si mañana cambia el nombre del alumno, la libreta emitida no cambia (está en la foto).
		jdbc.update("UPDATE alumno SET nombres = 'Matías' WHERE id = (SELECT alumno_id FROM matricula WHERE id = ?)", mateo);
		ContenidoLibreta despues = enElColegio(() -> foto.armar(libretas.findById(id).orElseThrow()));
		assertThat(despues.cabecera().alumnoNombre()).isEqualTo("Quispe Huamán, Mateo");
		assertThat(FotoLibreta.huella(despues)).isEqualTo(l.getHuellaContenido());
	}

	/**
	 * E40, A3 y decisión 188: alguien cambia una nota en la base después de emitir. La familia no recibe ese PDF sino «en
	 * revisión» con la fecha (2 días hábiles), Promotoría recibe la alerta, Dirección pide la reemisión (solo con la huella
	 * distinta), Promotoría la aprueba (Dirección no) y sistema.libretas emite la versión siguiente: se descarga otra vez.
	 */
	@Test
	void huellaDistintaDaPlazoYReemision() throws Exception {
		cerrarYEmitir();
		Long id = libreta(mateo);
		// Sin la huella distinta, Dirección no puede pedir una reemisión.
		assertThatThrownBy(() -> como(EscenarioCobranza.DIRECCION, () -> reemision.pedir(id, "Revisión de integridad")))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("coincide");
		// Alguien cambia la nota de Mateo directo en la base (H2 no tiene triggers).
		jdbc.update("UPDATE calificacion SET nivel_logro = 'C' WHERE id = ?", vigente(mateo, competencias("MAT").get(0)));
		hoyEs(LocalDate.of(2026, 5, 7));
		org.springframework.test.web.servlet.MvcResult descarga = mvc.perform(get("/familia/libretas/" + id + ".pdf")
				.with(UsuariosDePrueba.como(rosaEnLinea()))).andReturn();
		MockHttpServletResponse r = descarga.getResponse();
		assertThat(r.getStatus()).isEqualTo(303);
		// Correcciones del sprint 10 (hallazgo 5): el aviso viaja en el flash que calcula el servidor, no en la URL.
		assertThat(r.getRedirectedUrl()).isEqualTo("/familia/libretas");
		String pagina = mvc.perform(get(r.getRedirectedUrl()).flashAttrs(descarga.getFlashMap())
				.with(UsuariosDePrueba.como(rosaEnLinea()))).andReturn().getResponse().getContentAsString();
		assertThat(pagina).contains("Su libreta está en revisión. El colegio se la entregará a más tardar el 11/05/2026.");
		assertThat(contar("SELECT COUNT(*) FROM entrega_libreta")).isZero();
		assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'LIBRETA_HUELLA_NO_COINCIDE'")).isEqualTo(1);
		// La alerta CRÍTICA a Promotoría (sale al celular).
		List<AlertaRevision> alertas = como(EscenarioCobranza.PROMOTORIA, () -> integridad.alertas());
		assertThat(alertas).anySatisfy(a -> {
			assertThat(a.gravedad()).isEqualTo(AlertaRevision.Gravedad.CRITICA);
			assertThat(a.aviso().tipo()).isEqualTo(TipoAviso.INTEGRIDAD_LIBRETAS);
			assertThat(a.texto()).contains("no coincide con su huella").contains("2026-05-11");
			assertThat(a.difundible()).isTrue();
		});
		// Dirección pide la reemisión; Dirección no la aprueba; Promotoría sí.
		Long solicitud = como(EscenarioCobranza.DIRECCION, () -> reemision.pedir(id, "La nota de Matemática cambió en la "
				+ "base: revisado con la docente"));
		assertThat(solicitud).isNotNull();
		assertThatThrownBy(() -> {
			try {
				EscenarioAprobaciones.aprueba(UsuariosDePrueba.autenticado(usuarios.findByNombreUsuario("director")
						.orElseThrow()), bandeja, jdbc, "libreta_emitida", id);
			}
			finally {
				org.springframework.security.core.context.SecurityContextHolder.clearContext();
			}
		}).isInstanceOf(RuntimeException.class);
		// Correcciones del sprint 10 (hallazgo 2): con la nota alterada, Promotoría tampoco puede aprobarla (la reemisión
		// vuelve a emitir los niveles emitidos). Revisado con el responsable técnico, la nota vuelve a lo emitido y entonces
		// sí se reemite (con los mismos niveles).
		assertThatThrownBy(() -> {
			try {
				EscenarioAprobaciones.aprueba(EscenarioCobranza.PROMOTORIA, bandeja, jdbc, "libreta_emitida", id);
			}
			finally {
				org.springframework.security.core.context.SecurityContextHolder.clearContext();
			}
		}).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("No se puede reemitir");
		jdbc.update("UPDATE calificacion SET nivel_logro = 'A' WHERE id = ?", vigente(mateo, competencias("MAT").get(0)));
		try {
			EscenarioAprobaciones.aprueba(EscenarioCobranza.PROMOTORIA, bandeja, jdbc, "libreta_emitida", id);
		}
		finally {
			org.springframework.security.core.context.SecurityContextHolder.clearContext();
		}
		assertThat(emitir().reemitidas()).isEqualTo(1);
		Long nueva = libreta(mateo);
		assertThat(jdbc.queryForMap("SELECT numero_version, motivo, cambios, solicitud_id FROM libreta_emitida WHERE id = ?",
				nueva)).containsEntry("NUMERO_VERSION", 2).containsEntry("MOTIVO", "REEMISION")
				.containsEntry("CAMBIOS", "Reemitida tras una revisión de integridad");
		assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'LIBRETA_REEMITIDA'")).isEqualTo(1);
		assertThat(mvc.perform(get("/familia/libretas/" + nueva + ".pdf").with(UsuariosDePrueba.como(rosaEnLinea())))
				.andReturn().getResponse().getStatus()).isEqualTo(200);
		// La alerta ya no sale (la vigente coincide) y la familia recibe el aviso de libreta corregida.
		assertThat(como(EscenarioCobranza.PROMOTORIA, () -> integridad.alertas())).noneMatch(a -> a.texto()
				.contains("no coincide con su huella"));
		assertThat(avisar()).isGreaterThanOrEqualTo(1);
		assertThat(contar("SELECT COUNT(*) FROM mensaje WHERE familia_id = ? AND tipo = 'LIBRETA_CORREGIDA'", familiaRosa))
				.isEqualTo(1);
	}

	/** Vencido el plazo sin reemitir, la alerta se repite cada día (otra referencia por día). */
	@Test
	void vencidoElPlazoLaAlertaSeRepiteCadaDia() {
		cerrarYEmitir();
		Long id = libreta(mateo);
		jdbc.update("UPDATE calificacion SET nivel_logro = 'C' WHERE id = ?", vigente(mateo, competencias("MAT").get(0)));
		hoyEs(LocalDate.of(2026, 5, 7));
		Descarga d = como(rosaEnLinea(), () -> portal.descargar(id));
		assertThat(d.enRevision()).isTrue();
		hoyEs(LocalDate.of(2026, 5, 12));
		String hoy = como(EscenarioCobranza.PROMOTORIA, () -> integridad.alertas()).stream()
				.filter(a -> a.texto().contains("Venció el plazo")).findFirst().orElseThrow().aviso().referencia();
		hoyEs(LocalDate.of(2026, 5, 13));
		String manana = como(EscenarioCobranza.PROMOTORIA, () -> integridad.alertas()).stream()
				.filter(a -> a.texto().contains("Venció el plazo")).findFirst().orElseThrow().aviso().referencia();
		assertThat(hoy).isNotEqualTo(manana).startsWith("LIBHUELLA:" + id);
	}

	/** A3: la exoneración rige desde su periodo: en la libreta de ese periodo sale «EXO» y no cuenta como nota. */
	@Test
	void exoneracionDesdeSuPeriodo() {
		anaPoneA();
		como(EscenarioCobranza.DIRECCION, () -> exoneraciones.exonerar(new ExonerarRequest(valeria, area("EF"), "EXP-001",
				"Escrito del padre por indicación médica")));
		cerrarPeriodo();
		emitir();
		Long id = libreta(valeria);
		ContenidoLibreta c = enElColegio(() -> foto.armar(libretas.findById(id).orElseThrow()));
		ContenidoLibreta.Area ef = c.areas().stream().filter(a -> a.codigo().equals("EF")).findFirst().orElseThrow();
		assertThat(ef.competencias()).allSatisfy(k -> assertThat(k.delPeriodo().texto()).isEqualTo("EXO"));
		ContenidoLibreta.Area mat = c.areas().stream().filter(a -> a.codigo().equals("MAT")).findFirst().orElseThrow();
		assertThat(mat.competencias()).allSatisfy(k -> assertThat(k.delPeriodo().texto()).isEqualTo("A"));
		// Mateo no está exonerado.
		ContenidoLibreta deMateo = enElColegio(() -> foto.armar(libretas.findById(libreta(mateo)).orElseThrow()));
		assertThat(deMateo.areas().stream().filter(a -> a.codigo().equals("EF")).findFirst().orElseThrow().competencias())
				.allSatisfy(k -> assertThat(k.delPeriodo().exonerada()).isFalse());
	}
}
