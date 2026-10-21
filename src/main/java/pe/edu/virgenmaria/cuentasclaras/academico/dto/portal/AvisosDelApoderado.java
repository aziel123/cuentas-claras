package pe.edu.virgenmaria.cuentasclaras.academico.dto.portal;

import java.util.List;

/**
 * Sprint 9, tanda 2: los avisos de un apoderado (su nombre y si tiene celular o correo verificado, sin mostrarlos) y sus
 * consentimientos vigentes.
 */
public record AvisosDelApoderado(long apoderadoId, String nombre, boolean yo, boolean whatsappVerificado,
		boolean correoVerificado, List<ConsentimientoVista> consentimientos) {
}
