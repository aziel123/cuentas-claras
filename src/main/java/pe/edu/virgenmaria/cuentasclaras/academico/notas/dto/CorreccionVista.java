package pe.edu.virgenmaria.cuentasclaras.academico.notas.dto;

import java.time.LocalDateTime;

/**
 * Sprint 10, tanda 2 (sección 3.6, vigilancia): una corrección aprobada del periodo para «Correcciones de notas del
 * periodo» de Dirección: dónde, de qué a qué, quién pidió y quién aprobó. Sin el texto de las conclusiones.
 */
public record CorreccionVista(Long calificacionId, String seccion, String competencia, String de, String a, String pidio,
		String aprobo, LocalDateTime cuando) {
}
