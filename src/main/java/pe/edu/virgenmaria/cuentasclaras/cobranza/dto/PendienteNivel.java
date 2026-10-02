package pe.edu.virgenmaria.cuentasclaras.cobranza.dto;

/** Matrículas de un nivel sin cronograma, y el plan vigente que las generaría (nulo si aún no hay). */
public record PendienteNivel(String nivel, String etiqueta, String planVigente, long matriculas) {
}
