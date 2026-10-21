package pe.edu.virgenmaria.cuentasclaras.academico.dto.portal;

import java.time.LocalDateTime;

/**
 * Sprint 9, tanda 2 (decisión 152): el consentimiento vigente de un apoderado para una finalidad y un canal. Sin fila,
 * WhatsApp vale «no» y el correo «sí» (salvo oposición).
 */
public record ConsentimientoVista(String finalidad, String finalidadTexto, String canal, String canalTexto,
		boolean otorgado, boolean porDefecto, String origen, LocalDateTime desde, boolean contacto) {
}
