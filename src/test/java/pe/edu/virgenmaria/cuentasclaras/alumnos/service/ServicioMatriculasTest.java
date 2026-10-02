package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.CambiarSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.MatriculaResultado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.MatricularRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.RegistroResultado;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.Estructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.MOTIVO;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.ultimoEvento;

/** Matrículas: una por alumno y año, en una sección de ese año; cada una publica MatriculaRegistrada. */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
@RecordApplicationEvents
class ServicioMatriculasTest {

	@Autowired
	private ServicioMatriculas matriculas;

	@Autowired
	private ServicioAlumnos alumnos;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ApplicationEvents eventos;

	private Estructura escuela;

	private Long mateo;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.ADMINISTRACION));
		escuela = EscenarioEscolar.crearEstructura(estructura);
		mateo = alumnos.registrar(EscenarioEscolar.mateoConRosa(null)).alumnoId();
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void unaMatriculaPorAlumnoYAnio() {
		matriculas.matricular(mateo, new MatricularRequest(escuela.primaria5A2026(), null));

		assertThatThrownBy(() -> matriculas.matricular(mateo, new MatricularRequest(escuela.primaria5B2026(), null)))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessage("Mateo Quispe Huamán ya está matriculado en 2026 (5.° Primaria A). Para moverlo usa «Cambiar sección».");
		// En otro año sí (matrícula 2027 adelantada).
		matriculas.matricular(mateo, new MatricularRequest(escuela.primaria6A2027(), null));
		assertThat(contar(jdbc, "matricula")).isEqualTo(2);

		// En la base: UNIQUE (colegio_id, alumno_id, anio_escolar_id).
		assertThatThrownBy(() -> jdbc.update("INSERT INTO matricula (colegio_id, alumno_id, anio_escolar_id, seccion_id, "
				+ "fecha_matricula, estado, creado_en, creado_por, actualizado_en) VALUES (1, ?, ?, ?, DATE '2026-03-02', "
				+ "'ACTIVA', CURRENT_TIMESTAMP, 'prueba', CURRENT_TIMESTAMP)", mateo, escuela.anio2026(),
				escuela.primaria5B2026())).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void seccionDebeSerDelAnioDeLaMatriculaTambienEnLaBase() {
		Long matricula = matriculas.matricular(mateo, new MatricularRequest(escuela.primaria5A2026(), null)).matriculaId();

		assertThatThrownBy(() -> matriculas.cambiarSeccion(matricula,
				new CambiarSeccionRequest(escuela.primaria6A2027(), MOTIVO)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("mismo año (2026)");

		// En la base: FK (seccion_id, anio_escolar_id) → seccion (id, anio_escolar_id).
		assertThatThrownBy(() -> jdbc.update("UPDATE matricula SET seccion_id = ? WHERE id = ?",
				escuela.primaria6A2027(), matricula)).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(jdbc.queryForObject("SELECT seccion_id FROM matricula WHERE id = ?", Long.class, matricula))
				.isEqualTo(escuela.primaria5A2026());
	}

	@Test
	void fechaDeMatriculaFuturaEsRechazada() {
		LocalDate hoy = LocalDate.of(2026, 10, 2);
		assertThatThrownBy(() -> matriculas.matricular(mateo,
				new MatricularRequest(escuela.primaria6A2027(), hoy.plusDays(1))))
				.isInstanceOf(ReglaNegocioException.class).hasMessage("La fecha de matrícula no puede ser futura.");
		assertThatThrownBy(() -> matriculas.matricular(mateo,
				new MatricularRequest(escuela.primaria6A2027(), LocalDate.of(2026, 6, 30))))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("entre el 01/07/2026 y el 17/12/2027");
		assertThat(contar(jdbc, "matricula")).isZero();

		// Sin fecha: hoy para 2027 (las clases aún no empiezan) y el inicio de clases para 2026.
		Long de2027 = matriculas.matricular(mateo, new MatricularRequest(escuela.primaria6A2027(), null)).matriculaId();
		assertThat(jdbc.queryForObject("SELECT fecha_matricula FROM matricula WHERE id = ?", Date.class, de2027))
				.isEqualTo(Date.valueOf(hoy));
		Long de2026 = matriculas.matricular(mateo, new MatricularRequest(escuela.primaria5A2026(), null)).matriculaId();
		assertThat(jdbc.queryForObject("SELECT fecha_matricula FROM matricula WHERE id = ?", Date.class, de2026))
				.isEqualTo(Date.valueOf("2026-03-02"));
	}

	@Test
	void cambioDeSeccionEnElMismoNivelSeAudita() {
		Long matricula = matriculas.matricular(mateo, new MatricularRequest(escuela.primaria5A2026(), null)).matriculaId();

		assertThatThrownBy(() -> matriculas.cambiarSeccion(matricula, new CambiarSeccionRequest(escuela.primaria5B2026(),
				"corto"))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("entre 10 y 500");
		assertThat(alumnos.obtenerFicha(mateo).matriculas().getFirst().otrasSecciones())
				.extracting(s -> s.etiqueta()).contains("5.° Primaria B").doesNotContain("5.° Primaria A");

		assertThat(matriculas.cambiarSeccion(matricula, new CambiarSeccionRequest(escuela.primaria5B2026(), MOTIVO)))
				.isEqualTo(mateo);

		Map<String, Object> evento = ultimoEvento(jdbc, "MATRICULA_SECCION_CAMBIADA");
		assertThat(evento).containsEntry("valor_anterior", "5.° Primaria A").containsEntry("valor_nuevo", "5.° Primaria B")
				.containsEntry("entidad_id", matricula.toString());
		assertThat((String) evento.get("detalle")).contains("Mateo Quispe Huamán", "2026", MOTIVO);
		// A la misma sección: no hay cambio.
		assertThatThrownBy(() -> matriculas.cambiarSeccion(matricula,
				new CambiarSeccionRequest(escuela.primaria5B2026(), MOTIVO))).isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya está en 5.° Primaria B");
		// Sin cuotas todavía (tanda 3), cambiar de nivel está permitido.
		matriculas.cambiarSeccion(matricula, new CambiarSeccionRequest(escuela.secundaria1A2026(), MOTIVO));
	}

	@Test
	void matricularPublicaMatriculaRegistrada() {
		eventos.clear();
		MatriculaResultado resultado = matriculas.matricular(mateo, new MatricularRequest(escuela.primaria5A2026(), null));
		RegistroResultado valeria = alumnos.registrar(EscenarioEscolar.valeriaConRosaRegistrada(escuela.primaria2B2026()));

		Long deValeria = jdbc.queryForObject("SELECT id FROM matricula WHERE alumno_id = ?", Long.class,
				valeria.alumnoId());
		assertThat(eventos.stream(MatriculaRegistrada.class)).containsExactly(
				new MatriculaRegistrada(resultado.matriculaId()), new MatriculaRegistrada(deValeria));
		assertThat(ultimoEvento(jdbc, "MATRICULA_REGISTRADA")).containsEntry("entidad_id", deValeria.toString());
	}

	@Test
	void edadQueNoCorrespondeAlGradoEsUnaAdvertencia() {
		MatriculaResultado resultado = matriculas.matricular(mateo,
				new MatricularRequest(escuela.secundaria1A2026(), null));
		assertThat(resultado.advertencia()).contains("Tendrá 10 años", "1.° Secundaria");
	}
}
