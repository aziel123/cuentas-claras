package pe.edu.virgenmaria.cuentasclaras.academico.curriculo.dto;

/**
 * Sprint 10, tanda 1 (contrato de la sección 6 del brief): una competencia con su área, en el orden de la libreta y del
 * SIAGIE ({@code area.orden}, {@code competencia.orden}). El código no se renombra.
 */
public record CompetenciaDelCatalogo(Long id, String codigo, String nombre, int orden, Long areaId, String areaCodigo,
		String areaNombre, int areaOrden, boolean transversal, String soloGrados) {
}
