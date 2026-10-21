package pe.edu.virgenmaria.cuentasclaras.academico;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.HojaDelDia;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.HojaRequest;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.MarcaPedida;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.ResultadoRegistro;
import pe.edu.virgenmaria.cuentasclaras.academico.model.EstadoAsistencia;
import pe.edu.virgenmaria.cuentasclaras.comun.error.RecursoNoEncontradoException;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioCobranza;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 9, tanda 2 (sección 3.4; E1, E2, E49): la asistencia del día la registra SOLO el docente asignado, HOY, en un
 * POST con UNA firma; Dirección no registra (403) y una sección ajena responde 404.
 */
class RegistroAsistenciaTest extends PruebaAsistencia {

	@Autowired
	private MockMvc mvc;

	@Test
	void soloElDiaDeHoy() {
		HojaDelDia hoja = como(tutora(), () -> asistencia.hoja(datos.seccion()));
		assertThat(hoja.fecha()).isEqualTo(EscenarioAsistencia.MARTES);
		assertThat(hoja.registrado()).isFalse();
		assertThat(hoja.alumnos()).extracting(a -> a.estado()).containsOnly("PRESENTE");
		ResultadoRegistro resultado = registrar(Map.of(datos.mateo(), EstadoAsistencia.FALTA));
		assertThat(resultado.resumen()).isEqualTo("2 presentes, 1 falta, 0 tardes");
		assertThat(jdbc.queryForObject("SELECT fecha FROM registro_asistencia WHERE id = ?", LocalDate.class,
				resultado.registroId())).isEqualTo(EscenarioAsistencia.MARTES);
		assertThat(jdbc.queryForObject("SELECT origen FROM registro_asistencia WHERE id = ?", String.class,
				resultado.registroId())).isEqualTo("DEL_DIA");
		// El sábado no hay clases: no se registra (E1, la aplicación; en MySQL, el trigger con la fecha de la base).
		alas(4, 8, 0);
		assertThatThrownBy(() -> registrar(Map.of())).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("fin de semana");
		// Un registro del mismo día no se repite.
		alas(0, 9, 0);
		assertThatThrownBy(() -> registrar(Map.of())).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya está registrada");
	}

	@Test
	void seccionAjenaResponde404() {
		assertThatThrownBy(() -> como(otroDocente(), () -> asistencia.hoja(datos.seccion())))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> como(otroDocente(), () -> asistencia.guardar(datos.seccion(),
				EscenarioAsistencia.hoja(Map.of(), List.of(datos.mateo(), datos.valeria(), datos.lucas())))))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(EscenarioEscolar.contar(jdbc, "registro_asistencia")).isZero();
	}

	@Test
	void direccionNoRegistra() throws Exception {
		mvc.perform(get("/academico/asistencia/{id}/hoy", datos.seccion()).with(UsuariosDePrueba.como(
				EscenarioCobranza.DIRECCION))).andExpect(status().isForbidden());
		mvc.perform(post("/academico/asistencia/{id}/hoy", datos.seccion()).with(csrf())
				.param("estado-" + datos.mateo(), "FALTA").with(UsuariosDePrueba.como(EscenarioCobranza.DIRECCION)))
				.andExpect(status().isForbidden());
		mvc.perform(get("/academico/asistencia/{id}/hoy", datos.seccion()).with(UsuariosDePrueba.como(
				EscenarioCobranza.CAJA))).andExpect(status().isForbidden());
		// El docente sí (y la hoja queda en «Quién vio datos personales»).
		mvc.perform(get("/academico/asistencia/{id}/hoy", datos.seccion()).with(UsuariosDePrueba.como(datos.tutora())))
				.andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM acceso_dato_personal WHERE tipo = 'LISTA_SECCION'",
				Long.class)).isPositive();
		assertThat(EscenarioEscolar.contar(jdbc, "registro_asistencia")).isZero();
	}

	@Test
	void elFormularioGuardaLaHojaEnUnSoloPost() throws Exception {
		mvc.perform(post("/academico/asistencia/{id}/hoy", datos.seccion()).with(csrf())
				.param("estado-" + datos.mateo(), "FALTA").param("estado-" + datos.valeria(), "TARDE")
				.param("hora-" + datos.valeria(), "08:12").param("estado-" + datos.lucas(), "PRESENTE")
				.with(UsuariosDePrueba.como(datos.tutora()))).andExpect(status().is3xxRedirection());
		assertThat(EscenarioAsistencia.estadoVigente(jdbc, datos.mateo(), EscenarioAsistencia.MARTES)).isEqualTo("FALTA");
		assertThat(jdbc.queryForObject("SELECT hora_llegada FROM marca_asistencia WHERE alumno_id = ?", String.class,
				datos.valeria())).startsWith("08:12");
		assertThat(jdbc.queryForObject("SELECT fuente FROM marca_asistencia WHERE alumno_id = ?", String.class,
				datos.lucas())).isEqualTo("DOCENTE");
	}

	/** 30 alumnos en UN POST con UNA firma de la sesión (A5), todas las marcas a nombre de quien registró. */
	@Test
	void treintaAlumnosEnUnPostConUnaFirma() {
		EscenarioCobranza.como(EscenarioCobranza.ADMINISTRACION);
		List<Long> todos = new ArrayList<>(List.of(datos.mateo(), datos.valeria(), datos.lucas()));
		for (int i = 0; i < 27; i++) {
			String letra = String.valueOf((char) ('a' + i % 26)) + (i >= 26 ? "z" : "");
			todos.add(alumnos.registrar(EscenarioEscolar.conApoderadoNuevo(String.format("6%07d", i + 1), "Apellido" + letra,
					"Materno", "Alumno" + letra, LocalDate.of(2015, 1, 1).plusDays(i), String.format("5%07d", i + 1),
					"Padre" + letra, "Materno", "Nombre" + letra, null, "padre" + i + "@correo.pe", datos.seccion()))
					.alumnoId());
		}
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
		List<MarcaPedida> marcas = todos.stream().map(a -> new MarcaPedida(a, EstadoAsistencia.PRESENTE, null)).toList();
		ResultadoRegistro resultado = como(tutora(), () -> asistencia.guardar(datos.seccion(),
				new HojaRequest(marcas, null)));
		assertThat(resultado.presentes()).isEqualTo(30);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM marca_asistencia WHERE registro_id = ? AND creado_por = ?",
				Long.class, resultado.registroId(), "ana.docente")).isEqualTo(30);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM firma_operacion WHERE clave LIKE 'asistencia:%'",
				Long.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT clave FROM firma_operacion WHERE clave LIKE 'asistencia:%'", String.class))
				.isEqualTo("asistencia:" + datos.seccion() + ":2026-10-06:REGISTRADA");
	}

	@Test
	void unaHojaIncompletaSeRechaza() {
		assertThatThrownBy(() -> como(tutora(), () -> asistencia.guardar(datos.seccion(),
				EscenarioAsistencia.hoja(Map.of(), List.of(datos.mateo())))))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no tiene a todos");
		assertThatThrownBy(() -> como(tutora(), () -> asistencia.guardar(datos.seccion(), new HojaRequest(List.of(
				new MarcaPedida(datos.mateo(), EstadoAsistencia.FALTA_JUSTIFICADA, null),
				new MarcaPedida(datos.valeria(), EstadoAsistencia.PRESENTE, null),
				new MarcaPedida(datos.lucas(), EstadoAsistencia.PRESENTE, null)), null))))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("justificada");
	}
}
