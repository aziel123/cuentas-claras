package pe.edu.virgenmaria.cuentasclaras.comunicados.dto;

import java.util.List;

/**
 * Sprint 9, tanda 3 (sección 3.10, decisión 163): destinatarias, avisadas, entregadas, leídas y confirmadas. Los
 * pendientes, con el NOMBRE DEL ALUMNO (nunca el apoderado ni su contacto); vacío si quien mira no puede verlos (B16).
 */
public record Estadisticas(int destinatarias, int avisadas, int entregadas, int leidas, int confirmadas,
		int sinPortal, List<String> pendientes, boolean pendientesVisibles) {
}
