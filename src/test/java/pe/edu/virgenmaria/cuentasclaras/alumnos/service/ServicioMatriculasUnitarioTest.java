package pe.edu.virgenmaria.cuentasclaras.alumnos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.CambiarSeccionRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.dto.MatricularRequest;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Apoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatosAlumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DatosApoderado;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.DocumentoIdentidad;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Familia;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Parentesco;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.TipoDocumento;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.AlumnoRepository;
import pe.edu.virgenmaria.cuentasclaras.alumnos.repository.MatriculaRepository;
import pe.edu.virgenmaria.cuentasclaras.auditoria.model.AccionAuditoria;
import pe.edu.virgenmaria.cuentasclaras.auditoria.service.AuditoriaService;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;
import pe.edu.virgenmaria.cuentasclaras.colegio.repository.SeccionRepository;
import pe.edu.virgenmaria.cuentasclaras.comun.error.ReglaNegocioException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reglas de {@link ServicioMatriculas} aisladas con Mockito (sin base ni Spring). */
@ExtendWith(MockitoExtension.class)
class ServicioMatriculasUnitarioTest {

	private static final String MOTIVO = "Pedido de la familia por horario";

	private final Clock reloj = Clock.fixed(Instant.parse("2026-10-02T13:00:00Z"), ZoneId.of("America/Lima"));

	@Mock
	private AlumnoRepository alumnos;

	@Mock
	private MatriculaRepository matriculas;

	@Mock
	private SeccionRepository secciones;

	@Mock
	private RegistroAlumnos registro;

	@Mock
	private AuditoriaService auditoria;

	@Mock
	private ObjectProvider<ConsultaCuotasMatricula> proveedorConsulta;

	@Mock
	private ConsultaCuotasMatricula consulta;

	private ServicioMatriculas servicio;

	private AnioEscolar anio2026;

	private AnioEscolar anio2027;

	private Alumno alumno;

	@BeforeEach
	void preparar() {
		servicio = new ServicioMatriculas(alumnos, matriculas, secciones, registro, auditoria, proveedorConsulta, reloj);
		anio2026 = conId(AnioEscolar.nuevo(2026, true, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 12, 18)), 1L);
		anio2027 = conId(AnioEscolar.nuevo(2027, false, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 12, 17)), 2L);
		Apoderado rosa = conId(Apoderado.nuevo(Familia.nueva("Familia Quispe Huamán"), new DatosApoderado(
				new DocumentoIdentidad(TipoDocumento.DNI, "45678912"), "Huamán", "Ccori", "Rosa", Parentesco.MADRE,
				"+51987654321", null)), 5L);
		alumno = conId(Alumno.nuevo(new DatosAlumno(new DocumentoIdentidad(TipoDocumento.DNI, "78451236"), "Quispe",
				"Huamán", "Mateo", LocalDate.of(2015, 6, 14)), rosa), 7L);
		lenient().when(proveedorConsulta.getIfAvailable(any()))
				.thenAnswer(i -> consulta != null ? consulta : ((Supplier<?>) i.getArgument(0)).get());
	}

	@Test
	void cambioDeNivelConCuotasEsRechazadoYNoSeAudita() {
		Matricula matricula = matriculaEn(seccion(anio2026, Grado.PRIMARIA_5, "A", 10L), 20L);
		Seccion secundaria = seccion(anio2026, Grado.SECUNDARIA_1, "A", 11L);
		when(matriculas.findById(20L)).thenReturn(Optional.of(matricula));
		when(secciones.findById(11L)).thenReturn(Optional.of(secundaria));
		when(consulta.tieneCuotas(20L)).thenReturn(true);

		assertThatThrownBy(() -> servicio.cambiarSeccion(20L, new CambiarSeccionRequest(11L, MOTIVO)))
				.isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya tiene cuotas de Primaria");
		assertThat(matricula.getSeccion().getGrado()).isEqualTo(Grado.PRIMARIA_5);
		verify(auditoria, never()).registrar(any(), anyString(), anyString(), any(), any(), any());
	}

	@Test
	void sinCuotasCambiaDeNivelYAudita() {
		Matricula matricula = matriculaEn(seccion(anio2026, Grado.PRIMARIA_5, "A", 10L), 20L);
		when(matriculas.findById(20L)).thenReturn(Optional.of(matricula));
		when(secciones.findById(11L)).thenReturn(Optional.of(seccion(anio2026, Grado.SECUNDARIA_1, "A", 11L)));
		when(consulta.tieneCuotas(20L)).thenReturn(false);

		assertThat(servicio.cambiarSeccion(20L, new CambiarSeccionRequest(11L, MOTIVO))).isEqualTo(7L);

		verify(auditoria).registrar(eq(AccionAuditoria.MATRICULA_SECCION_CAMBIADA), eq("matricula"), eq("20"),
				eq("5.° Primaria A"), eq("1.° Secundaria A"), any());
	}

	@Test
	void sinImplementacionDelPuertoNingunaMatriculaTieneCuotas() {
		consulta = null;
		Matricula matricula = matriculaEn(seccion(anio2026, Grado.PRIMARIA_5, "A", 10L), 20L);
		when(matriculas.findById(20L)).thenReturn(Optional.of(matricula));
		when(secciones.findById(11L)).thenReturn(Optional.of(seccion(anio2026, Grado.SECUNDARIA_1, "A", 11L)));

		servicio.cambiarSeccion(20L, new CambiarSeccionRequest(11L, MOTIVO));

		assertThat(matricula.getSeccion().getGrado()).isEqualTo(Grado.SECUNDARIA_1);
	}

	@Test
	void matricularUsaLaFechaPorDefectoSegunElInicioDeClases() {
		Seccion de2026 = seccion(anio2026, Grado.PRIMARIA_5, "A", 10L);
		Seccion de2027 = seccion(anio2027, Grado.PRIMARIA_6, "A", 12L);
		when(alumnos.findById(7L)).thenReturn(Optional.of(alumno));
		when(secciones.findById(10L)).thenReturn(Optional.of(de2026));
		when(secciones.findById(12L)).thenReturn(Optional.of(de2027));
		when(registro.matricular(any(), any(), any())).thenAnswer(i -> conId(
				Matricula.nueva(i.getArgument(0), i.getArgument(1), i.getArgument(2)), 30L));

		servicio.matricular(7L, new MatricularRequest(10L, null));
		servicio.matricular(7L, new MatricularRequest(12L, null));

		verify(registro).matricular(alumno, de2026, LocalDate.of(2026, 3, 2));
		verify(registro).matricular(alumno, de2027, LocalDate.of(2026, 10, 2));
	}

	@Test
	void matricularSinSeccionEsRechazado() {
		when(alumnos.findById(7L)).thenReturn(Optional.of(alumno));
		assertThatThrownBy(() -> servicio.matricular(7L, new MatricularRequest(null, null)))
				.isInstanceOf(ReglaNegocioException.class).hasMessage("Elige la sección.");
		verify(registro, never()).matricular(any(), any(), any());
	}

	private Matricula matriculaEn(Seccion seccion, long id) {
		return conId(Matricula.nueva(alumno, seccion, LocalDate.of(2026, 3, 2)), id);
	}

	private static Seccion seccion(AnioEscolar anio, Grado grado, String nombre, long id) {
		return conId(Seccion.nueva(anio, grado, nombre), id);
	}

	private static <T> T conId(T entidad, long id) {
		ReflectionTestUtils.setField(entidad, "id", id);
		return entidad;
	}
}
