package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

import pe.edu.virgenmaria.cuentasclaras.cobranza.model.ConfiguracionPlan.RangoFechas;

/**
 * Sprint 11, tanda 1b (F-D3): los {@code min} y {@code max} de las fechas del formulario del plan, de la misma fuente que
 * {@code ConfiguracionPlan.validar} (el HTML y el servidor no pueden separarse). Las fechas en ISO (aaaa-mm-dd).
 */
public record RangosPlan(RangoFechas matricula, RangoFechas pensiones, RangoFechas cobroDesde) {
}
