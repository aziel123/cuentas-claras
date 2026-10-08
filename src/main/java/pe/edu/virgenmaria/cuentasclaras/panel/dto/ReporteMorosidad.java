package pe.edu.virgenmaria.cuentasclaras.panel.dto;

import pe.edu.virgenmaria.cuentasclaras.cobranza.dto.AnioOpcion;

import java.time.LocalDate;
import java.util.List;

/**
 * Morosidad por grado de un año (en pantalla). Nunca por sección ni con nombres de alumnos (Ley 29733, INDECOPI).
 * {@code anio == null}: el colegio aún no tiene años escolares.
 */
public record ReporteMorosidad(List<AnioOpcion> anios, AnioOpcion anio, LocalDate al, List<Fila> filas,
		String totalMonto, long totalConDeuda, long totalMatriculados, boolean puedeExportar) {

	public record Fila(String grado, long matriculados, long conDeuda, String monto, long hasta30, long hasta60,
			long hasta90, long masDe90) {
	}

	public ReporteMorosidad {
		anios = List.copyOf(anios);
		filas = List.copyOf(filas);
	}
}
