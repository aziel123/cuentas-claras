package pe.edu.virgenmaria.cuentasclaras.academico.notas.dto;

/**
 * Sprint 10, tanda 2 (sección 8.2): lo que el docente pide para una celda de la hoja: la matrícula, la competencia, el
 * nivel ({@code null}: «sin nota en este periodo») y la conclusión. NO tiene {@code origen}, {@code creado_por},
 * {@code lote_id} ni la huella de la conclusión: los decide el servicio (y la base), nunca el formulario
 * ({@code NotaPedidaSinOrigenTest} y la regla {@code notaPedidaSinOrigen}).
 */
public record NotaPedida(Long matriculaId, Long competenciaId, String nivel, String conclusion) {
}
