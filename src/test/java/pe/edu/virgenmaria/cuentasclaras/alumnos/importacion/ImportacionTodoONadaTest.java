package pe.edu.virgenmaria.cuentasclaras.alumnos.importacion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.service.ServicioEstructura;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.ConfiguracionRelojAjustable;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.LimpiezaBaseDatos;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.PruebaIntegracion;
import pe.edu.virgenmaria.cuentasclaras.comun.prueba.UsuariosDePrueba;
import pe.edu.virgenmaria.cuentasclaras.seguridad.model.Rol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ArchivoImportacion.archivo;
import static pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ArchivoImportacion.mateo;
import static pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ArchivoImportacion.sebastian;
import static pe.edu.virgenmaria.cuentasclaras.alumnos.importacion.ArchivoImportacion.valeria;
import static pe.edu.virgenmaria.cuentasclaras.comun.prueba.EscenarioEscolar.contar;

/**
 * Todo o nada: si la confirmación falla DESPUÉS de registrar todas las filas (aquí se fuerza una falla al auditar la
 * importación), no queda ningún alumno, familia, matrícula ni evento.
 */
@PruebaIntegracion
@Import(ConfiguracionRelojAjustable.class)
class ImportacionTodoONadaTest {

	@MockitoSpyBean
	private AuditoriaService auditoria;

	@Autowired
	private ServicioImportacionAlumnos servicio;

	@Autowired
	private ServicioEstructura estructura;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void preparar() {
		LimpiezaBaseDatos.limpiar(jdbc);
		UsuariosDePrueba.iniciarSesion(UsuariosDePrueba.autenticado(Rol.ADMINISTRACION));
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		LimpiezaBaseDatos.limpiar(jdbc);
	}

	@Test
	void importacionEsTodoONada() {
		EscenarioEscolar.Estructura escuela = EscenarioEscolar.crearEstructura(estructura);
		VistaPreviaImportacion previa = servicio.previsualizar(escuela.anio2026(), "alumnos.xlsx",
				archivo(mateo(), valeria(), sebastian()));
		long eventos = contar(jdbc, "evento_auditoria");
		doThrow(new IllegalStateException("falla simulada al terminar")).when(auditoria)
				.registrar(eq(AccionAuditoria.IMPORTACION_CONFIRMADA), any(), any(), any(), any(), any());

		assertThatThrownBy(() -> servicio.confirmar(previa, previa.token()))
				.isInstanceOf(IllegalStateException.class).hasMessage("falla simulada al terminar");

		for (String tabla : new String[] { "alumno", "apoderado", "familia", "matricula", "importacion_alumnos" }) {
			assertThat(contar(jdbc, tabla)).as(tabla).isZero();
		}
		assertThat(contar(jdbc, "evento_auditoria")).isEqualTo(eventos);
	}
}
