package pe.edu.virgenmaria.cuentasclaras.academico.siagie;

import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Alumno;
import pe.edu.virgenmaria.cuentasclaras.alumnos.model.Matricula;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.AnioEscolar;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Seccion;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Correcciones del sprint 9: QA-S9-4 (la situación de matrícula según la sección 3.11: solo cuenta como cursado el año
 * anterior con matrícula ACTIVA; RESERVADA no es haber estudiado; RETIRADA es una interrupción, REENTRANTE) y QA-S9-10
 * (el orden de la nómina: apellido paterno, materno y nombres, con la Ñ y las tildes del español).
 */
class NominaSituacionYOrdenTest {

	private final Alumno alumno = mock(Alumno.class);

	private static Matricula en(int anio, Grado grado) {
		AnioEscolar a = AnioEscolar.nuevo(anio, false, LocalDate.of(anio, 3, 1), LocalDate.of(anio, 12, 15));
		return Matricula.nueva(mock(Alumno.class), Seccion.nueva(a, grado, "A"), LocalDate.of(anio, 3, 1));
	}

	private final Matricula actual = en(2026, Grado.PRIMARIA_5);

	@Test
	void debeSerIngresanteSinMatriculasAnterioresOSoloConUnaReserva() {
		assertThat(NominaSiagie.situacion(actual, List.of())).isEqualTo(SituacionMatricula.INGRESANTE);
		Matricula reserva = Matricula.reservada(alumno, Seccion.nueva(AnioEscolar.nuevo(2025, false,
				LocalDate.of(2025, 3, 1), LocalDate.of(2025, 12, 15)), Grado.PRIMARIA_4, "A"), LocalDate.of(2025, 1, 10));
		assertThat(NominaSiagie.situacion(actual, List.of(reserva))).as("una RESERVADA no es haber estudiado")
				.isEqualTo(SituacionMatricula.INGRESANTE);
	}

	@Test
	void debeSerPromovidoORepitenteSoloConElAnioAnteriorCursado() {
		assertThat(NominaSiagie.situacion(actual, List.of(en(2025, Grado.PRIMARIA_4)))).isEqualTo(SituacionMatricula.PROMOVIDO);
		assertThat(NominaSiagie.situacion(actual, List.of(en(2025, Grado.PRIMARIA_5)))).isEqualTo(SituacionMatricula.REPITENTE);
		assertThat(NominaSiagie.situacion(actual, List.of(en(2025, Grado.PRIMARIA_3)))).as("saltó un grado")
				.isEqualTo(SituacionMatricula.PROMOVIDO);
	}

	@Test
	void debeSerReentranteSiSeRetiroElAnioAnteriorOEstudioAntes() {
		Matricula retirada = en(2025, Grado.PRIMARIA_4);
		retirada.retirar(LocalDate.of(2025, 6, 30));
		assertThat(NominaSiagie.situacion(actual, List.of(retirada))).as("una RETIRADA es una interrupción")
				.isEqualTo(SituacionMatricula.REENTRANTE);
		assertThat(NominaSiagie.situacion(actual, List.of(en(2024, Grado.PRIMARIA_3)))).as("estudió en 2024 y vuelve")
				.isEqualTo(SituacionMatricula.REENTRANTE);
	}

	private static Matricula de(String paterno, String materno, String nombres) {
		Alumno a = mock(Alumno.class);
		when(a.getApellidoPaterno()).thenReturn(paterno);
		when(a.getApellidoMaterno()).thenReturn(materno);
		when(a.getNombres()).thenReturn(nombres);
		return Matricula.nueva(a, Seccion.nueva(AnioEscolar.nuevo(2026, true, LocalDate.of(2026, 3, 1),
				LocalDate.of(2026, 12, 15)), Grado.PRIMARIA_5, "A"), LocalDate.of(2026, 3, 1));
	}

	@Test
	void debeOrdenarPorLosDosApellidosYLosNombresConLaEnheYLasTildes() {
		List<Matricula> lista = List.of(de("Zapata", "Ruiz", "Ana"), de("Ñique", "Paz", "Luis"),
				de("Ávila", "Soto", "Rosa"), de("Nuñez", "Vega", "Juan"), de("Quispe", "Rojas", "Mateo"),
				de("Quispe", "Álvarez", "Mateo"), de("Ochoa", "Díaz", "Eva"));
		List<String> ordenada = lista.stream().sorted(NominaSiagie.enOrdenAlfabetico())
				.map(m -> m.getAlumno().getApellidoPaterno() + " " + m.getAlumno().getApellidoMaterno()).toList();
		assertThat(ordenada).containsExactly("Ávila Soto", "Nuñez Vega", "Ñique Paz", "Ochoa Díaz", "Quispe Álvarez",
				"Quispe Rojas", "Zapata Ruiz");
	}
}
