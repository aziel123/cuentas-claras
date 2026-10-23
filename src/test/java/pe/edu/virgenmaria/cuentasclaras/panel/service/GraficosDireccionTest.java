package pe.edu.virgenmaria.cuentasclaras.panel.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.AsignacionVista;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.AsignacionesVista;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.AsistenciaDelDia;
import pe.edu.virgenmaria.cuentasclaras.academico.dto.SeccionConAsignaciones;
import pe.edu.virgenmaria.cuentasclaras.academico.service.Asignaciones;
import pe.edu.virgenmaria.cuentasclaras.academico.service.AsistenciaDireccion;
import pe.edu.virgenmaria.cuentasclaras.aprobaciones.service.BandejaAprobaciones;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.AnioOpcion;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.MorosidadGrado;
import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.Tramos;
import pe.edu.virgenmaria.cuentasclaras.cobranza.service.CifrasCobranza;
import pe.edu.virgenmaria.cuentasclaras.colegio.model.Grado;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.BarraApilada;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.BarrasHorizontales;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.Kpi;
import pe.edu.virgenmaria.cuentasclaras.comun.graficos.TableroDireccion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El tablero de Dirección con datos conocidos: secciones, docentes y secciones sin docente salen de la vista de
 * asignaciones; la asistencia del día, en números y en orden fijo; la morosidad se agrupa por NIVEL (sin alumnos ni
 * secciones) y las aprobaciones son las que ella puede resolver.
 */
class GraficosDireccionTest {

	private static final LocalDate HOY = LocalDate.of(2026, 10, 21);

	private final Asignaciones asignaciones = mock(Asignaciones.class);

	private final AsistenciaDireccion asistencia = mock(AsistenciaDireccion.class);

	private final CifrasCobranza cobranza = mock(CifrasCobranza.class);

	private final BandejaAprobaciones bandeja = mock(BandejaAprobaciones.class);

	private GraficosDireccion graficos;

	@BeforeEach
	void preparar() {
		graficos = new GraficosDireccion(asignaciones, asistencia, cobranza, bandeja,
				Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneId.of("America/Lima")));
		when(asignaciones.vista(null)).thenReturn(new AsignacionesVista(List.of(), 7L, 2026, false, List.of(
				seccion(1L, "5.° Primaria A", true, asignacion("carlos.quispe", true)),
				seccion(2L, "5.° Primaria B", true, asignacion("carlos.quispe", true), asignacion("ana.rojas", true)),
				seccion(3L, "1.° Secundaria A", true, asignacion("luis.vega", false)),
				seccion(4L, "2.° Secundaria A", true),
				seccion(5L, "3.° Secundaria A", false)), List.of(), true, true, false));
		when(asistencia.resumenDelDia()).thenReturn(new AsistenciaDelDia(HOY, 40, 5, 4, 1, 2));
		when(cobranza.anio(null)).thenReturn(Optional.of(new AnioOpcion(7L, 2026, "En curso")));
		when(cobranza.morosidadPorGrado(any(), any())).thenReturn(List.of(
				new MorosidadGrado(Grado.PRIMARIA_2, false, 20, 5, new BigDecimal("1350.00"), Tramos.NINGUNO),
				new MorosidadGrado(Grado.PRIMARIA_5, false, 10, 1, new BigDecimal("450.00"), Tramos.NINGUNO),
				new MorosidadGrado(Grado.SECUNDARIA_1, false, 10, 0, new BigDecimal("0.00"), Tramos.NINGUNO),
				new MorosidadGrado(null, true, 2, 1, new BigDecimal("200.00"), Tramos.NINGUNO)));
		when(bandeja.contarPorResolver()).thenReturn(1L);
	}

	@Test
	void lasCifrasDeDireccion() {
		List<Kpi> kpis = graficos.tablero().kpis();

		assertThat(kpis).extracting(Kpi::etiqueta, Kpi::cifra, Kpi::comparacion).containsExactly(
				tuple("Secciones 2026", "4", "2 secciones con docente"),
				tuple("Docentes asignados", "2", "Con al menos una sección vigente"),
				tuple("Secciones sin docente", "2", "Asígnales docente para que registren la asistencia"),
				tuple("Pendientes por aprobar", "1", "1 solicitud espera tu decisión"),
				tuple("Morosidad", "15 %", "6 de 40 alumnos con deuda vencida · S/ 2,000.00"));
		// Una asignación propuesta (no vigente) no cuenta: 1.° Secundaria A sigue sin docente.
		assertThat(graficos.tablero().seccionesSinDocente()).containsExactly("1.° Secundaria A", "2.° Secundaria A");
	}

	@Test
	void laAsistenciaDelDiaVaEnOrdenFijo() {
		BarraApilada a = graficos.tablero().asistencia();

		assertThat(a.titulo()).isEqualTo("Asistencia del día · 21/10/2026");
		assertThat(a.segmentos()).extracting(BarraApilada.Segmento::serie, BarraApilada.Segmento::etiqueta,
				BarraApilada.Segmento::x, BarraApilada.Segmento::ancho, BarraApilada.Segmento::monto,
				BarraApilada.Segmento::porcentaje).containsExactly(
						tuple(1, "Presentes", "0.00", "80.00", "40 alumnos", "80 %"),
						tuple(2, "Llegaron tarde", "80.00", "10.00", "5 alumnos", "10 %"),
						tuple(3, "Faltas", "90.00", "8.00", "4 alumnos", "8 %"),
						tuple(4, "Faltas justificadas", "98.00", "2.00", "1 alumno", "2 %"));
		assertThat(a.resumen()).isEqualTo("Hoy se registró la asistencia de 50 alumnos en 2 secciones: presentes 80 %, "
				+ "tarde 10 %, faltas 8 % y justificadas 2 %.");
	}

	@Test
	void sinRegistrosHoyLaAsistenciaQuedaVaciaSinInventarDatos() {
		when(asistencia.resumenDelDia()).thenReturn(new AsistenciaDelDia(HOY, 0, 0, 0, 0, 0));

		TableroDireccion.Tablero tablero = graficos.tablero();

		assertThat(tablero.asistencia()).isNull();
		assertThat(tablero.asistenciaVacia()).startsWith("Hoy (21/10/2026) todavía no se registró la asistencia");
	}

	@Test
	void laMorosidadVaPorNivelSinRetiradosNiSecciones() {
		BarrasHorizontales m = graficos.tablero().morosidad();

		assertThat(m.titulo()).isEqualTo("Morosidad por nivel · 2026");
		assertThat(m.filas()).extracting(BarrasHorizontales.Fila::etiqueta, BarrasHorizontales.Fila::valor,
				BarrasHorizontales.Fila::detalle, BarrasHorizontales.Fila::monto).containsExactly(
						tuple("Primaria", "20 %", "6 de 30 alumnos", "S/ 1,800.00"),
						tuple("Secundaria", "0 %", "0 de 10 alumnos", "S/ 0.00"));
	}

	private static SeccionConAsignaciones seccion(Long id, String nombre, boolean activa, AsignacionVista... a) {
		return new SeccionConAsignaciones(id, nombre, activa, List.of(a));
	}

	private static AsignacionVista asignacion(String usuario, boolean vigente) {
		return new AsignacionVista(1L, usuario, usuario, "Tutor", null, vigente ? "Aprobada" : "Propuesta", "exito",
				vigente, "director", null, null, null, null);
	}
}
