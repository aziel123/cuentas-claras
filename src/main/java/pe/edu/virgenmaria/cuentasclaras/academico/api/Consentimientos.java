package pe.edu.virgenmaria.cuentasclaras.academico.api;

/**
 * Sprint 9, tanda 2 (sección 3.6, H2, decisión 152): si un apoderado recibe los avisos de asistencia por un canal, según
 * su último consentimiento. WhatsApp exige un «sí» explícito; el correo sale salvo oposición. Lo leen la mensajería (el
 * aviso y su respaldo por correo) y el portal. En MySQL lo exige también trg_mensaje_nace.
 */
public interface Consentimientos {

	/** ¿Se le escribe por WhatsApp ({@code true}) o por correo ({@code false}) los avisos de asistencia? */
	boolean recibeAsistencia(long apoderadoId, boolean porWhatsapp);
}
