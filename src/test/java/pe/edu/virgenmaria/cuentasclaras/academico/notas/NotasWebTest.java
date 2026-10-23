package pe.edu.virgenmaria.cuentasclaras.academico.notas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ProponerAsignacionRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.model.TipoAsignacion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Usuario;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 10, tanda 2: las pantallas de las notas por la web (la matriz y los servicios): el docente abre su hoja y guarda
 * con un POST; Dirección no entra a la hoja (403) pero ve la sección, el avance, el cierre con su resumen y las
 * exoneraciones; Promotoría ve el avance; «Completar áreas» muestra la propuesta llenada. Ninguna pantalla muestra
 * documentos, contactos ni deudas.
 */
class NotasWebTest extends PruebaNotas {

	@Autowired
	private MockMvc mvc;

	private static org.springframework.test.web.servlet.request.RequestPostProcessor de(
			pe.edu.virgenmaria.cuentasclaras.seguridad.service.UsuarioAutenticado quien) {
		return authentication(UsuariosDePrueba.autenticacion(quien));
	}

	@Test
	void elDocenteAbreSuHojaYGuardaConUnPost() throws Exception {
		String ruta = "/academico/notas/" + seccion + "/" + area("MAT");
		mvc.perform(get("/academico/notas").with(UsuariosDePrueba.como(ana))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Matemática")));
		mvc.perform(get(ruta).with(UsuariosDePrueba.como(ana))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Guardar notas de Matemática")))
				.andExpect(content().string(containsString("Mateo")))
				.andExpect(content().string(not(containsString("DNI"))));
		MockHttpServletRequestBuilder envio = post(ruta).param("periodo", periodo.toString())
				.with(UsuariosDePrueba.como(ana)).with(csrf());
		for (Long m : List.of(mateo, valeria, lucas)) {
			for (Long c : competencias("MAT")) {
				envio = envio.param("nivel-" + m + "-" + c, "A");
			}
		}
		mvc.perform(envio).andExpect(status().is3xxRedirection());
		assertThat(contar("SELECT COUNT(*) FROM calificacion")).isEqualTo(12);
		assertThat(contar("SELECT COUNT(*) FROM lote_calificacion")).isEqualTo(1);
		assertThat(contar("SELECT COUNT(*) FROM acceso_dato_personal WHERE tipo = 'NOTAS_SECCION'")).isPositive();
	}

	@Test
	void direccionNoEntraALaHojaPeroVeLaSeccionElAvanceElCierreYLasExoneraciones() throws Exception {
		anaPoneA();
		mvc.perform(get("/academico/notas/" + seccion + "/" + area("MAT")).with(de(EscenarioCobranza.DIRECCION)))
				.andExpect(status().isForbidden());
		mvc.perform(get("/academico/notas/secciones").with(de(EscenarioCobranza.DIRECCION))).andExpect(status().isOk());
		mvc.perform(get("/academico/notas/secciones/" + seccion).with(de(EscenarioCobranza.DIRECCION)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Matemática")));
		mvc.perform(get("/academico/notas/avance").with(de(EscenarioCobranza.DIRECCION))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Correcciones de notas del periodo")));
		mvc.perform(get("/academico/notas/avance").with(de(EscenarioCobranza.PROMOTORIA))).andExpect(status().isOk())
				.andExpect(content().string(not(containsString("Mateo"))));
		mvc.perform(get("/academico/notas/avance/correcciones").param("periodo", periodo.toString())
				.with(de(EscenarioCobranza.DIRECCION))).andExpect(status().isOk());
		mvc.perform(get("/academico/periodos/" + periodo + "/cierre").with(de(EscenarioCobranza.DIRECCION)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Confirmar el cierre")));
		mvc.perform(get("/academico/notas/exoneraciones").with(de(EscenarioCobranza.DIRECCION)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Exonerar en")));
		mvc.perform(get("/academico/notas/exoneraciones").with(de(EscenarioCobranza.PROMOTORIA)))
				.andExpect(status().isForbidden());
	}

	@Test
	void elTutorVeSuSeccionYPideUnaCorreccion() throws Exception {
		anaPoneA();
		cerrarPeriodo();
		mvc.perform(get("/academico/notas/secciones/" + seccion).with(UsuariosDePrueba.como(tito)))
				.andExpect(status().isOk());
		mvc.perform(get("/academico/notas/secciones/" + seccion).with(UsuariosDePrueba.como(luis)))
				.andExpect(status().isNotFound());
		Long nota = vigente(mateo, competencias("MAT").get(0));
		mvc.perform(get("/academico/notas/" + seccion + "/" + area("MAT")).param("periodo", periodo.toString())
				.with(UsuariosDePrueba.como(ana))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Pedir corrección")));
		mvc.perform(get("/academico/notas/correcciones/" + nota).with(UsuariosDePrueba.como(ana)))
				.andExpect(status().isOk()).andExpect(content().string(containsString("Pedir la corrección")));
		mvc.perform(post("/academico/notas/correcciones/" + nota).param("nivel", "AD").param("motivo", MOTIVO_CORRECCION)
				.with(UsuariosDePrueba.como(ana)).with(csrf())).andExpect(status().is3xxRedirection());
		assertThat(contar("SELECT COUNT(*) FROM solicitud_cambio WHERE tipo = 'CORRECCION_CALIFICACION'")).isEqualTo(1);
	}

	@Test
	void completarAreasMuestraLaPropuestaLlenada() throws Exception {
		Usuario delSprint9 = docente("pepe.sprint9");
		Long id = como(EscenarioCobranza.ADMINISTRACION, () -> asignaciones.proponer(new ProponerAsignacionRequest(
				escuela.primaria5B2026(), delSprint9.getId(), TipoAsignacion.DOCENTE, "Matemática", MOTIVO)));
		EscenarioAprobaciones.aprueba(EscenarioCobranza.DIRECCION, bandeja, jdbc, "asignacion_docente", id);
		SecurityContextHolder.clearContext();
		mvc.perform(get("/academico/asignaciones").with(de(EscenarioCobranza.DIRECCION))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Completar áreas")));
		mvc.perform(get("/academico/asignaciones/completar").param("asignacion", id.toString())
				.with(de(EscenarioCobranza.DIRECCION))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Antes decía")));
		mvc.perform(post("/academico/asignaciones/completar").param("areas-" + id, area("MAT").toString())
				.param("motivo", "Completar las áreas del catálogo").with(de(EscenarioCobranza.DIRECCION)).with(csrf()))
				.andExpect(status().is3xxRedirection());
		assertThat(contar("SELECT COUNT(*) FROM asignacion_docente WHERE estado = 'PROPUESTA' AND usuario_id = ?",
				delSprint9.getId())).isEqualTo(1);
	}
}
