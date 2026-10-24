package pe.edu.virgenmaria.cuentasclaras.academico.libreta.service;

import pe.edu.virgenmaria.cuentasclaras.academico.libreta.model.LibretaEmitida;

import java.util.List;
import java.util.Objects;

/**
 * Sprint 10, tanda 3 (secciones 3.7 y 3.13): lo que dice una libreta, armado de la foto ({@link FotoLibreta}): la cabecera
 * congelada, por área y competencia el nivel de cada periodo cerrado hasta este (y la conclusión de este periodo), las
 * exoneraciones, el resumen de asistencia y la leyenda de la escala. NADA de pagos, deudas ni pensiones: quien la arma no
 * puede leer cobranza (ArchUnit) y {@code LibretaSinCobranzaTest} lee el texto del PDF.
 *
 * @param periodos los periodos del año hasta este, en orden (las columnas de la tabla)
 * @param ultimoPeriodo si este es el último periodo del año (entonces va el nivel anual)
 */
public record ContenidoLibreta(String colegio, String nivel, String grado, String seccion, int anio, Long alumnoId,
		Long periodoId, int periodoNumero, String periodoNombre, boolean ultimoPeriodo, LibretaEmitida.Cabecera cabecera,
		List<Periodo> periodos, List<Area> areas, List<Asistencia> asistencia, List<Escala> escala) {

	public ContenidoLibreta {
		Objects.requireNonNull(cabecera, "cabecera");
		periodos = List.copyOf(periodos);
		areas = List.copyOf(areas);
		asistencia = List.copyOf(asistencia);
		escala = List.copyOf(escala);
	}

	public record Periodo(int numero, String nombre) {
	}

	/** Un área del grado (las transversales, aparte), con las competencias que se califican en ese grado. */
	public record Area(String codigo, String nombre, boolean transversal, List<Competencia> competencias) {

		public Area {
			competencias = List.copyOf(competencias);
		}
	}

	/**
	 * Una competencia: una nota por periodo (alineada con {@link ContenidoLibreta#periodos()}) y, en el último periodo del
	 * año, el nivel anual (el último registrado).
	 */
	public record Competencia(String codigo, String nombre, List<Nota> notas, String anual) {

		public Competencia {
			notas = List.copyOf(notas);
		}

		/** La nota del periodo de la libreta (la última de la lista). */
		public Nota delPeriodo() {
			return notas.get(notas.size() - 1);
		}
	}

	/**
	 * La nota de un periodo. {@code nivel} null y {@code exonerada} false: «sin nota». {@code conclusion} solo en la del
	 * periodo de la libreta (la huella lleva la de todas: {@code conclusionSha256}).
	 */
	public record Nota(int periodo, String nivel, boolean exonerada, String conclusion, String conclusionSha256) {

		public String texto() {
			return exonerada ? "EXO" : nivel == null ? "—" : nivel;
		}
	}

	/**
	 * Resumen de asistencia de un periodo: inasistencias justificadas (FALTA_JUSTIFICADA), injustificadas (FALTA) y
	 * tardanzas (TARDE).
	 */
	public record Asistencia(int periodo, String nombre, int justificadas, int injustificadas, int tardanzas) {
	}

	public record Escala(String valor, String nombre, String descripcion) {
	}

	/** El nombre del periodo de la libreta («I bimestre»). */
	public String nombrePeriodo() {
		return periodoNombre;
	}
}
