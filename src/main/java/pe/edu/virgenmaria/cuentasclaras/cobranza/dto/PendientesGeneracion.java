package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import java.util.List;

/** Pantalla «Cronogramas»: qué matrículas activas del año aún no tienen cuotas. */
public record PendientesGeneracion(AnioOpcion anio, List<AnioOpcion> anios, List<PendienteNivel> niveles,
		long total, long generables, boolean puedeGenerar) {
}
