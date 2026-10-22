package pe.edu.virgenmaria.cuentasclaras.comunicados.dto;

import java.util.List;

/**
 * Sprint 9, tanda 3 (sección 3.8): cómo lo verá la familia, antes de enviarlo. No guarda nada. {@code porAprobar}: con el
 * PDF de un docente o un PDF que requiere revisión, espera la aprobación de {@code quienAprueba}.
 */
public record VistaPrevia(String etiqueta, String alcance, int destinatarias, String titulo, List<String> parrafos,
		String pie, boolean urgente, boolean requiereConfirmacion, Integer paginasAdjunto, boolean porAprobar,
		String quienAprueba) {
}
