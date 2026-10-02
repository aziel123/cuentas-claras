package pe.edu.virgenmaria.cuentasclaras.aprobaciones.dto;

import java.util.List;

/** Bandeja de aprobaciones: pendientes, resueltas recientes y el reporte «Ingresos tardíos». */
public record BandejaVista(List<SolicitudVista> pendientes, List<SolicitudVista> resueltas,
		List<SolicitudVista> ingresosTardios) {
}
