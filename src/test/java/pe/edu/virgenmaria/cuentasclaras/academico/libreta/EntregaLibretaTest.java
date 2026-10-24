package pe.edu.virgenmaria.cuentasclaras.academico.libreta;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.colegio.dto.CrearSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Nivel;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Sprint 10, tanda 3 (M9, E32, E33): la entrega de la libreta. El tutor imprime solo las de su sección (otra: 404); cada
 * impresión deja su constancia IMPRESA a su nombre y con su sesión; en Inicial, ENTREVISTA. Otra familia u otro colegio:
 * 404. Un docente que no es tutor no imprime.
 */
class EntregaLibretaTest extends PruebaLibretas {

	@Test
	void elTutorImprimeSoloSuSeccion() throws Exception {
		cerrarYEmitir();
		Long id = libreta(mateo);
		MockHttpServletResponse r = mvc.perform(get("/academico/libretas/" + id + ".pdf").with(UsuariosDePrueba.como(
				UsuariosDePrueba.autenticado(tito)))).andReturn().getResponse();
		assertThat(r.getStatus()).isEqualTo(200);
		assertThat(r.getHeader("Content-Disposition")).startsWith("attachment").contains("libreta-" + id + "-v1.pdf");
		assertThat(r.getHeader("Content-Security-Policy")).contains("sandbox");
		assertThat(jdbc.queryForMap("SELECT canal, creado_por, apoderado_id FROM entrega_libreta WHERE libreta_id = ?", id))
				.containsEntry("CANAL", "IMPRESA").containsEntry("CREADO_POR", "tito.tutor")
				.containsEntry("APODERADO_ID", null);
		// La sección completa (3 libretas, una por página como mínimo).
		MockHttpServletResponse seccionCompleta = mvc.perform(get("/academico/libretas/seccion.pdf")
				.param("seccion", seccion.toString()).param("periodo", periodo.toString())
				.with(UsuariosDePrueba.como(UsuariosDePrueba.autenticado(tito)))).andReturn().getResponse();
		assertThat(seccionCompleta.getStatus()).isEqualTo(200);
		assertThat(paginas(seccionCompleta.getContentAsByteArray())).isGreaterThanOrEqualTo(3);
		assertThat(contar("SELECT COUNT(*) FROM entrega_libreta WHERE canal = 'IMPRESA'")).isEqualTo(4);
		// Ana (docente de Matemática, no tutora) no imprime: 404.
		assertThat(mvc.perform(get("/academico/libretas/" + id + ".pdf").with(UsuariosDePrueba.como(
				UsuariosDePrueba.autenticado(ana)))).andReturn().getResponse().getStatus()).isEqualTo(404);
		// El tutor de OTRA sección tampoco.
		Long otra = como(EscenarioCobranza.ADMINISTRACION, () -> estructura.crearSeccion(escuela.anio2026(),
				new CrearSeccionRequest(Grado.PRIMARIA_4, "C")));
		Usuario otroTutor = docente("otro.tutor");
		Long asignacion = como(EscenarioCobranza.ADMINISTRACION, () -> asignaciones.proponer(ProponerAsignacionRequest
				.conAreas(otra, otroTutor.getId(), TipoAsignacion.TUTOR, MOTIVO, List.of(area("PS")))));
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "asignacion_docente", asignacion);
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
		assertThat(mvc.perform(get("/academico/libretas/" + id + ".pdf").with(UsuariosDePrueba.como(
				UsuariosDePrueba.autenticado(otroTutor)))).andReturn().getResponse().getStatus()).isEqualTo(404);
		assertThat(mvc.perform(get("/academico/libretas/seccion.pdf").param("seccion", seccion.toString())
				.param("periodo", periodo.toString()).with(UsuariosDePrueba.como(UsuariosDePrueba.autenticado(otroTutor))))
				.andReturn().getResponse().getStatus()).isEqualTo(404);
		// Dirección imprime cualquiera.
		assertThat(mvc.perform(get("/academico/libretas/" + id + ".pdf").with(UsuariosDePrueba.como(
				EscenarioCobranza.DIRECCION))).andReturn().getResponse().getStatus()).isEqualTo(200);
		// Caja y Promotoría no entran (403).
		assertThat(mvc.perform(get("/academico/libretas/" + id + ".pdf").with(UsuariosDePrueba.como(EscenarioCobranza.CAJA)))
				.andReturn().getResponse().getStatus()).isEqualTo(403);
		assertThat(mvc.perform(get("/academico/libretas").with(UsuariosDePrueba.como(EscenarioCobranza.PROMOTORIA)))
				.andReturn().getResponse().getStatus()).isEqualTo(403);
	}

	/** E32: la libreta de otra familia, 404 (y no queda constancia). */
	@Test
	void otraFamiliaResponde404() throws Exception {
		cerrarYEmitir();
		assertThat(mvc.perform(get("/familia/libretas/" + libreta(lucas) + ".pdf").with(UsuariosDePrueba.como(
				rosaEnLinea()))).andReturn().getResponse().getStatus()).isEqualTo(404);
		assertThat(mvc.perform(get("/familia/libretas/" + libreta(mateo) + ".pdf").with(UsuariosDePrueba.como(
				pedroEnLinea()))).andReturn().getResponse().getStatus()).isEqualTo(404);
		assertThat(contar("SELECT COUNT(*) FROM entrega_libreta")).isZero();
		String lista = mvc.perform(get("/familia/libretas").with(UsuariosDePrueba.como(pedroEnLinea()))).andReturn()
				.getResponse().getContentAsString();
		assertThat(lista).contains("Lucas").doesNotContain("Mateo");
		// Su propia libreta, sí (constancia PORTAL con su apoderado).
		assertThat(mvc.perform(get("/familia/libretas/" + libreta(lucas) + ".pdf").with(UsuariosDePrueba.como(
				pedroEnLinea()))).andReturn().getResponse().getStatus()).isEqualTo(200);
		assertThat(jdbc.queryForMap("SELECT canal, apoderado_id FROM entrega_libreta")).containsEntry("CANAL", "PORTAL")
				.containsEntry("APODERADO_ID", pedro);
	}

	/** M9: en Inicial la impresión queda como entrega en la ENTREVISTA con la familia. */
	@Test
	void enInicialSeEntregaEnLaEntrevista() {
		Long inicial = como(EscenarioCobranza.ADMINISTRACION, () -> estructura.crearSeccion(escuela.anio2026(),
				new CrearSeccionRequest(Grado.INICIAL_5, "Girasoles")));
		Long alumno = como(EscenarioCobranza.ADMINISTRACION, () -> alumnos.registrar(EscenarioEscolar.conApoderadoNuevo(
				"71234570", "Rojas", "Paz", "Ariana", LocalDate.of(2021, 4, 2), "41234570", "Paz", "Ríos", "Elena",
				"987555666", "elena.paz@gmail.com", inicial)).alumnoId());
		Long matricula = matricula(alumno);
		Usuario maestra = docente("maestra.inicial");
		Long asignacion = como(EscenarioCobranza.ADMINISTRACION, () -> asignaciones.proponer(ProponerAsignacionRequest
				.conAreas(inicial, maestra.getId(), TipoAsignacion.TUTOR, MOTIVO, List.of(area(Nivel.INICIAL, "PS")))));
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "asignacion_docente", asignacion);
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
		cerrarPeriodo();
		emitir();
		Long id = libreta(matricula);
		var descarga = como(UsuariosDePrueba.autenticado(maestra), () -> libretasColegio.imprimir(id));
		assertThat(descarga.enRevision()).isFalse();
		assertThat(jdbc.queryForObject("SELECT canal FROM entrega_libreta WHERE libreta_id = ?", String.class, id))
				.isEqualTo("ENTREVISTA");
	}
}
